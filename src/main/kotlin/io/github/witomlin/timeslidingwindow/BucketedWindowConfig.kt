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

import io.github.witomlin.timeslidingwindow.observability.Metrics
import io.github.witomlin.timeslidingwindow.schedule.TaskScheduler
import java.time.Duration
import kotlin.reflect.KClass

abstract class BucketedWindowConfig(
    val name: String,
    val length: Duration,
    val forDataClasses: List<KClass<*>>,
    val taskScheduler: TaskScheduler,
    val metrics: Metrics,
) {
    internal companion object {
        const val EXCEPTION_MESSAGE_NAME_EMPTY = "'name' must not be empty"
        const val EXCEPTION_MESSAGE_FOR_DATA_CLASSES_EMPTY = "'forDataClasses' must not be empty"
    }

    init {
        require(name.isNotBlank()) { EXCEPTION_MESSAGE_NAME_EMPTY }
        require(forDataClasses.isNotEmpty()) { EXCEPTION_MESSAGE_FOR_DATA_CLASSES_EMPTY }
    }
}
