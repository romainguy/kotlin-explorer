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

package dev.romainguy.kotlin.explorer

/** Based on Guava PeekingIterator */
class PeekingIterator<E : Any>(private val iterator: Iterator<E>) : Iterator<E> {
    private var peekedElement: E? = null

    override fun hasNext(): Boolean {
        return peekedElement != null || iterator.hasNext()
    }

    override fun next(): E {
        val element = peekedElement ?: return iterator.next()
        peekedElement = null
        return element
    }

    fun peek(): E {
        return peekedElement.takeIf { it != null } ?: iterator.next().also { peekedElement = it }
    }
}