package com.gsynk.app.core

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.roundToInt

/**
 * Провод подземелья: превращает мир игры в одну текстовую строку и обратно.
 *
 * Зачем текст, а не бинарный формат: строка читаема в журнале, её легко проверить
 * тестом без телефона, и она переживает любую версию протокола — лишние поля
 * просто игнорируются. Дроби передаём целыми («милли-единицы», ×10000), поэтому
 * в кадре нет ни точек, ни запятых, и никакая локаль на разбор не влияет.
 *
 * Формат (строки через `\n`, элементы через `|`):
 * ```
 * W |версия|seed|этаж|комната|счёт|время|дверь|конец|победа|время баннера|баннер(b64)
 * P |id|x|y|размер|hp|hp макс|урон|скорость|темп|перезарядка|неуязвимость|вспышка|убийства
 * E |id|вид|x|y|размер|hp|hp макс|скорость|перезарядка|урон|фаза|вспышка
 * S |x|y|vx|vy|урон|от игрока|вражеский
 * K |x|y|вид|жизнь
 * R |x|y|ширина|высота
 * ```
 */
object CoopCodec {

    const val VERSION = 1

    /** Во сколько раз увеличиваем дробь при передаче. */
    private const val SCALE = 10_000f

    // ————— числа —————

    private fun pack(value: Float): Int = (value * SCALE).roundToInt()

    private fun unpack(value: Int): Float = value / SCALE

    // ————— мир —————

    fun encode(world: RogueGame.World): String {
        val sb = StringBuilder(768)
        sb.append("W|").append(VERSION)
            .append('|').append(world.seed)
            .append('|').append(world.floor)
            .append('|').append(world.room)
            .append('|').append(world.score)
            .append('|').append(pack(world.time))
            .append('|').append(if (world.doorOpen) 1 else 0)
            .append('|').append(if (world.over) 1 else 0)
            .append('|').append(if (world.won) 1 else 0)
            .append('|').append(pack(world.bannerTime))
            .append('|').append(encodeText(world.banner))
            .append('\n')

        world.players.forEach { p ->
            sb.append("P|").append(p.id)
                .append('|').append(pack(p.x))
                .append('|').append(pack(p.y))
                .append('|').append(pack(p.size))
                .append('|').append(p.hp)
                .append('|').append(p.maxHp)
                .append('|').append(p.damage)
                .append('|').append(pack(p.speed))
                .append('|').append(pack(p.rate))
                .append('|').append(pack(p.cooldown))
                .append('|').append(pack(p.invulnerable))
                .append('|').append(pack(p.hitFlash))
                .append('|').append(p.kills)
                .append('\n')
        }

        world.enemies.forEach { e ->
            sb.append("E|").append(e.id)
                .append('|').append(e.kind.name)
                .append('|').append(pack(e.x))
                .append('|').append(pack(e.y))
                .append('|').append(pack(e.size))
                .append('|').append(e.hp)
                .append('|').append(e.maxHp)
                .append('|').append(pack(e.speed))
                .append('|').append(pack(e.cooldown))
                .append('|').append(e.damage)
                .append('|').append(pack(e.phase))
                .append('|').append(pack(e.hitFlash))
                .append('\n')
        }

        world.shots.forEach { s ->
            sb.append("S|").append(pack(s.x))
                .append('|').append(pack(s.y))
                .append('|').append(pack(s.vx))
                .append('|').append(pack(s.vy))
                .append('|').append(s.damage)
                .append('|').append(s.fromPlayer)
                .append('|').append(if (s.hostile) 1 else 0)
                .append('\n')
        }

        world.pickups.forEach { k ->
            sb.append("K|").append(pack(k.x))
                .append('|').append(pack(k.y))
                .append('|').append(k.kind.name)
                .append('|').append(pack(k.life))
                .append('\n')
        }

        world.rocks.forEach { r ->
            sb.append("R|").append(pack(r.x))
                .append('|').append(pack(r.y))
                .append('|').append(pack(r.w))
                .append('|').append(pack(r.h))
                .append('\n')
        }

        return sb.toString()
    }

