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

package io.github.witomlin.realtimeslidingwindow.test

import io.github.witomlin.realtimeslidingwindow.core.GenericSubject
import io.github.witomlin.realtimeslidingwindow.schedule.TaskScheduler

class TestSubject(taskScheduler: TaskScheduler, private val async: Boolean) :
    GenericSubject<TestSubject.Event, String>(taskScheduler) {
    sealed class Event : GenericSubject.Event() {
        data object Event1 : Event()
    }

    fun notifyEvent() {
        notifyObservers(Event.Event1, "event1", async)
    }
}
