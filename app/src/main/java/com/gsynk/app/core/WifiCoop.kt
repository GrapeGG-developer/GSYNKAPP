package com.gsynk.app.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.random.Random

/**
 * «Grape Link» по Wi-Fi: двое играют в одно подземелье на двух телефонах.
 *
 * NFC в этой схеме — только знакомство: по касанию хозяин отдаёт адрес и код комнаты.
 * Сама игра идёт по локальной сети, потому что NFC для потока кадров не годится.
 *
 * Кто хозяин мира — тот его и считает: держит камни, врагов, слёзы и счёт.
 * Гость отправляет только свой стик, получает готовую картину комнаты
 * и рисует её у себя. Поэтому оба видят одно и то же подземелье, но каждый
 * управляет своим героем на своём телефоне.
 *
 * Транспорт — обычные TCP-сокеты, протокол — строки из [CoopCodec]. На слабом
 * Wi-Fi первый подключившийся выигрывает: сокет один, а очередь отправки
 * «схлопывается», чтобы старые кадры не копились.
 */
object WifiCoop {

    const val PORT = 45_991

    enum class Role { HOST, GUEST }

    sealed interface Status {
        /** Ничего не происходит. */
        data object Idle : Status

        /** Хозяин открыл комнату и ждёт второго игрока. */
        data class Listening(
            val ip: String,
            val port: Int,
            val code: String,
            val seed: Long
        ) : Status

        /** Гость стучится к хозяину. */
        data class Connecting(val host: String, val port: Int) : Status

        /** Связь есть, можно играть. */
        data class Live(
            val role: Role,
            val peer: String,
            val address: String,
            val seed: Long
        ) : Status

        /** Не получилось — с причиной на русском. */
        data class Failed(val reason: String) : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Последняя картина мира, присланная хозяином: её рисует гость. */
    private val _remoteWorld = MutableStateFlow<RogueGame.World?>(null)
    val remoteWorld: StateFlow<RogueGame.World?> = _remoteWorld.asStateFlow()

    /** Стик гостя: его читает хозяин и ведёт второго героя. */
    private val _peerInput = MutableStateFlow(RogueGame.Input())
    val peerInput: StateFlow<RogueGame.Input> = _peerInput.asStateFlow()

    /** Ключ подземелья: у обоих телефоном одинаковый. */
    private val _seed = MutableStateFlow(0L)
    val seed: StateFlow<Long> = _seed.asStateFlow()

    @Volatile
    var role: Role? = null
        private set

    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var acceptJob: Job? = null
    private var receiveJob: Job? = null

    /** Очередь на отправку: переполнение не копим, шлём самое свежее. */
    private val outgoing = Channel<String>(Channel.CONFLATED)

