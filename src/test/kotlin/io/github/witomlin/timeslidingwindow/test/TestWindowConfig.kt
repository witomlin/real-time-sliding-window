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

package io.github.witomlin.timeslidingwindow.test

import io.github.witomlin.timeslidingwindow.observability.Metrics
import io.github.witomlin.timeslidingwindow.observability.NoOpMetrics
import io.github.witomlin.timeslidingwindow.schedule.TaskScheduler
import io.github.witomlin.timeslidingwindow.windowimpl.fixedtumbling.FixedTumblingBucketedWindowConfig
import io.github.witomlin.timeslidingwindow.windowimpl.ondemand.OnDemandBucketedWindowConfig
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

object TestWindowConfig {
    const val DEFAULT_NAME = "name"
    val DEFAULT_LENGTH: Duration = Duration.ofSeconds(5)
    val DEFAULT_BUCKET_LENGTH: Duration = Duration.ofSeconds(1)
    val DEFAULT_FOR_DATA_CLASSES = listOf(TestWindowBucketData::class)
    val DEFAULT_TASK_SCHEDULER =
        object : TaskScheduler {
            override fun scheduleFor(time: Instant, task: () -> Unit) {}

            override fun scheduleEvery(every: Duration, task: () -> Unit) {}

            override fun execute(task: () -> Unit) {}
        }
    val DEFAULT_METRICS
        get() = NoOpMetrics() // Mutable

    fun fixed(
        name: String = DEFAULT_NAME,
        length: Duration = DEFAULT_LENGTH,
        bucketLength: Duration = DEFAULT_BUCKET_LENGTH,
        forDataClasses: List<KClass<*>> = DEFAULT_FOR_DATA_CLASSES,
        taskScheduler: TaskScheduler = DEFAULT_TASK_SCHEDULER,
        metrics: Metrics = DEFAULT_METRICS,
    ): FixedTumblingBucketedWindowConfig {
        return FixedTumblingBucketedWindowConfig(name, length, bucketLength, forDataClasses, taskScheduler, metrics)
    }

    val DEFAULT_ON_DEMAND_MAINTENANCE_INTERVAL: Duration = DEFAULT_LENGTH.dividedBy(4)

    fun onDemand(
        name: String = DEFAULT_NAME,
        length: Duration = DEFAULT_LENGTH,
        forDataClasses: List<KClass<*>> = DEFAULT_FOR_DATA_CLASSES,
        taskScheduler: TaskScheduler = DEFAULT_TASK_SCHEDULER,
        metrics: Metrics = DEFAULT_METRICS,
        maintenanceInterval: Duration = DEFAULT_ON_DEMAND_MAINTENANCE_INTERVAL,
    ): OnDemandBucketedWindowConfig {
        return OnDemandBucketedWindowConfig(name, length, forDataClasses, taskScheduler, metrics, maintenanceInterval)
    }
}
