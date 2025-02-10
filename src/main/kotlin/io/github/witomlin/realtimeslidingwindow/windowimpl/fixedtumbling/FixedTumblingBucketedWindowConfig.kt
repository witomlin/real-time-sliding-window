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

package io.github.witomlin.realtimeslidingwindow.windowimpl.fixedtumbling

import io.github.witomlin.realtimeslidingwindow.BucketedWindowConfig
import io.github.witomlin.realtimeslidingwindow.observability.Metrics
import io.github.witomlin.realtimeslidingwindow.observability.NoOpMetrics
import io.github.witomlin.realtimeslidingwindow.schedule.DefaultTaskScheduler
import io.github.witomlin.realtimeslidingwindow.schedule.TaskScheduler
import java.time.Duration
import kotlin.reflect.KClass

class FixedTumblingBucketedWindowConfig(
    name: String,
    length: Duration,
    val bucketLength: Duration,
    forDataClasses: List<KClass<*>>,
    taskScheduler: TaskScheduler = DefaultTaskScheduler(),
    metrics: Metrics = NoOpMetrics(),
) : BucketedWindowConfig(name, length, forDataClasses, taskScheduler, metrics) {
    internal companion object {
        const val CONFIG_MIN_LENGTH_MS = 250L
        const val CONFIG_MIN_BUCKET_LENGTH_MS = 250L

        const val EXCEPTION_MESSAGE_LENGTH_INSUFFICIENT = "'length' must be >= ${CONFIG_MIN_LENGTH_MS}ms"
        const val EXCEPTION_MESSAGE_BUCKET_INSUFFICIENT = "'bucketLength' must be >= ${CONFIG_MIN_BUCKET_LENGTH_MS}ms"
        const val EXCEPTION_MESSAGE_LENGTH_LESS_THAN_BUCKET = "'length' must be >= 'bucketLength'"
        const val EXCEPTION_MESSAGE_LENGTH_MULTIPLE = "'length' must be an exact multiple of 'bucketLength'"
    }

    init {
        require(length >= Duration.ofMillis(CONFIG_MIN_LENGTH_MS)) { EXCEPTION_MESSAGE_LENGTH_INSUFFICIENT }
        require(bucketLength >= Duration.ofMillis(CONFIG_MIN_BUCKET_LENGTH_MS)) {
            EXCEPTION_MESSAGE_BUCKET_INSUFFICIENT
        }
        require(length >= bucketLength) { EXCEPTION_MESSAGE_LENGTH_LESS_THAN_BUCKET }
        require(length.toMillis() % bucketLength.toMillis() == 0L) { EXCEPTION_MESSAGE_LENGTH_MULTIPLE }
    }

    val nonCurrentBucketCount = length.dividedBy(bucketLength).toInt()
}