    fun decode(text: String): RogueGame.World? {
        if (text.isBlank()) return null
        val players = ArrayList<RogueGame.Player>()
        val enemies = ArrayList<RogueGame.Enemy>()
        val shots = ArrayList<RogueGame.Shot>()
        val pickups = ArrayList<RogueGame.Pickup>()
        val rocks = ArrayList<RogueGame.Box>()
        var world: RogueGame.World? = null

        text.lineSequence().forEach { line ->
            val f = line.split('|')
            when (f.getOrNull(0)) {
                "W" -> {
                    if (f.size < 12) return@forEach
                    world = RogueGame.World(
                        seed = f[2].toLongOrNull() ?: 0L,
                        floor = f[3].toIntOrNull() ?: 1,
                        room = f[4].toIntOrNull() ?: 1,
                        score = f[5].toIntOrNull() ?: 0,
                        time = unpack(f[6].toIntOrNull() ?: 0),
                        doorOpen = f[7] == "1",
                        over = f[8] == "1",
                        won = f[9] == "1",
                        bannerTime = unpack(f[10].toIntOrNull() ?: 0),
                        banner = decodeText(f[11])
                    )
                }

                "P" -> {
                    if (f.size < 14) return@forEach
                    players += RogueGame.Player(
                        id = f[1].toIntOrNull() ?: 0,
                        x = unpack(f[2].toIntOrNull() ?: 0),
                        y = unpack(f[3].toIntOrNull() ?: 0),
                        size = unpack(f[4].toIntOrNull() ?: 0),
                        hp = f[5].toIntOrNull() ?: 0,
                        maxHp = f[6].toIntOrNull() ?: 1,
                        damage = f[7].toIntOrNull() ?: 1,
                        speed = unpack(f[8].toIntOrNull() ?: 0),
                        rate = unpack(f[9].toIntOrNull() ?: 0),
                        cooldown = unpack(f[10].toIntOrNull() ?: 0),
                        invulnerable = unpack(f[11].toIntOrNull() ?: 0),
                        hitFlash = unpack(f[12].toIntOrNull() ?: 0),
                        kills = f[13].toIntOrNull() ?: 0
                    )
                }

                "E" -> {
                    if (f.size < 13) return@forEach
                    enemies += RogueGame.Enemy(
                        id = f[1].toIntOrNull() ?: 0,
                        kind = runCatching { RogueGame.EnemyKind.valueOf(f[2]) }
                            .getOrDefault(RogueGame.EnemyKind.CHASER),
                        x = unpack(f[3].toIntOrNull() ?: 0),
                        y = unpack(f[4].toIntOrNull() ?: 0),
                        size = unpack(f[5].toIntOrNull() ?: 0),
                        hp = f[6].toIntOrNull() ?: 0,
                        maxHp = f[7].toIntOrNull() ?: 1,
                        speed = unpack(f[8].toIntOrNull() ?: 0),
                        cooldown = unpack(f[9].toIntOrNull() ?: 0),
                        damage = f[10].toIntOrNull() ?: 1,
                        phase = unpack(f[11].toIntOrNull() ?: 0),
                        hitFlash = unpack(f[12].toIntOrNull() ?: 0)
                    )
                }

                "S" -> {
                    if (f.size < 7) return@forEach
                    shots += RogueGame.Shot(
                        x = unpack(f[1].toIntOrNull() ?: 0),
                        y = unpack(f[2].toIntOrNull() ?: 0),
                        vx = unpack(f[3].toIntOrNull() ?: 0),
                        vy = unpack(f[4].toIntOrNull() ?: 0),
                        damage = f[5].toIntOrNull() ?: 0,
                        fromPlayer = f[6].toIntOrNull() ?: 0,
                        hostile = f[7] == "1"
                    )
                }

                "K" -> {
                    if (f.size < 4) return@forEach
                    pickups += RogueGame.Pickup(
                        x = unpack(f[1].toIntOrNull() ?: 0),
                        y = unpack(f[2].toIntOrNull() ?: 0),
                        kind = runCatching { RogueGame.PickupKind.valueOf(f[3]) }
                            .getOrDefault(RogueGame.PickupKind.HEART),
                        life = f.getOrNull(4)?.toIntOrNull()?.let { unpack(it) } ?: 14f
                    )
                }

                "R" -> {
                    if (f.size < 5) return@forEach
                    rocks += RogueGame.Box(
                        x = unpack(f[1].toIntOrNull() ?: 0),
                        y = unpack(f[2].toIntOrNull() ?: 0),
                        w = unpack(f[3].toIntOrNull() ?: 0),
                        h = unpack(f[4].toIntOrNull() ?: 0)
                    )
                }
            }
        }

        val base = world ?: return null
        return base.copy(
            players = players.ifEmpty { base.players },
            enemies = enemies,
            shots = shots,
            pickups = pickups,
            rocks = rocks
        )
    }

    // ————— ввод игрока —————

    fun encodeInput(input: RogueGame.Input): String =
        "I|" + pack(input.moveX) + '|' + pack(input.moveY) + '|' + if (input.shooting) 1 else 0

    fun decodeInput(text: String): RogueGame.Input? {
        val f = text.split('|')
        if (f.getOrNull(0) != "I" || f.size < 4) return null
        val x = f[1].toIntOrNull() ?: return null
        val y = f[2].toIntOrNull() ?: return null
        return RogueGame.Input(moveX = unpack(x), moveY = unpack(y), shooting = f[3] == "1")
    }

    /** Приветствие гостя: версия, код комнаты и имя телефона. */
    fun encodeHello(code: String, name: String): String =
        "HELLO|" + VERSION + '|' + code + '|' + encodeText(name)

    /** Ответ хозяина: ключ подземелья. */
    fun encodeWelcome(seed: Long): String = "OK|" + seed

    data class Hello(val version: Int, val code: String, val name: String)

    fun decodeHello(text: String): Hello? {
        val f = text.split('|')
        if (f.getOrNull(0) != "HELLO" || f.size < 3) return null
        return Hello(
            version = f[1].toIntOrNull() ?: 0,
            code = f[2],
            name = f.getOrNull(3)?.let { decodeText(it) }.orEmpty()
        )
    }

    fun decodeWelcome(text: String): Long? {
        val f = text.split('|')
        if (f.getOrNull(0) != "OK") return null
        return f.getOrNull(1)?.toLongOrNull()
    }

    // ————— строки с кириллицей —————

    private fun encodeText(value: String): String = runCatching {
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }.getOrDefault("")

    private fun decodeText(value: String): String = runCatching {
        String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrDefault("")
}
