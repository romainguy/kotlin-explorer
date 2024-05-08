/*
 * Copyright (C) 2024 Romain Guy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package testData

inline fun Int.uncheckedCoerceIn(minimumValue: Int, maximumValue: Int) =
    this.coerceAtLeast(minimumValue).coerceAtMost(maximumValue)

inline fun Rect(l: Int, t: Int, r: Int, b: Int) =
    Rect((
            ((l.uncheckedCoerceIn(0, 8) and 0xf) shl 12) or
                    ((t.uncheckedCoerceIn(0, 8) and 0xf) shl  8) or
                    ((r.uncheckedCoerceIn(0, 8) and 0xf) shl  4) or
                    ((b.uncheckedCoerceIn(0, 8) and 0xf)       )
            ).toShort())

@JvmInline
value class Rect @PublishedApi internal constructor(@PublishedApi internal val points: Short) {
    inline val l: Int get() = points.toInt() ushr 12
    inline val t: Int get() = (points.toInt() shr 8) and 0xf
    inline val r: Int get() = (points.toInt() shr 4) and 0xf
    inline val b: Int get() = points.toInt() and 0xf

    override fun toString() = "Rect($l, $t, $r, $b)"
}

inline fun Grid() = Grid(0L)
inline fun Grid(r: Rect) = Grid(rasterize(r.l, r.t, r.r, r.b))
inline fun Grid(l: Int, t: Int, r: Int, b: Int) = Grid(rasterize(l, t, r, b))

@JvmInline
value class Grid @PublishedApi internal constructor(@PublishedApi internal val cells: Long) {
    inline fun forEach(block: (Int, Int) -> Unit) {
        var v = cells
        while (v.hasNext()) {
            val index = v.get()
            block(index and 0x7, index ushr 3)
            v = v.next()
        }
    }

    inline operator fun get(x: Int, y: Int) =
        ((cells ushr ((7 - y) shl 3)) and (0x1L shl (7 - x))) != 0L

    inline operator fun plus(r: Rect) =
        Grid(cells or rasterize(r.l, r.t, r.r, r.b))

    inline operator fun minus(r: Rect) =
        Grid(cells and rasterize(r.l, r.t, r.r, r.b).inv())

    inline infix fun and(r: Rect) =
        Grid(cells and rasterize(r.l, r.t, r.r, r.b))

    inline fun intersects(r: Rect) =
        (cells and rasterize(r.l, r.t, r.r, r.b)) != 0L

    override fun toString() = buildString {
        for (y in 0..7) {
            val line = (cells ushr (56 - y shl 3) and 0xffL).toString(2).padStart(8, '0')
            appendLine(line)
        }
    }
}

@PublishedApi
internal inline fun Long.get() = 63 - countTrailingZeroBits()

@PublishedApi
internal inline fun Long.hasNext() = this != 0L
@PublishedApi
internal inline fun Long.next() = this and (this - 1L)

@PublishedApi
internal fun rasterize(l: Int, t: Int, r: Int, b: Int): Long {
    val w = r - l
    val h = b - t
    val scanline = 0xffL ushr (8 - w) shl (8 - r)
    val rows = 0x01_01_01_01_01_01_01_01L ushr ((8 - h) shl 3) shl ((8 - b) shl 3)
    return rows * scanline
}

fun main() {
    val grid = Grid(1, 1, 5, 5)
    println(grid)
}
