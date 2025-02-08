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

package io.github.witomlin.timeslidingwindow.schedule

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class DefaultTaskScheduler(threads: Int = 3) : TaskScheduler {
    val executor = ScheduledThreadPoolExecutor(threads)

    override fun scheduleFor(time: Instant, task: () -> Unit) {
        executor.schedule(task, time.toEpochMilli() - Instant.now().toEpochMilli(), TimeUnit.MILLISECONDS)
    }

    override fun scheduleEvery(every: Duration, task: () -> Unit) {
        val everyMillis = every.toMillis()
        executor.scheduleAtFixedRate(task, everyMillis, everyMillis, TimeUnit.MILLISECONDS)
    }

    override fun execute(task: () -> Unit) {
        executor.execute(task)
    }
}
