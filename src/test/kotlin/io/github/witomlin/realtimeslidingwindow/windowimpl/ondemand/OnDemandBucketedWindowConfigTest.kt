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

package io.github.witomlin.realtimeslidingwindow.windowimpl.ondemand

import io.github.witomlin.realtimeslidingwindow.test.TestWindowConfig
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class OnDemandBucketedWindowConfigTest :
    BehaviorSpec({
        context("init") {
            given("the class is being initialized") {
                `when`("'length' is < min") {
                    then("an exception is thrown") {
                        shouldThrowExactly<IllegalArgumentException> {
                                TestWindowConfig.onDemand(
                                    length =
                                        Duration.ofMillis(OnDemandBucketedWindowConfig.CONFIG_MIN_LENGTH_MS)
                                            .minusMillis(1)
                                )
                            }
                            .message
                            .shouldBe(OnDemandBucketedWindowConfig.EXCEPTION_MESSAGE_LENGTH_INSUFFICIENT)
                    }
                }
                `when`("'maintenanceInterval' is < min") {
                    then("an exception is thrown") {
                        shouldThrowExactly<IllegalArgumentException> {
                                TestWindowConfig.onDemand(
                                    maintenanceInterval =
                                        Duration.ofMillis(OnDemandBucketedWindowConfig.CONFIG_MIN_MAINTENANCE_MS)
                                            .minusMillis(1)
                                )
                            }
                            .message
                            .shouldBe(OnDemandBucketedWindowConfig.EXCEPTION_MESSAGE_MAINTENANCE_TOO_SHORT)
                    }
                }
            }
        }
    })
