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
import io.github.witomlin.realtimeslidingwindow.test.TestWindowConfig
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class FixedTumblingBucketedWindowConfigTest :
    BehaviorSpec({
        context("init") {
            given("the class is being initialized") {
                `when`("'name' is empty") {
                    then("an exception is thrown") {
                        shouldThrowExactly<IllegalArgumentException> { TestWindowConfig.fixed(name = "") }
                            .message
                            .shouldBe(BucketedWindowConfig.EXCEPTION_MESSAGE_NAME_EMPTY)
                    }
                }
                `when`("'forDataClasses' is empty") {
                    then("an exception is thrown") {
                        shouldThrowExactly<IllegalArgumentException> {
                                TestWindowConfig.fixed(forDataClasses = emptyList())
                            }
                            .message
                            .shouldBe(BucketedWindowConfig.EXCEPTION_MESSAGE_FOR_DATA_CLASSES_EMPTY)
                    }
                }
                `when`("'length' is < min") {
                    then("an exception is thrown") {
                        shouldThrowExactly<IllegalArgumentException> {
                                TestWindowConfig.fixed(
                                    length =
                                        Duration.ofMillis(FixedTumblingBucketedWindowConfig.CONFIG_MIN_LENGTH_MS)
                                            .minusMillis(1)
                                )
                            }
                            .message
                            .shouldBe(FixedTumblingBucketedWindowConfig.EXCEPTION_MESSAGE_LENGTH_INSUFFICIENT)
                    }
                }
                `when`("'bucket' is < min") {
                    then("an exception is thrown") {
                        shouldThrowExactly<IllegalArgumentException> {
                                TestWindowConfig.fixed(
                                    bucketLength =
                                        Duration.ofMillis(FixedTumblingBucketedWindowConfig.CONFIG_MIN_BUCKET_LENGTH_MS)
                                            .minusMillis(1)
                                )
                            }
                            .message
                            .shouldBe(FixedTumblingBucketedWindowConfig.EXCEPTION_MESSAGE_BUCKET_INSUFFICIENT)
                    }
                }
                `when`("'length' is < 'bucket'") {
                    then("an exception is thrown") {
                        shouldThrowExactly<IllegalArgumentException> {
                                TestWindowConfig.fixed(
                                    length = Duration.ofMillis(FixedTumblingBucketedWindowConfig.CONFIG_MIN_LENGTH_MS),
                                    bucketLength =
                                        Duration.ofMillis(FixedTumblingBucketedWindowConfig.CONFIG_MIN_LENGTH_MS)
                                            .plusMillis(1),
                                )
                            }
                            .message
                            .shouldBe(FixedTumblingBucketedWindowConfig.EXCEPTION_MESSAGE_LENGTH_LESS_THAN_BUCKET)
                    }
                }
                `when`("'length' is not an exact multiple of 'bucket'") {
                    then("an exception is thrown") {
                        shouldThrowExactly<IllegalArgumentException> {
                                TestWindowConfig.fixed(
                                    length =
                                        Duration.ofMillis(
                                            FixedTumblingBucketedWindowConfig.CONFIG_MIN_BUCKET_LENGTH_MS * 2
                                        ),
                                    bucketLength =
                                        Duration.ofMillis(FixedTumblingBucketedWindowConfig.CONFIG_MIN_BUCKET_LENGTH_MS)
                                            .plusMillis(1),
                                )
                            }
                            .message
                            .shouldBe(FixedTumblingBucketedWindowConfig.EXCEPTION_MESSAGE_LENGTH_MULTIPLE)
                    }
                }
            }
        }

        context("nonCurrentBucketCount") {
            given("an instance") {
                `when`("accessed") {
                    then("the correct value is returned") {
                        with(
                            TestWindowConfig.fixed(
                                length = Duration.ofSeconds(30),
                                bucketLength = Duration.ofSeconds(5),
                            )
                        ) {
                            this.nonCurrentBucketCount.shouldBe(6)
                        }
                    }
                }
            }
        }
    })
