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

import io.github.witomlin.realtimeslidingwindow.schedule.TaskScheduler
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

class TestRunOnlyNTimesTaskScheduler(
    private val forTimes: Int = 0,
    private val forWaitUntilPerTime: List<CountDownLatch?> = List(forTimes) { null },
    private val forRunAsync: Boolean = true,
    private val everyTimes: Int = 0,
    private val everyWaitUntilPerTime: List<CountDownLatch?> = List(everyTimes) { null },
    private val everyRunAsync: Boolean = true,
    private val executeTimes: Int = 0,
    private val executeWaitUntilPerTime: List<CountDownLatch?> = List(executeTimes) { null },
    private val executeRunAsync: Boolean = true,
) : TaskScheduler {
    init {
        require(forWaitUntilPerTime.size == forTimes) { "'forWaitUntilPerTime' must have the same size as 'forTimes'" }

        require(everyWaitUntilPerTime.size == everyTimes) {
            "'everyWaitUntilPerTime' must have the same size as 'everyTimes'"
        }

        require(executeWaitUntilPerTime.size == executeTimes) {
            "'executeWaitUntilPerTime' must have the same size as 'executeTimes'"
        }
    }

    @Volatile private var forInvocationCount = 0
    @Volatile private var everyInvocationCount = 0
    @Volatile private var executeInvocationCount = 0

    private val _forExceptions = ConcurrentHashMap<Int, Exception>()
    private val _everyExceptions = ConcurrentHashMap<Int, Exception>()
    private val _executeExceptions = ConcurrentHashMap<Int, Exception>()

    val forExceptions: Map<Int, Exception>
        get() = Collections.unmodifiableMap(_forExceptions)

    val everyExceptions: Map<Int, Exception>
        get() = Collections.unmodifiableMap(_everyExceptions)

    val executeExceptions: Map<Int, Exception>
        get() = Collections.unmodifiableMap(_executeExceptions)

    val forAllTimesRun = CountDownLatch(forTimes)
    val everyAllTimesRun = CountDownLatch(everyTimes)
    val executeAllTimesRun = CountDownLatch(executeTimes)

    @Synchronized
    override fun scheduleFor(time: Instant, task: () -> Unit) {
        if (++forInvocationCount > forTimes) return
        run(
            task,
            forRunAsync,
            forInvocationCount,
            forWaitUntilPerTime[forInvocationCount - 1],
            _forExceptions,
            forAllTimesRun,
        )
    }

    override fun scheduleEvery(every: Duration, task: () -> Unit) {
        if (++everyInvocationCount > everyTimes) return
        run(
            task,
            everyRunAsync,
            everyInvocationCount,
            everyWaitUntilPerTime[everyInvocationCount - 1],
            _everyExceptions,
            everyAllTimesRun,
        )
    }

    override fun execute(task: () -> Unit) {
        if (++executeInvocationCount > executeTimes) return
        run(
            task,
            executeRunAsync,
            executeInvocationCount,
            executeWaitUntilPerTime[executeInvocationCount - 1],
            _executeExceptions,
            executeAllTimesRun,
        )
    }

    private fun run(
        task: () -> Unit,
        async: Boolean,
        invocationCount: Int,
        waitUntil: CountDownLatch?,
        exceptions: ConcurrentHashMap<Int, Exception>,
        allTimesRun: CountDownLatch,
    ) {
        val run: () -> Unit = {
            waitUntil?.awaitDefault()?.also {
                if (!it) {
                    error("Timed out")
                }
            }

            try {
                task()
            } catch (e: Exception) {
                exceptions[invocationCount] = e
            } finally {
                allTimesRun.countDown()
            }
        }

        if (async) Thread(run).start() else run()
    }
}
