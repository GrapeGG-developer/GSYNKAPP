package com.gsynk.app.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Настоящая игра для Grape Link: подземелье сверху вниз в духе The Binding of Isaac.
 *
 * Комнаты, камни, враги трёх видов и босс, слёзы вместо пуль, предметы, этажи
 * и счёт. Один или два игрока на одном экране. Всё состояние — чистые данные,
 * поэтому движок считается на телефоне в реальном времени и полностью покрыт тестами.
 *
 * Мир измеряется в единицах комнаты: 0..1 по обеим осям, отрисовка просто
 * растягивает её на холст. Благодаря этому игра одинаково выглядит на любом экране.
 */
object RogueGame {

    const val WORLD = 1f
    const val ROOMS_PER_FLOOR = 5

    enum class Dir { NORTH, EAST, SOUTH, WEST }

    enum class EnemyKind { CHASER, SHOOTER, FLYER, BOSS }

    enum class PickupKind { HEART, DAMAGE, SPEED, RATE }

    data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
        fun overlaps(other: Box, inset: Float = 0f): Boolean =
            x + inset < other.x + other.w && x + w - inset > other.x &&
                y + inset < other.y + other.h && y + h - inset > other.y

        fun centerX(): Float = x + w / 2f
        fun centerY(): Float = y + h / 2f
    }

    data class Player(
        val id: Int,
        val x: Float,
        val y: Float,
        val size: Float = 0.062f,
        val hp: Int = 6,
        val maxHp: Int = 6,
        val speed: Float = 0.62f,
        val damage: Int = 2,
        val cooldown: Float = 0f,
        val rate: Float = 0.34f,
        val invulnerable: Float = 0f,
        val hitFlash: Float = 0f,
        val kills: Int = 0,
        val steps: Float = 0f
    ) {
        val down: Boolean get() = hp <= 0
        fun box(): Box = Box(x - size / 2f, y - size / 2f, size, size)
    }

    data class Enemy(
        val id: Int,
        val kind: EnemyKind,
        val x: Float,
        val y: Float,
        val size: Float,
        val hp: Int,
        val maxHp: Int,
        val speed: Float,
        val cooldown: Float = 0f,
        val damage: Int = 1,
        val phase: Float = 0f,
        val hitFlash: Float = 0f
    ) {
        fun box(): Box = Box(x - size / 2f, y - size / 2f, size, size)
    }

    /** Слеза игрока или снаряд врага — маленький прямоугольник. */
    data class Shot(
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
        val damage: Int,
        val fromPlayer: Int,
        val hostile: Boolean,
        val life: Float = 2.2f
    )

    data class Pickup(val x: Float, val y: Float, val kind: PickupKind, val life: Float = 14f)

    data class Input(
        val moveX: Float = 0f,
        val moveY: Float = 0f,
        val shooting: Boolean = false
    )

    data class World(
        val seed: Long,
        val floor: Int = 1,
        val room: Int = 1,
        val players: List<Player> = listOf(Player(0, 0.5f, 0.68f)),
        val enemies: List<Enemy> = emptyList(),
        val shots: List<Shot> = emptyList(),
        val pickups: List<Pickup> = emptyList(),
        val rocks: List<Box> = emptyList(),
        val doorOpen: Boolean = false,
        val score: Int = 0,
        val time: Float = 0f,
        val banner: String = "",
        val bannerTime: Float = 0f,
        val over: Boolean = false,
        val won: Boolean = false
    ) {
        val alivePlayers: List<Player> get() = players.filter { !it.down }
        val isBossRoom: Boolean get() = room == ROOMS_PER_FLOOR
    }

    fun newSeed(): Long = Random.Default.nextLong(1L, 9_000_000_000L)

    /** Стартовое состояние подземелья. */
    fun start(seed: Long, playerCount: Int = 1): World {
        val players = when (playerCount.coerceIn(1, 2)) {
            1 -> listOf(Player(0, 0.5f, 0.62f))
            else -> listOf(
                Player(0, 0.38f, 0.62f),
                Player(1, 0.62f, 0.62f, damage = 2, rate = 0.36f)
            )
        }
        return buildRoom(World(seed = seed, players = players), floor = 1, room = 1)
    }

    /** Собирает комнату: камни, враги, счёт и состояние двери. */
    fun buildRoom(world: World, floor: Int, room: Int): World {
        val random = Random(world.seed * 31 + floor * 977 + room)
        val rocks = ArrayList<Box>()
        val rockCount = 3 + random.nextInt(4) + floor
        repeat(rockCount) {
            val w = 0.07f + random.nextFloat() * 0.05f
            val h = 0.07f + random.nextFloat() * 0.05f
            val x = 0.08f + random.nextFloat() * (WORLD - 0.16f - w)
            val y = 0.16f + random.nextFloat() * (WORLD - 0.30f - h)
            // не заваливаем камнями вход: там стоят игроки
            if (abs(x + w / 2f - 0.5f) < 0.22f && y + h / 2f > 0.55f) return@repeat
            rocks.add(Box(x, y, w, h))
        }

        val isBoss = room == ROOMS_PER_FLOOR
        val enemies = ArrayList<Enemy>()
        var id = 0
        if (isBoss) {
            val hp = 40 + floor * 26
            enemies.add(
                Enemy(
                    id = id++,
                    kind = EnemyKind.BOSS,
                    x = 0.5f,
                    y = 0.32f,
                    size = 0.20f,
                    hp = hp,
                    maxHp = hp,
                    speed = 0.16f + floor * 0.012f,
                    damage = 2
                )
            )
        } else {
            val count = 2 + floor / 2 + room / 2
            repeat(count) { index ->
                val kind = when {
                    floor >= 3 && index % 4 == 3 -> EnemyKind.SHOOTER
                    index % 3 == 2 -> EnemyKind.FLYER
                    else -> EnemyKind.CHASER
                }
                val hp = when (kind) {
                    EnemyKind.FLYER -> 2 + floor / 2
                    EnemyKind.SHOOTER -> 3 + floor / 2
                    EnemyKind.CHASER -> 4 + floor
                    EnemyKind.BOSS -> 0
                }
                val spreadX = 0.16f + (index % 4) * 0.22f
                val spreadY = 0.20f + (index / 4) * 0.18f
                enemies.add(
                    Enemy(
                        id = id++,
                        kind = kind,
                        x = spreadX.coerceIn(0.12f, 0.88f),
                        y = spreadY.coerceIn(0.14f, 0.62f),
                        size = if (kind == EnemyKind.FLYER) 0.05f else 0.065f,
                        hp = hp,
                        maxHp = hp,
                        speed = when (kind) {
                            EnemyKind.CHASER -> 0.20f + floor * 0.02f
                            EnemyKind.SHOOTER -> 0.15f
                            EnemyKind.FLYER -> 0.34f + floor * 0.02f
                            EnemyKind.BOSS -> 0f
                        },
                        damage = 1,
                        phase = random.nextFloat() * 6.28f
                    )
                )
            }
        }

        return world.copy(
            floor = floor,
            room = room,
            enemies = enemies,
            rocks = rocks,
            shots = emptyList(),
            pickups = emptyList(),
            doorOpen = false,
            banner = if (isBoss) "ЭТАЖ $floor · БОСС" else "ЭТАЖ $floor · КОМНАТА $room",
            bannerTime = 2.2f,
            over = false
        )
    }

    /** Один шаг мира. [dt] — секунды, обычно 1/60. */
    fun step(world: World, dt: Float, inputs: List<Input>): World {
        if (world.over) return world
        val d = dt.coerceIn(0f, 0.05f)
        var shots = world.shots.toMutableList()
        var pickups = world.pickups.filter { it.life > 0f }.toMutableList()
        var score = world.score
        val random = Random(world.seed + (world.time * 1000).toLong())

        // ————— игроки —————
        val players = world.players.map { player ->
            if (player.down) return@map player
            val input = inputs.getOrNull(player.id) ?: Input()
            val len = hypot(input.moveX, input.moveY)
            val (nx, ny) = if (len > 0.08f) {
                val dx = input.moveX / len
                val dy = input.moveY / len
                val nextX = (player.x + dx * player.speed * d).coerceIn(0.04f, WORLD - 0.04f)
                val nextY = (player.y + dy * player.speed * d).coerceIn(0.08f, WORLD - 0.04f)
                nextX to nextY
            } else {
                player.x to player.y
            }

            // столкновение с камнями: сдвигаем по осям отдельно
            var px = nx
            var py = player.y
            if (world.rocks.any { it.overlaps(Box(px - player.size / 2f, py - player.size / 2f, player.size, player.size), 0.004f) }) {
                px = player.x
            }
            var py2 = ny
            if (world.rocks.any { it.overlaps(Box(px - player.size / 2f, py2 - player.size / 2f, player.size, player.size), 0.004f) }) {
                py2 = py
            }

            var cooldown = (player.cooldown - d).coerceAtLeast(0f)
            if (input.shooting && cooldown <= 0f && len > 0.08f) {
                val dirX = input.moveX / len
                val dirY = input.moveY / len
                shots.add(
                    Shot(
                        x = px + dirX * 0.05f,
                        y = py2 + dirY * 0.05f,
                        vx = dirX * 0.95f,
                        vy = dirY * 0.95f,
                        damage = player.damage,
                        fromPlayer = player.id,
                        hostile = false
                    )
                )
                cooldown = player.rate
            }

            player.copy(
                x = px,
                y = py2,
                cooldown = cooldown,
                invulnerable = (player.invulnerable - d).coerceAtLeast(0f),
                hitFlash = (player.hitFlash - d).coerceAtLeast(0f),
                steps = player.steps + len * d
            )
        }.toMutableList()

        // ————— враги —————
        val alivePlayers = players.filter { !it.down }
        val enemies = world.enemies.mapNotNull { enemy ->
            if (enemy.hp <= 0) return@mapNotNull null
            val target = alivePlayers.minByOrNull { hypot(it.x - enemy.x, it.y - enemy.y) }
            var ex = enemy.x
            var ey = enemy.y
            var cooldown = (enemy.cooldown - d).coerceAtLeast(0f)
            var phase = enemy.phase + d

            if (target != null) {
                val dx = target.x - enemy.x
                val dy = target.y - enemy.y
                val dist = hypot(dx, dy).coerceAtLeast(0.0001f)
                when (enemy.kind) {
                    EnemyKind.CHASER -> {
                        ex += dx / dist * enemy.speed * d
                        ey += dy / dist * enemy.speed * d
                    }

                    EnemyKind.FLYER -> {
                        ex += dx / dist * enemy.speed * d
                        ey += dy / dist * enemy.speed * d
                        ex += sin(phase * 3.4f) * 0.05f * d
                        ey += cos(phase * 2.7f) * 0.05f * d
                    }

                    EnemyKind.SHOOTER -> {
                        val want = 0.30f
                        val push = if (dist < want) -1f else 1f
                        ex += dx / dist * enemy.speed * d * push
                        ey += dy / dist * enemy.speed * d * push
                        if (cooldown <= 0f) {
                            shots.add(
                                Shot(
                                    x = enemy.x,
                                    y = enemy.y,
                                    vx = dx / dist * 0.42f,
                                    vy = dy / dist * 0.42f,
                                    damage = enemy.damage,
                                    fromPlayer = -1,
                                    hostile = true
                                )
                            )
                            cooldown = 1.9f
                        }
                    }

                    EnemyKind.BOSS -> {
                        val angle = phase * 0.7f
                        ex = 0.5f + cos(angle) * 0.26f
                        ey = 0.30f + sin(angle * 1.3f) * 0.14f
                        if (cooldown <= 0f) {
                            // веер из пяти снарядов
                            repeat(5) { index ->
                                val spread = (index - 2) * 0.22f
                                val base = if (target.y > enemy.y) 1.5707f else -1.5707f
                                val dir = base + spread
                                shots.add(
                                    Shot(
                                        x = enemy.x,
                                        y = enemy.y,
                                        vx = cos(dir) * 0.36f,
                                        vy = sin(dir) * 0.36f,
                                        damage = enemy.damage,
                                        fromPlayer = -1,
                                        hostile = true
                                    )
                                )
                            }
                            cooldown = 2.1f
                        }
                    }
                }
            }

            // враги не проходят сквозь камни и не выходят за стены
            val box = Box(ex - enemy.size / 2f, ey - enemy.size / 2f, enemy.size, enemy.size)
            if (world.rocks.any { it.overlaps(box, 0.004f) }) {
                ex = enemy.x
                ey = enemy.y
            }
            ex = ex.coerceIn(0.05f, WORLD - 0.05f)
            ey = ey.coerceIn(0.08f, WORLD - 0.05f)

            enemy.copy(
                x = ex,
                y = ey,
                cooldown = cooldown,
                phase = phase,
                hitFlash = (enemy.hitFlash - d).coerceAtLeast(0f)
            )
        }.toMutableList()

        // ————— снаряды —————
        val movingShots = ArrayList<Shot>()
        shots.forEach { shot ->
            val nx = shot.x + shot.vx * d
            val ny = shot.y + shot.vy * d
            val life = shot.life - d
            val outside = nx < 0.03f || nx > WORLD - 0.03f || ny < 0.05f || ny > WORLD - 0.03f
            val box = Box(nx - 0.014f, ny - 0.014f, 0.028f, 0.028f)
            if (life <= 0f || outside) return@forEach

            if (shot.hostile) {
                // попал в игрока?
                val hit = players.indexOfFirst { !it.down && it.box().overlaps(box, 0.006f) && it.invulnerable <= 0f }
                if (hit >= 0) {
                    val victim = players[hit]
                    players[hit] = victim.copy(
                        hp = (victim.hp - shot.damage).coerceAtLeast(0),
                        invulnerable = 0.9f,
                        hitFlash = 0.35f
                    )
                    return@forEach
                }
            } else {
                val rock = world.rocks.any { it.overlaps(box, 0.004f) }
                if (rock) return@forEach
                val hit = enemies.indexOfFirst { it.hp > 0 && it.box().overlaps(box, 0.006f) }
                if (hit >= 0) {
                    val victim = enemies[hit]
                    val hpAfter = victim.hp - shot.damage
                    enemies[hit] = victim.copy(hp = hpAfter, hitFlash = 0.2f)
                    if (hpAfter <= 0) {
                        score += if (victim.kind == EnemyKind.BOSS) 250 else 25 + victim.maxHp * 3
                        players[shot.fromPlayer.coerceIn(0, players.lastIndex)] =
                            players[shot.fromPlayer.coerceIn(0, players.lastIndex)].let {
                                it.copy(kills = it.kills + 1)
                            }
                        // с врага с некоторой вероятностью падает сердце
                        if (random.nextFloat() < 0.16f) {
                            pickups.add(Pickup(victim.x, victim.y, PickupKind.HEART))
                        }
                    }
                    return@forEach
                }
            }
            movingShots.add(shot.copy(x = nx, y = ny, life = life))
        }
        shots = movingShots

        // ————— столкновение вплотную —————
        players.forEachIndexed { index, player ->
            if (player.down || player.invulnerable > 0f) return@forEachIndexed
            val touch = enemies.firstOrNull { it.hp > 0 && it.box().overlaps(player.box(), 0.008f) }
            if (touch != null) {
                players[index] = player.copy(
                    hp = (player.hp - touch.damage).coerceAtLeast(0),
                    invulnerable = 0.9f,
                    hitFlash = 0.35f
                )
            }
        }

        // ————— подбор предметов —————
        val takenPickups = ArrayList<Pickup>()
        pickups.forEach { pickup ->
            val index = players.indexOfFirst {
                !it.down && hypot(it.x - pickup.x, it.y - pickup.y) < 0.07f
            }
            if (index >= 0) {
                val player = players[index]
                players[index] = when (pickup.kind) {
                    PickupKind.HEART -> player.copy(hp = (player.hp + 2).coerceAtMost(player.maxHp + 2), maxHp = (player.maxHp + 2).coerceAtMost(12))
                    PickupKind.DAMAGE -> player.copy(damage = player.damage + 1)
                    PickupKind.SPEED -> player.copy(speed = (player.speed + 0.07f).coerceAtMost(1.15f))
                    PickupKind.RATE -> player.copy(rate = (player.rate - 0.05f).coerceAtLeast(0.12f))
                }
                score += 10
                takenPickups.add(pickup)
            }
        }
        pickups = pickups.filterNot { it in takenPickups }.toMutableList()

        // ————— дверь: открывается, когда комната зачищена —————
        var doorOpen = enemies.isEmpty()
        var floor = world.floor
        var room = world.room
        var banner = world.banner
        var bannerTime = (world.bannerTime - d).coerceAtLeast(0f)
        var over = players.all { it.down }
        var won = false

        if (doorOpen && alivePlayers.isNotEmpty()) {
            val reach = alivePlayers.any { it.x > WORLD - 0.075f && it.y > 0.35f && it.y < 0.75f }
            if (reach) {
                if (room < ROOMS_PER_FLOOR) {
                    return buildRoom(
                        world.copy(players = players, score = score + 15, banner = "ДАЛЬШЕ"),
                        floor = floor,
                        room = room + 1
                    )
                } else {
                    // босс пройден — спускаемся ниже
                    floor += 1
                    room = 1
                    // награда за этаж: предмет по выбору случая
                    val reward = listOf(PickupKind.HEART, PickupKind.DAMAGE, PickupKind.SPEED, PickupKind.RATE)
                        .random(Random(world.seed + floor * 13))
                    pickups.add(Pickup(0.5f, 0.30f, reward))
                    banner = "ЭТАЖ $floor"
                    bannerTime = 2.6f
                    score += 200
                }
            }
        }

        return world.copy(
            floor = floor,
            room = room,
            players = players,
            enemies = enemies,
            shots = shots,
            pickups = pickups,
            doorOpen = doorOpen,
            score = score,
            time = world.time + d,
            banner = banner,
            bannerTime = bannerTime,
            over = over,
            won = won
        )
    }

    /** Победное завершение (когда пройден пятый этаж) — задел на длинную игру. */
    fun finish(world: World): World = world.copy(over = true, won = true, banner = "ПОДЗЕМЕЛЬЕ ПРОЙДЕНО")
}
