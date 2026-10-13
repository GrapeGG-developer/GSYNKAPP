package com.gsynk.app.core

import kotlin.random.Random

/**
 * Логика игры «2048» — вынесена из интерфейса, чтобы её можно было проверять тестами
 * без Android. Поле — список из 16 клеток, нули означают пустые.
 */
object TileGame {

    const val LEFT = 0
    const val RIGHT = 1
    const val UP = 2
    const val DOWN = 3

    val DIRECTIONS = listOf(LEFT, RIGHT, UP, DOWN)

    /** Сдвиг поля: возвращает новое поле и число очков за слияния. */
    fun slide(board: List<Int>, direction: Int): Pair<List<Int>, Int> {
        if (board.size != 16) return board to 0
        val grid = board.toMutableList()
        var gained = 0

        repeat(4) { line ->
            val cells = when (direction) {
                LEFT, RIGHT -> (0 until 4).map { grid[line * 4 + it] }
                else -> (0 until 4).map { grid[it * 4 + line] }
            }
            val reversed = direction == RIGHT || direction == DOWN
            val oriented = if (reversed) cells.reversed() else cells
            val (merged, gain) = mergeLine(oriented)
            gained += gain
            val back = if (reversed) merged.reversed() else merged
            repeat(4) { position ->
                if (direction == LEFT || direction == RIGHT) {
                    grid[line * 4 + position] = back[position]
                } else {
                    grid[position * 4 + line] = back[position]
                }
            }
        }
        return grid to gained
    }

    /** Сжатие и слияние одной линии: 2,2,4 → 4,4,0,0. */
    fun mergeLine(line: List<Int>): Pair<List<Int>, Int> {
        val compact = line.filter { it != 0 }
        val result = mutableListOf<Int>()
        var gained = 0
        var index = 0
        while (index < compact.size) {
            if (index + 1 < compact.size && compact[index] == compact[index + 1]) {
                val merged = compact[index] * 2
                result.add(merged)
                gained += merged
                index += 2
            } else {
                result.add(compact[index])
                index++
            }
        }
        while (result.size < 4) result.add(0)
        return result to gained
    }

    /** Новая плитка (2 или 4) в случайной пустой клетке. */
    fun spawn(board: List<Int>, random: Random = Random.Default): List<Int> {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return board
        val next = board.toMutableList()
        next[empty[random.nextInt(empty.size)]] = if (random.nextInt(10) == 0) 4 else 2
        return next
    }

    /** Стартовое поле: две плитки. */
    fun start(random: Random = Random.Default): List<Int> = spawn(spawn(List(16) { 0 }, random), random)

    /** Ходов больше нет: ни один сдвиг не меняет поле. */
    fun isStuck(board: List<Int>): Boolean = DIRECTIONS.all { slide(board, it).first == board }

    /** Есть ли на поле победная плитка. */
    fun hasReached(board: List<Int>, value: Int = 2048): Boolean = board.any { it >= value }

    fun maxTile(board: List<Int>): Int = board.maxOrNull() ?: 0
}
