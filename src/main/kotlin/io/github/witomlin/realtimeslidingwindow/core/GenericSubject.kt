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

package io.github.witomlin.realtimeslidingwindow.core

import io.github.witomlin.realtimeslidingwindow.schedule.TaskScheduler
import java.util.*

abstract class GenericSubject<E : GenericSubject.Event, D>(private val taskScheduler: TaskScheduler) {
    abstract class Event {
        val name = this::class.simpleName ?: "Unknown"
    }

    private val observers = Collections.synchronizedList(mutableListOf<(E, D) -> Unit>())

    fun addObserver(observer: (E, D) -> Unit) = observers.add(observer)

    fun removeObserver(observer: (E, D) -> Unit) = observers.remove(observer)

    fun notifyObservers(event: E, data: D, async: Boolean) {
        synchronized(observers) {
            observers.forEach { if (async) taskScheduler.execute { it(event, data) } else it(event, data) }
        }
    }
}
