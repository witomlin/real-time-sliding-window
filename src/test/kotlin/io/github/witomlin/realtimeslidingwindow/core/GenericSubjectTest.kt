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

import io.github.witomlin.realtimeslidingwindow.test.TestRunOnlyNTimesTaskScheduler
import io.github.witomlin.realtimeslidingwindow.test.TestSubject
import io.github.witomlin.realtimeslidingwindow.test.awaitDefault
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class GenericSubjectTest :
    BehaviorSpec({
        context("addObserver") {
            given("a subject with no observers") {
                `when`("an observer is added") {
                    then("the observer is correctly added") {
                        var observed = false

                        with(
                            TestSubject(TestRunOnlyNTimesTaskScheduler(), false).apply {
                                addObserver { _, _ -> observed = true }
                            }
                        ) {
                            this.notifyEvent()
                            observed.shouldBeTrue()
                        }
                    }
                }
            }
        }

        context("removeObserver") {
            given("a subject with 1 observer") {
                `when`("an observer is removed") {
                    then("the observer is correctly removed") {
                        var observed = false
                        val observer = { _: TestSubject.Event, _: String -> observed = true }

                        with(TestSubject(TestRunOnlyNTimesTaskScheduler(), false).apply { addObserver(observer) }) {
                            this.removeObserver(observer)
                            this.notifyEvent()
                            observed.shouldBeFalse()
                        }
                    }
                }
            }
        }

        context("notifyObservers") {
            given("a subject with 2 observers") {
                `when`("observers are notified synchronously") {
                    then("both observers are notified") {
                        var observed1 = false
                        var observed2 = false
                        val observer1 = { _: TestSubject.Event, _: String -> observed1 = true }
                        val observer2 = { _: TestSubject.Event, _: String -> observed2 = true }

                        with(
                            TestSubject(TestRunOnlyNTimesTaskScheduler(), false).apply {
                                addObserver(observer1)
                                addObserver(observer2)
                            }
                        ) {
                            this.notifyEvent()
                            observed1.shouldBeTrue()
                            observed2.shouldBeTrue()
                        }
                    }
                }
                `when`("observers are notified asynchronously") {
                    then("both observers are notified") {
                        val scheduler = TestRunOnlyNTimesTaskScheduler(executeTimes = 2)
                        var observed1 = false
                        var observed2 = false
                        val observer1 = { _: TestSubject.Event, _: String -> observed1 = true }
                        val observer2 = { _: TestSubject.Event, _: String -> observed2 = true }

                        with(
                            TestSubject(scheduler, true).apply {
                                addObserver(observer1)
                                addObserver(observer2)
                            }
                        ) {
                            this.notifyEvent()
                            scheduler.executeAllTimesRun.awaitDefault().shouldBeTrue()
                            observed1.shouldBeTrue()
                            observed2.shouldBeTrue()
                        }
                    }
                }
            }
        }
    })
