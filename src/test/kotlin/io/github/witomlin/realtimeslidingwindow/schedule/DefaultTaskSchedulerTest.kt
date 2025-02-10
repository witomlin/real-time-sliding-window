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

package io.github.witomlin.realtimeslidingwindow.schedule

import io.github.witomlin.realtimeslidingwindow.test.TestConfig
import io.github.witomlin.realtimeslidingwindow.test.awaitDefault
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CountDownLatch

class DefaultTaskSchedulerTest :
    BehaviorSpec({
        context("scheduleFor") {
            given("an instance") {
                `when`("invoked") {
                    then("the task should be scheduled for the correct time") {
                        val taskRun = CountDownLatch(1)
                        var timeInvoked: Instant? = null
                        val task = {
                            timeInvoked = Instant.now()
                            taskRun.countDown()
                        }

                        val start = Instant.now()
                        DefaultTaskScheduler().scheduleFor(start.plusMillis(TestConfig.TIME_TESTS_PERIOD_MS_LONG), task)
                        taskRun.awaitDefault().shouldBeTrue()
                        Duration.between(start, timeInvoked)
                            .toMillis()
                            .toDouble()
                            .shouldBe(
                                TestConfig.TIME_TESTS_PERIOD_MS_DOUBLE plusOrMinus TestConfig.TIME_TESTS_GRACE_MS_DOUBLE
                            )
                    }
                }
            }
        }

        context("scheduleEvery") {
            given("an instance") {
                `when`("invoked") {
                    then("the task should be scheduled at the correct intervals") {
                        val tasksRun = CountDownLatch(3)
                        val timesInvoked = Collections.synchronizedList(mutableListOf<Instant>())
                        val task = {
                            timesInvoked.add(Instant.now())
                            tasksRun.countDown()
                        }

                        val start = Instant.now()
                        DefaultTaskScheduler()
                            .scheduleEvery(Duration.ofMillis(TestConfig.TIME_TESTS_PERIOD_MS_LONG), task)
                        tasksRun.awaitDefault().shouldBeTrue()

                        Duration.between(start, timesInvoked[0])
                            .toMillis()
                            .toDouble()
                            .shouldBe(
                                TestConfig.TIME_TESTS_PERIOD_MS_DOUBLE plusOrMinus TestConfig.TIME_TESTS_GRACE_MS_DOUBLE
                            )
                        for (i in 1 until timesInvoked.size) {
                            Duration.between(timesInvoked[i - 1], timesInvoked[i])
                                .toMillis()
                                .toDouble()
                                .shouldBe(
                                    TestConfig.TIME_TESTS_PERIOD_MS_DOUBLE plusOrMinus
                                        TestConfig.TIME_TESTS_GRACE_MS_DOUBLE
                                )
                        }
                    }
                }
            }
        }

        context("execute") {
            given("an instance") {
                `when`("invoked") {
                    then("the task should be executed immediately") {
                        val taskRun = CountDownLatch(1)
                        var timeInvoked: Instant? = null
                        val task = {
                            timeInvoked = Instant.now()
                            taskRun.countDown()
                        }

                        val start = Instant.now()
                        DefaultTaskScheduler().execute(task)
                        taskRun.awaitDefault().shouldBeTrue()
                        Duration.between(start, timeInvoked)
                            .toMillis()
                            .shouldBeLessThanOrEqual(TestConfig.TIME_TESTS_GRACE_MS_LONG)
                    }
                }
            }
        }
    })