    /** Свой IP в локальной сети — его показываем хозяину. */
    fun localAddress(): String = runCatching {
        val candidates = ArrayList<String>()
        for (iface in NetworkInterface.getNetworkInterfaces()) {
            if (!iface.isUp || iface.isLoopback) continue
            for (address in iface.inetAddresses) {
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    val host = address.hostAddress ?: continue
                    // Wi-Fi и точка доступа в приоритете, но годится любой локальный адрес
                    if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                        return@runCatching host
                    }
                    candidates += host
                }
            }
        }
        candidates.firstOrNull() ?: "0.0.0.0"
    }.getOrDefault("0.0.0.0")

    /** Хозяин: открыть комнату. */
    fun host(name: String, gameSeed: Long) {
        stop()
        role = Role.HOST
        _seed.value = gameSeed
        val code = (1_000 + Random.nextInt(9_000)).toString()
        val ip = localAddress()
        _status.value = Status.Listening(ip, PORT, code, gameSeed)
        AppLog.event("Grape Link", "подземелье по Wi-Fi: комната открыта на $ip:$PORT")

        acceptJob = scope.launch {
            runCatching {
                val srv = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT))
                }
                server = srv
                val client = srv.accept()
                socket = client
                client.tcpNoDelay = true
                client.keepAlive = true

                val reader = BufferedReader(
                    InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)
                )
                val out = BufferedWriter(
                    OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8)
                )
                writer = out

                val hello = reader.readLine()
                val greet = hello?.let { CoopCodec.decodeHello(it) }
                if (greet == null || greet.code != code) {
                    out.write("NO|код комнаты не совпал\n")
                    out.flush()
                    _status.value = Status.Failed("Код комнаты не совпал — подключение отклонено")
                    closeQuietly()
                    return@runCatching
                }

                out.write(CoopCodec.encodeWelcome(gameSeed))
                out.newLine()
                out.flush()

                val peer = greet.name.ifBlank { "второй телефон" }
                _status.value = Status.Live(
                    role = Role.HOST,
                    peer = peer,
                    address = client.inetAddress?.hostAddress ?: "локальная сеть",
                    seed = gameSeed
                )
                AppLog.event("Grape Link", "подключился второй телефон: $peer")

                receiveJob = launch {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("BYE")) break
                        CoopCodec.decodeInput(line)?.let { _peerInput.value = it }
                    }
                    if (_status.value is Status.Live) {
                        _status.value = Status.Failed("Второй телефон отключился")
                    }
                }

                for (line in outgoing) {
                    out.write(line)
                    out.newLine()
                    out.flush()
                }
            }.onFailure { error ->
                if (_status.value !is Status.Idle) {
                    _status.value = Status.Failed("Комната не открылась: " + error.readable())
                }
            }
        }
    }

    /** Гость: прийти по адресу, который прилетел по NFC (или введён руками). */
    fun join(hostAddress: String, port: Int, code: String, name: String) {
        stop()
        role = Role.GUEST
        val target = hostAddress.trim().ifBlank { "192.168.1.1" }
        _status.value = Status.Connecting(target, port)
        AppLog.event("Grape Link", "подключаемся к хозяину: $target:$port")

        acceptJob = scope.launch {
            runCatching {
                val client = Socket()
                client.tcpNoDelay = true
                client.keepAlive = true
                client.connect(InetSocketAddress(target, port), 6_000)
                socket = client

                val reader = BufferedReader(
                    InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)
                )
                val out = BufferedWriter(
                    OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8)
                )
                writer = out

                out.write(CoopCodec.encodeHello(code, name))
                out.newLine()
                out.flush()

                val answer = reader.readLine()
                val welcome = answer?.let { CoopCodec.decodeWelcome(it) }
                if (welcome == null) {
                    _status.value = Status.Failed(
                        if (answer?.startsWith("NO|") == true) "Хозяин отклонил код комнаты"
                        else "Хозяин не ответил на приветствие"
                    )
                    closeQuietly()
                    return@runCatching
                }

                _seed.value = welcome
                _status.value = Status.Live(
                    role = Role.GUEST,
                    peer = target,
                    address = "$target:$port",
                    seed = welcome
                )
                AppLog.event("Grape Link", "связь с хозяином есть, ключ подземелья $welcome")

                receiveJob = launch {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        CoopCodec.decode(line)?.let { _remoteWorld.value = it }
                    }
                    if (_status.value is Status.Live) {
                        _status.value = Status.Failed("Хозяин закрыл комнату")
                    }
                }

                for (line in outgoing) {
                    out.write(line)
                    out.newLine()
                    out.flush()
                }
            }.onFailure { error ->
                if (_status.value !is Status.Idle) {
                    _status.value = Status.Failed("Не подключиться: " + error.readable())
                }
            }
        }
    }

    /** Гость отправляет свой стик. Хозяин зовёт это же, когда жмёт «в бой» впустую. */
    fun sendInput(input: RogueGame.Input) {
        if (role != Role.GUEST) return
        if (_status.value !is Status.Live) return
        outgoing.trySend(CoopCodec.encodeInput(input))
    }

    /** Хозяин отправляет кадр мира. */
    fun publish(world: RogueGame.World) {
        if (role != Role.HOST) return
        if (_status.value !is Status.Live) return
        outgoing.trySend(CoopCodec.encode(world))
    }

    fun stop() {
        runCatching { receiveJob?.cancel() }
        runCatching { acceptJob?.cancel() }
        receiveJob = null
        acceptJob = null
        while (outgoing.tryReceive().isSuccess) {
            // вычищаем очередь, чтобы следующий сеанс начался с чистого листа
        }
        runCatching { writer?.write("BYE\n"); writer?.flush() }
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        runCatching { server?.close() }
        writer = null
        socket = null
        server = null
        role = null
        _remoteWorld.value = null
        _peerInput.value = RogueGame.Input()
        _status.value = Status.Idle
    }

    /** Код комнаты и ключ — то, что хозяин отдаёт по NFC одной строкой. */
    fun inviteBody(seed: Long, code: String, from: String): String {
        val ip = (status.value as? Status.Listening)?.ip ?: localAddress()
        return "$ip;$PORT;$code;$seed;$from"
    }

    private fun closeQuietly() {
        runCatching { writer?.close() }
        runCatching { socket?.close() }
        writer = null
        socket = null
    }

    private fun Throwable.readable(): String =
        message?.takeIf { it.isNotBlank() } ?: (this::class.java.simpleName)

    /** Полная остановка при выходе из игры. */
    fun shutdown() {
        stop()
        runCatching { scope.cancel() }
    }
}
