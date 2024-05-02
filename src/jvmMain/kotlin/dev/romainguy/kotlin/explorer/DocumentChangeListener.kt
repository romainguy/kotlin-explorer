/*
 * Copyright (C) 2023 Romain Guy
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

import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * A [DocumentListener] that takes a single lambda that's invoked on any change
 */
class DocumentChangeListener(private val block: (DocumentEvent) -> Unit) : DocumentListener {
    override fun insertUpdate(event: DocumentEvent) {
        block(event)
    }

    override fun removeUpdate(event: DocumentEvent) {
        block(event)
    }

    override fun changedUpdate(event: DocumentEvent) {
        block(event)
    }
}