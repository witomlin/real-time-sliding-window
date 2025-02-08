/*
    Copyright 2025 Will Tomlin

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package io.github.witomlin.timeslidingwindow

import java.util.*

internal object WindowName {
    const val EXCEPTION_MESSAGE_NAME_ALREADY_REGISTERED = "Window name already registered"

    private val _windowNames = Collections.synchronizedList(mutableListOf<String>())
    val windowNames
        get() = _windowNames.toList()

    fun register(name: String) {
        check(!_windowNames.contains(name)) { EXCEPTION_MESSAGE_NAME_ALREADY_REGISTERED }

        _windowNames.add(name)
    }

    fun unregister(name: String) {
        _windowNames.remove(name)
    }
}
