package com.gsynk.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.net.wifi.WpsInfo
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * «GSYNK рядом»: связаться с другим человеком без NFC — по Bluetooth и Wi-Fi Direct.
 *
 * Зачем два канала:
 *  * **Bluetooth** — медленный, зато работает без роутера и без интернета: показ сцен,
 *    метка, обновление, сценарии, короткие сообщения.
 *  * **Wi-Fi Direct** — быстрый: телефоны сами договариваются о своей сети, и по ней
 *    идёт игра GTBOI и передача файлов теми же портами, что и в Grape Link.
 *
 * Обе связи говорят одним и тем же строчным протоколом (см. [line]), поэтому логика
 * одна, а канал меняется.
 */
object Nearby {

    /** Свой UUID службы: по нему второй телефон GSYNK сразу видит, что это «свой». */
    val BT_UUID: UUID = UUID.fromString("6f8a1c14-9d2b-4f57-8b3e-2c5a7d9e1101")
    const val BT_SERVICE = "GSYNK"

    /** Порт Wi-Fi Direct: тот же, что у игры, — протокол общий. */
    const val PORT = 45_991

    enum class Channel(val title: String, val hint: String) {
        BLUETOOTH("Bluetooth", "показ, метки, обновление, сценарии"),
        WIFI_DIRECT("Wi-Fi Direct", "игра и файлы напрямую, без роутера")
    }

    enum class Kind { PAIRED, FOUND, DIRECT }

    data class Peer(
        val name: String,
        val address: String,
        val channel: Channel,
        val kind: Kind = Kind.FOUND,
        val signal: Int = 0
    ) {
        val label: String get() = if (name.isBlank()) address else name
        val isPaired: Boolean get() = kind == Kind.PAIRED
    }

    sealed interface Status {
        data object Idle : Status
        data class Scanning(val channel: Channel, val note: String) : Status
        data class Connecting(val peer: Peer) : Status
        data class Ready(val peer: Peer, val channel: Channel) : Status
        data class Failed(val reason: String) : Status

        val label: String
            get() = when (this) {
                is Idle -> "не подключено"
                is Scanning -> "ищу: $note"
                is Connecting -> "подключаюсь к «${peer.label}»"
                is Ready -> "связь есть: ${peer.label} (${channel.title})"
                is Failed -> "не вышло: $reason"
            }
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    /** Заряд телефона собеседника в процентах (-1 — неизвестно). */
    private val _partnerBattery = MutableStateFlow(-1)
    val partnerBattery: StateFlow<Int> = _partnerBattery.asStateFlow()

    /** Текст, который собеседник просит показать на нашем острове. */
    private val _incomingIsland = MutableStateFlow<String?>(null)
    val incomingIsland: StateFlow<String?> = _incomingIsland.asStateFlow()

    /** Тело сценария, пришедшее по связи, — интерфейс предложит его применить. */
    private val _incomingProfile = MutableStateFlow<String?>(null)
    val incomingProfile: StateFlow<String?> = _incomingProfile.asStateFlow()

    /** Задержка связи в миллисекундах: измеряется пингом, а не выдумывается. */
    private val _latency = MutableStateFlow(0L)
    val latency: StateFlow<Long> = _latency.asStateFlow()

    /** Кто на другом конце: «имя|модель». */
    private val _partner = MutableStateFlow("")
    val partner: StateFlow<String> = _partner.asStateFlow()

    /** Адрес хозяина в сети Wi-Fi Direct: по нему гость заходит в игру. */
    private val _hostAddress = MutableStateFlow("")
    val hostAddress: StateFlow<String> = _hostAddress.asStateFlow()

    @Volatile private var socketStream: Streams? = null
    @Volatile private var btServer: BluetoothServerSocket? = null
    @Volatile private var tcpServer: ServerSocket? = null
    @Volatile private var writer: java.io.Writer? = null
    @Volatile private var receiver: BroadcastReceiver? = null
    @Volatile private var p2p: WifiP2pManager? = null
    @Volatile private var p2pChannel: WifiP2pManager.Channel? = null
    @Volatile private var activeChannel: Channel = Channel.BLUETOOTH

    /** Приложение-контекст: нужен, чтобы принимать короткие файлы, когда связь уже есть. */
    @Volatile private var appContext: Context? = null
    private var counter = 0
    private var lastPingSent = 0L
    private var lastPingId = -1

