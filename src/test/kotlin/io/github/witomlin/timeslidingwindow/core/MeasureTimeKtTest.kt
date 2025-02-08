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

package io.github.witomlin.timeslidingwindow.core

import io.github.witomlin.timeslidingwindow.test.TestConfig
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class MeasureTimeKtTest :
    BehaviorSpec({
        context("measureTimeMsDouble") {
            given("a block that takes TestConfig.TIME_TESTS_PERIOD_MS_LONG to execute") {
                `when`("invoked") {
                    then("the measured duration is around TestConfig.TIME_TESTS_PERIOD_MS_LONG") {
                        val duration = measureTimeMsDouble { Thread.sleep(TestConfig.TIME_TESTS_PERIOD_MS_LONG) }
                        duration.shouldBe(
                            TestConfig.TIME_TESTS_PERIOD_MS_DOUBLE plusOrMinus TestConfig.TIME_TESTS_GRACE_MS_DOUBLE
                        )
                    }
                }
            }
        }
    })
