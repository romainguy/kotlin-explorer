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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.awt.Component
import javax.swing.FocusManager

/**
 * A [MutableState]<[Boolean]> that saves and restores the focus owner before and after a dialog is shown.
 *
 * This is probably related to [DialogSupportingSwingPanel] and sometimes causes a crash on Linux when:
 * * Click in a code panel to set focus
 * * Open Settings
 * * Close Settings
 * * Press any key
 *
 * TODO: Remove all this code
 * See https://github.com/JetBrains/compose-multiplatform-core/pull/915
 */
class DialogState(initial: Boolean) : MutableState<Boolean> {
    private var state = mutableStateOf(initial)
    private var focusOwner: Component? = null
    override var value: Boolean
        get() = state.value
        set(value) {
            if (value) {
                focusOwner = FocusManager.getCurrentManager().focusOwner
                println("Saving focus owner: $focusOwner")
            } else {
                println("Restoring focus owner: $focusOwner")
                focusOwner?.requestFocus()
            }
            state.value = value
        }

    override fun component1() = state.component1()

    override fun component2() = state.component2()
}