    // ————————————— права —————————————

    fun permissionsFor(channel: Channel): List<String> = when (channel) {
        Channel.BLUETOOTH -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        Channel.WIFI_DIRECT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasPermissions(context: Context, channel: Channel): Boolean =
        permissionsFor(channel).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun bluetoothEnabled(context: Context): Boolean =
        runCatching { adapter(context)?.isEnabled == true }.getOrDefault(false)

    private fun adapter(context: Context): BluetoothAdapter? = runCatching {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }.getOrNull()

    // ————————————— поиск —————————————

    @SuppressLint("MissingPermission")
    fun startDiscovery(context: Context, channel: Channel) {
        appContext = context.applicationContext
        stopDiscovery(context)
        activeChannel = channel
        _peers.value = emptyList()
        _status.value = Status.Scanning(channel, "ищу GSYNK-устройства")

        when (channel) {
            Channel.BLUETOOTH -> startBluetoothDiscovery(context)
            Channel.WIFI_DIRECT -> startWifiDirectDiscovery(context)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery(context: Context) {
        val adapter = adapter(context)
        if (adapter == null) {
            _status.value = Status.Failed("в телефоне нет Bluetooth")
            return
        }
        if (!hasPermissions(context, Channel.BLUETOOTH)) {
            _status.value = Status.Failed("нужны разрешения «Рядом с устройством»")
            return
        }
        if (!adapter.isEnabled) {
            _status.value = Status.Failed("Bluetooth выключен — включите его в шторке")
            return
        }

        // уже сопряжённые — сразу в списке: их искать не нужно
        val paired = runCatching { adapter.bondedDevices }.getOrNull().orEmpty().map { device ->
            Peer(
                name = runCatching { device.name }.getOrNull().orEmpty(),
                address = device.address,
                channel = Channel.BLUETOOTH,
                kind = Kind.PAIRED
            )
        }
        _peers.value = paired
        log("Найдено сопряжённых: " + paired.size)

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        val listener = object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt()
                        val found = device?.let {
                            Peer(
                                name = runCatching { it.name }.getOrNull().orEmpty(),
                                address = it.address,
                                channel = Channel.BLUETOOTH,
                                kind = Kind.FOUND,
                                signal = rssi
                            )
                        }
                        if (found != null) addPeer(found)
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED ->
                        _status.value = Status.Scanning(Channel.BLUETOOTH, "поиск закончен, можно повторить")
                }
            }
        }
        receiver = listener
        register(context, listener, filter)
        runCatching { adapter.startDiscovery() }
            .onFailure { _status.value = Status.Failed("поиск не запустился: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    private fun startWifiDirectDiscovery(context: Context) {
        val manager = p2pManager(context)
        if (manager == null) {
            _status.value = Status.Failed("Wi-Fi Direct недоступен")
            return
        }
        if (!hasPermissions(context, Channel.WIFI_DIRECT)) {
            _status.value = Status.Failed("нужны разрешения «Рядом с устройствами»")
            return
        }
        val ch = p2pChannel(context, manager) ?: return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        }
        val listener = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                            _status.value = Status.Failed("Wi-Fi выключен — включите его")
                        }
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> manager.requestPeers(ch) { list ->
                        val found = list.deviceList.map { device -> device.toPeer() }
                        _peers.value = found.sortedBy { it.label.lowercase() }
                        _status.value = Status.Scanning(
                            Channel.WIFI_DIRECT,
                            "рядом устройств: " + found.size
                        )
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> manager.requestConnectionInfo(ch) { info ->
                        onP2pInfo(info)
                    }
                }
            }
        }
        receiver = listener
        register(context, listener, filter)
        manager.discoverPeers(ch, simpleListener("поиск Wi-Fi Direct"))
    }

    private fun WifiP2pDevice.toPeer(): Peer = Peer(
        name = deviceName.orEmpty(),
        address = deviceAddress.orEmpty(),
        channel = Channel.WIFI_DIRECT,
        kind = Kind.DIRECT,
        signal = 0
    )

    private fun addPeer(peer: Peer) {
        _peers.update { current ->
            val without = current.filterNot { it.address == peer.address && it.channel == peer.channel }
            (without + peer).sortedWith(compareByDescending<Peer> { it.isPaired }.thenBy { it.label.lowercase() })
        }
    }

    // ————————————— подключение —————————————

    @SuppressLint("MissingPermission")
    fun connect(context: Context, peer: Peer) {
        appContext = context.applicationContext
        _status.value = Status.Connecting(peer)
        when (peer.channel) {
            Channel.BLUETOOTH -> Thread({ connectBluetooth(context, peer) }, "gsynk-bt").start()
            Channel.WIFI_DIRECT -> connectWifiDirect(context, peer)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectBluetooth(context: Context, peer: Peer) {
        val adapter = adapter(context)
        if (adapter == null) {
            _status.value = Status.Failed("Bluetooth недоступен")
            return
        }
        runCatching { adapter.cancelDiscovery() }
        val remote: BluetoothDevice? = runCatching { adapter.getRemoteDevice(peer.address) }.getOrNull()
        if (remote == null) {
            _status.value = Status.Failed("устройство не найдено")
            return
        }
        val socket = runCatching { remote.createRfcommSocketToServiceRecord(BT_UUID) }.getOrNull()
        if (socket == null) {
            _status.value = Status.Failed("сокет не создался")
            return
        }
        val ok = runCatching {
            socket.connect()
            adopt(BluetoothStreams(socket), peer, Channel.BLUETOOTH)
            true
        }.getOrElse {
            runCatching { socket.close() }
            _status.value = Status.Failed("не соединилось: ${it.message ?: "нет ответа"}")
            false
        }
        if (ok) handshake("gsynk-${Build.MODEL.take(12)}")
    }

    /** Ждём гостя: вторая сторона подключается сама (по Bluetooth). */
    @SuppressLint("MissingPermission")
    fun host(context: Context, peerName: String = "GSYNK") {
        appContext = context.applicationContext
        val adapter = adapter(context) ?: run {
            _status.value = Status.Failed("Bluetooth недоступен")
            return
        }
        if (!hasPermissions(context, Channel.BLUETOOTH)) {
            _status.value = Status.Failed("нужны разрешения «Рядом с устройством»")
            return
        }
        activeChannel = Channel.BLUETOOTH
        _status.value = Status.Scanning(Channel.BLUETOOTH, "жду подключения по Bluetooth")
        Thread({
            val server = runCatching { adapter.listenUsingRfcommWithServiceRecord(BT_SERVICE, BT_UUID) }.getOrNull()
            if (server == null) {
                _status.value = Status.Failed("не удалось открыть приём")
                return@Thread
            }
            btServer = server
            val socket = runCatching { server.accept() }.getOrNull()
            runCatching { server.close() }
            btServer = null
            if (socket == null) {
                if (_status.value !is Status.Ready) _status.value = Status.Idle
                return@Thread
            }
            val peer = Peer(
                name = runCatching { socket.remoteDevice.name }.getOrNull().orEmpty().ifBlank { "гость" },
                address = runCatching { socket.remoteDevice.address }.getOrNull().orEmpty(),
                channel = Channel.BLUETOOTH,
                kind = Kind.PAIRED
            )
            adopt(BluetoothStreams(socket), peer, Channel.BLUETOOTH)
            handshake(peerName)
        }, "gsynk-bt-host").start()
    }

    @SuppressLint("MissingPermission")
    private fun connectWifiDirect(context: Context, peer: Peer) {
        val manager = p2pManager(context)
        val ch = p2pChannel(context, manager ?: return) ?: return
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.address
            wps.setup = WpsInfo.PBC
        }
        activeChannel = Channel.WIFI_DIRECT
        manager.connect(ch, config, simpleListener("подключение Wi-Fi Direct"))
    }

    /** Договариваемся, кто хозяин сети, и открываем канал. */
    @SuppressLint("MissingPermission")
    private fun onP2pInfo(info: WifiP2pInfo?) {
        if (info == null || !info.groupFormed) return
        val owner = info.groupOwnerAddress?.hostAddress.orEmpty()
        Thread({
            if (info.isGroupOwner) {
                val server = runCatching { ServerSocket() }.getOrNull() ?: return@Thread
                runCatching { server.reuseAddress = true }
                val bound = runCatching { server.bind(InetSocketAddress(PORT)) }.isSuccess
                if (!bound) {
                    _status.value = Status.Failed("порт $PORT занят — закройте игру и повторите")
                    runCatching { server.close() }
                    return@Thread
                }
                tcpServer = server
                val socket = runCatching { server.accept() }.getOrNull()
                runCatching { server.close() }
                tcpServer = null
                if (socket == null) return@Thread
                val peer = Peer(
                    name = socket.inetAddress?.hostAddress.orEmpty(),
                    address = socket.inetAddress?.hostAddress.orEmpty(),
                    channel = Channel.WIFI_DIRECT,
                    kind = Kind.DIRECT
                )
                adopt(TcpStreams(socket), peer, Channel.WIFI_DIRECT)
                handshake("gsynk-${Build.MODEL.take(12)}")
            } else {
                val socket = runCatching { Socket() }.getOrNull() ?: return@Thread
                val ok = runCatching {
                    socket.connect(InetSocketAddress(owner, PORT), 8_000)
                    true
                }.getOrDefault(false)
                if (!ok) {
                    _status.value = Status.Failed("хозяин сети не отвечает — пусть нажмёт «Открыть комнату»")
                    runCatching { socket.close() }
                    return@Thread
                }
                val peer = Peer(
                    name = owner,
                    address = owner,
                    channel = Channel.WIFI_DIRECT,
                    kind = Kind.DIRECT
                )
                adopt(TcpStreams(socket), peer, Channel.WIFI_DIRECT)
                handshake("gsynk-${Build.MODEL.take(12)}")
            }
        }, "gsynk-p2p").start()
    }

    /** Хозяин быстрого канала по Wi-Fi Direct: ждём гостя на общем порту. */
    fun hostFast(): Boolean = runCatching {
        Thread({
            val server = ServerSocket()
            server.reuseAddress = true
            server.bind(InetSocketAddress(PORT))
            tcpServer = server
            val socket = server.accept()
            runCatching { server.close() }
            tcpServer = null
            val peer = Peer("гость", socket.inetAddress?.hostAddress.orEmpty(), Channel.WIFI_DIRECT, Kind.DIRECT)
            adopt(TcpStreams(socket), peer, Channel.WIFI_DIRECT)
            handshake("gsynk-host")
        }, "gsynk-fast-host").apply { isDaemon = true }.start()
        true
    }.getOrDefault(false)

    // ————————————— сам канал —————————————

    interface Streams {
        fun input(): InputStream
        fun output(): OutputStream
        fun close()
        val address: String
    }

    private class BluetoothStreams(private val socket: BluetoothSocket) : Streams {
        override fun input(): InputStream = socket.inputStream
        override fun output(): OutputStream = socket.outputStream
        override fun close() = runCatching { socket.close() }.let { }
        override val address: String get() = runCatching { socket.remoteDevice.address }.getOrDefault("")
    }

    private class TcpStreams(private val socket: Socket) : Streams {
        override fun input(): InputStream = socket.getInputStream()
        override fun output(): OutputStream = socket.getOutputStream()
        override fun close() = runCatching { socket.close() }.let { }
        override val address: String get() = socket.inetAddress?.hostAddress.orEmpty()
    }

    private fun adopt(streams: Streams, peer: Peer, channel: Channel) {
        closeChannel()
        socketStream = streams
        activeChannel = channel
        writer = runCatching { java.io.OutputStreamWriter(streams.output(), Charsets.UTF_8) }.getOrNull()
        _status.value = Status.Ready(peer, channel)
        _peers.update { current -> (current.filterNot { it.address == peer.address } + peer) }
        log("Связь установлена: " + peer.label + " (" + channel.title + ")")
        Thread({ readLoop(streams) }, "gsynk-read").apply { isDaemon = true }.start()
    }

    private fun readLoop(streams: Streams) {
        runCatching {
            val reader = BufferedReader(InputStreamReader(streams.input(), Charsets.UTF_8))
            while (true) {
                val line = runCatching { reader.readLine() }.getOrNull() ?: break
                if (line.isBlank()) continue
                handle(line.trim())
            }
        }
        if (socketStream === streams) {
            closeChannel()
            _status.value = Status.Idle
            log("Связь закончилась")
        }
    }

    private fun handshake(name: String) {
        send(line(HELLO, "1", name))
    }

    /** Отправляет строку по текущему каналу. */
    fun send(text: String): Boolean {
        val out = writer ?: return false
        return runCatching {
            out.write(text + "\n")
            out.flush()
            true
        }.getOrDefault(false)
    }

    private fun line(vararg parts: String): String = parts.joinToString("|")

    private fun handle(raw: String) {
        val parts = raw.split("|")
        when (parts.getOrNull(0)) {
            HELLO -> {
                _partner.value = parts.getOrNull(2).orEmpty()
                send(line(OK, "1", Build.MODEL))
                log("На связи: " + _partner.value)
            }

            OK -> {
                _partner.value = parts.getOrNull(2).orEmpty()
                log("Друг ответил: " + _partner.value)
            }

            SHOW -> {
                val body = parts.getOrNull(1).orEmpty()
                val decoded = NfcShow.decode(body)
                if (decoded != null) {
                    NfcShow.show(decoded.first, decoded.second, _partner.value)
                    log("Показ: " + decoded.first.label.lowercase())
                }
            }

            UPDATE -> {
                val share = UpdateShare.parse(parts.drop(1).joinToString("|"))
                if (share != null) {
                    UpdateShare.put(share)
                    log("Передано обновление " + share.version)
                }
            }

            UPDCHK -> {
                UpdateShare.putPing(parts.getOrNull(1).orEmpty())
                log("Просят проверить обновление")
            }

            PROFILE -> {
                val body = decodeText(parts.drop(1).joinToString("|"))
                if (body.isNotBlank()) {
                    _incomingProfile.value = body
                    log("Пришёл сценарий — можно применить")
                }
            }

            SAY -> log("Сообщение: " + parts.drop(1).joinToString("|"))

            HOST -> {
                _hostAddress.value = parts.getOrNull(1).orEmpty()
                log("Хозяин сети: " + _hostAddress.value)
            }

            PING -> {
                // отвечаем мгновенно: по этому ответу собеседник посчитает задержку
                send(line(PONG, parts.getOrNull(1).orEmpty()))
            }

            PONG -> {
                val id = parts.getOrNull(1)?.toIntOrNull() ?: 0
                if (id == lastPingId) {
                    val spent = System.currentTimeMillis() - lastPingSent
                    _latency.value = spent.coerceAtLeast(1L)
                    log("Задержка связи: " + _latency.value + " мс")
                }
            }

            BATTERY -> {
                _partnerBattery.value = parts.getOrNull(1)?.toIntOrNull() ?: -1
                log("Заряд собеседника: " + _partnerBattery.value + " проц.")
            }

            ISLAND -> {
                _incomingIsland.value = decodeText(parts.drop(1).joinToString("|"))
                log("На остров: " + _incomingIsland.value)
            }

            FILE -> {
                val body = decodeText(parts.drop(1).joinToString("|"))
                val bits = body.split(";")
                val name = bits.getOrNull(0).orEmpty()
                val size = bits.getOrNull(1)?.toLongOrNull() ?: 0L
                val base64 = bits.getOrNull(2).orEmpty()
                val ctx = appContext
                if (ctx != null && name.isNotBlank() && base64.isNotBlank()) {
                    val saved = FileLink.readInline(ctx, name, size, base64)
                    log(if (saved) "Файл «$name» сохранён" else "Файл «$name» не сохранился")
                }
            }
        }
    }

    // ————————————— что умеем отправлять —————————————

    fun sendShow(kind: NfcShow.Kind, text: String): Boolean =
        send(line(SHOW, NfcShow.encode(kind, text)))

    fun sendUpdate(share: UpdateShare.Share): Boolean =
        send(line(UPDATE, UpdateShare.body(share)))

    fun sendUpdatePing(name: String): Boolean =
        send(line(UPDCHK, name.replace("|", " ")))

    fun sendProfile(name: String, body: String): Boolean =
        send(line(PROFILE, encodeText(body)))

    fun sendSay(text: String): Boolean = send(line(SAY, text.replace("|", " ")))

    /** Отправляет пинг: второй телефон ответит, и мы узнаем задержку. */
    fun ping(): Boolean {
        counter++
        lastPingSent = System.currentTimeMillis()
        lastPingId = counter
        return send(line(PING, counter.toString()))
    }

    /** «Я хозяин»: гость запоминает адрес и потом заходит в игру одним нажатием. */
    fun sendHost(address: String = WifiCoop.localAddress()): Boolean {
        _hostAddress.value = address
        return send(line(HOST, address))
    }

    /** Отправляет свой заряд, чтобы собеседник видел его на экране связи. */
    fun sendBattery(percent: Int): Boolean = send(line(BATTERY, percent.toString()))

    /** Пишет текст на острове собеседника (если у него включён остров). */
    fun sendIslandText(text: String): Boolean = send(line(ISLAND, encodeText(text)))

    /** Отправляет визитку: имя и телефон. */
    fun sendContact(name: String, phone: String): Boolean = send(line(SAY, "визитка: " + name + " • " + phone))

    /** Отправляет точку на карте. */
    fun sendGeo(lat: Double, lon: Double): Boolean =
        send(line(SAY, "точка: " + "%.5f".format(lat) + ", " + "%.5f".format(lon)))

    /** Короткий файл (до ~600 байт) уходит прямо в строке — метка не нужна. */
    fun sendInlineFile(name: String, size: Long, base64: String): Boolean =
        send(line(FILE, encodeText(name + ";" + size + ";" + base64)))

    fun isConnected(): Boolean = socketStream != null

    fun activeChannel(): Channel = activeChannel

    // ————————————— уборка —————————————

    @SuppressLint("MissingPermission")
    fun stopDiscovery(context: Context) {
        if (hasPermissions(context, Channel.BLUETOOTH)) {
            runCatching { adapter(context)?.cancelDiscovery() }
        }
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        if (hasPermissions(context, Channel.WIFI_DIRECT)) {
            runCatching { p2p?.removeGroup(p2pChannel, simpleListener("закрываю группу")) }
        }
    }

    fun disconnect(context: Context) {
        closeChannel()
        _status.value = Status.Idle
        _partner.value = ""
        stopDiscovery(context)
    }

    private fun closeChannel() {
        runCatching { writer?.flush() }
        writer = null
        val streams = socketStream
        socketStream = null
        runCatching { streams?.close() }
        runCatching { btServer?.close() }
        btServer = null
        runCatching { tcpServer?.close() }
        tcpServer = null
    }

    fun reset() {
        _latency.value = 0L
        _partnerBattery.value = -1
        _incomingIsland.value = null
        _hostAddress.value = ""
        _peers.value = emptyList()
        _log.value = emptyList()
        _status.value = Status.Idle
        _partner.value = ""
        _incomingProfile.value = null
    }

    private fun log(text: String) {
        counter++
        _log.update { current -> (current + text).takeLast(60) }
        AppLog.event("Рядом", text)
    }

    // ————————————— помощники —————————————

    private fun register(context: Context, listener: BroadcastReceiver, filter: IntentFilter) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(listener, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(listener, filter)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun p2pManager(context: Context): WifiP2pManager? = runCatching {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun p2pChannel(context: Context, manager: WifiP2pManager): WifiP2pManager.Channel? {
        p2p = manager
        val existing = p2pChannel
        if (existing != null) return existing
        return runCatching {
            manager.initialize(context, Looper.getMainLooper(), null)?.also { p2pChannel = it }
        }.getOrNull()
    }

    private fun simpleListener(what: String) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            log("$what: ok")
        }

        override fun onFailure(reason: Int) {
            log("$what: не вышло (код $reason)")
            _status.value = Status.Failed("$what не удалось (код $reason)")
        }
    }

    private fun encodeText(value: String): String = runCatching {
        android.util.Base64.encodeToString(
            value.replace("\n", " ").toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
    }.getOrDefault("")

    private fun decodeText(value: String): String = runCatching {
        String(android.util.Base64.decode(value, android.util.Base64.NO_WRAP), Charsets.UTF_8)
    }.getOrDefault("")

    // ————— метки протокола (строки короткие: канал медленный) —————
    const val HELLO = "HELLO"
    const val OK = "OK"
    const val SHOW = "SHOW"
    const val UPDATE = "UPDATE"
    const val UPDCHK = "UPDCHK"
    const val PROFILE = "PROFILE"
    const val SAY = "SAY"
    const val HOST = "HOST"
    const val PING = "PING"
    const val PONG = "PONG"
    const val BATTERY = "BATT"
    const val ISLAND = "ISLE"
    const val FILE = "FILE"
}
