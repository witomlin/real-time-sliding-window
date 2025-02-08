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

package io.github.witomlin.timeslidingwindow.windowimpl.ondemand

import io.github.witomlin.timeslidingwindow.BucketedWindowConfig
import io.github.witomlin.timeslidingwindow.observability.Metrics
import io.github.witomlin.timeslidingwindow.observability.NoOpMetrics
import io.github.witomlin.timeslidingwindow.schedule.DefaultTaskScheduler
import io.github.witomlin.timeslidingwindow.schedule.TaskScheduler
import java.time.Duration
import kotlin.reflect.KClass

class OnDemandBucketedWindowConfig(
    name: String,
    length: Duration,
    forDataClasses: List<KClass<*>>,
    taskScheduler: TaskScheduler = DefaultTaskScheduler(),
    metrics: Metrics = NoOpMetrics(),
    val maintenanceInterval: Duration = length.dividedBy(4),
) : BucketedWindowConfig(name, length, forDataClasses, taskScheduler, metrics) {
    internal companion object {
        const val CONFIG_MIN_LENGTH_MS = 250L
        const val CONFIG_MIN_MAINTENANCE_MS = 250L

        const val EXCEPTION_MESSAGE_LENGTH_INSUFFICIENT = "'length' must be >= ${CONFIG_MIN_LENGTH_MS}ms"
        const val EXCEPTION_MESSAGE_MAINTENANCE_TOO_SHORT =
            "'maintenanceInterval' must be >= ${CONFIG_MIN_MAINTENANCE_MS}ms"
    }

    init {
        require(length >= Duration.ofMillis(CONFIG_MIN_LENGTH_MS)) { EXCEPTION_MESSAGE_LENGTH_INSUFFICIENT }
        require(maintenanceInterval >= Duration.ofMillis(CONFIG_MIN_MAINTENANCE_MS)) {
            EXCEPTION_MESSAGE_MAINTENANCE_TOO_SHORT
        }
    }
}
