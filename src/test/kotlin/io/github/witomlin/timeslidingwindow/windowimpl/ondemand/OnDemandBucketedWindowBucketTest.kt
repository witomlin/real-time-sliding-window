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

import io.github.witomlin.timeslidingwindow.Bucket
import io.github.witomlin.timeslidingwindow.BucketData
import io.github.witomlin.timeslidingwindow.BucketType
import io.github.witomlin.timeslidingwindow.test.TestWindowBucketData
import io.github.witomlin.timeslidingwindow.test.TestWindowConfig
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.time.Instant

class OnDemandBucketedWindowBucketTest :
    BehaviorSpec({
        context("init") {
            given("the class is being instantiated") {
                `when`("a data class is supplied that's not in the window config") {
                    then("an exception is thrown") {
                        val now = Instant.now()

                        shouldThrowExactly<IllegalArgumentException> {
                                OnDemandBucketedWindowBucket(
                                    TestWindowConfig.onDemand(forDataClasses = listOf(TestWindowBucketData::class)),
                                    BucketType.TUMBLING,
                                    now,
                                    now.plusNanos(1),
                                    mapOf(
                                        TestWindowBucketData::class to listOf(),
                                        String::class to listOf(BucketData.TimestampedData(now, "test")),
                                    ),
                                )
                            }
                            .message
                            .shouldBe(Bucket.EXCEPTION_MESSAGE_CLASS_NOT_STORED)
                    }
                }
            }

            given("the class has just been instantiated") {
                `when`("data is accessed that was specified upon instantiation") {
                    then("the correct data is returned") {
                        val now = Instant.now()
                        val data1 = BucketData.TimestampedData(now, TestWindowBucketData("1"))
                        val data2 = BucketData.TimestampedData(now.plusNanos(1), TestWindowBucketData("2"))
                        val data3 = BucketData.TimestampedData(now.plusNanos(2), "test")

                        val bucket =
                            OnDemandBucketedWindowBucket(
                                TestWindowConfig.onDemand(
                                    forDataClasses = listOf(TestWindowBucketData::class, String::class)
                                ),
                                BucketType.TUMBLING,
                                now,
                                now.plusNanos(1),
                                mapOf(
                                    TestWindowBucketData::class to listOf(data1, data2),
                                    String::class to listOf(data3),
                                ),
                            )

                        with(bucket.dataForClass(TestWindowBucketData::class).entries) {
                            this.size.shouldBe(2)
                            this[0].shouldBe(data1)
                            this[1].shouldBe(data2)
                        }

                        with(bucket.dataForClass(String::class).entries) {
                            this.size.shouldBe(1)
                            this[0].shouldBe(data3)
                        }
                    }
                }
            }
        }

        context("type") {
            given("an instance configured as TUMBLING") {
                `when`("accessed") {
                    then("TUMBLING is returned") {
                        val now = Instant.now()

                        with(
                            OnDemandBucketedWindowBucket(
                                TestWindowConfig.onDemand(),
                                BucketType.TUMBLING,
                                now,
                                now.plusNanos(1),
                                mapOf(),
                            )
                        ) {
                            this.type.shouldBe(BucketType.TUMBLING)
                        }
                    }
                }
            }
        }

        context("isMutationAllowed") {
            given("an instance") {
                `when`("accessed") {
                    then("false is returned") {
                        val now = Instant.now()

                        with(
                            OnDemandBucketedWindowBucket(
                                TestWindowConfig.onDemand(),
                                BucketType.TUMBLING,
                                now,
                                now.plusNanos(1),
                                mapOf(),
                            )
                        ) {
                            this.isMutationAllowed.shouldBeFalse()
                        }
                    }
                }
            }
        }

        context("equals") {
            given("both buckets have referential equality") {
                `when`("invoked") {
                    then("true is returned") {
                        val now = Instant.now()

                        val bucket =
                            OnDemandBucketedWindowBucket(
                                TestWindowConfig.onDemand(),
                                BucketType.TUMBLING,
                                now,
                                now.plusNanos(1),
                                mapOf(),
                            )
                        (bucket == bucket).shouldBeTrue()
                    }
                }
            }
            given("'other' is not a bucket") {
                `when`("invoked") {
                    then("false is returned") {
                        val now = Instant.now()

                        (OnDemandBucketedWindowBucket(
                                    TestWindowConfig.onDemand(),
                                    BucketType.TUMBLING,
                                    now,
                                    now.plusNanos(1),
                                    mapOf(),
                                )
                                .equals("test"))
                            .shouldBeFalse()
                    }
                }
            }
            given("'other' does not have referential equality but is a bucket") {
                `when`("both buckets have the same start and scheduled end") {
                    then("true is returned") {
                        val now = Instant.now()

                        val bucket1 =
                            OnDemandBucketedWindowBucket(
                                TestWindowConfig.onDemand(),
                                BucketType.TUMBLING,
                                now,
                                now.plusNanos(1),
                                mapOf(),
                            )
                        val bucket2 =
                            OnDemandBucketedWindowBucket(
                                TestWindowConfig.onDemand(),
                                BucketType.TUMBLING,
                                now,
                                now.plusNanos(1),
                                mapOf(),
                            )
                        (bucket1 == bucket2).shouldBeTrue()
                    }
                }
                `when`("the buckets have a different start") {
                    then("false is returned") {
                        val now = Instant.now()

                        val bucket1 =
                            OnDemandBucketedWindowBucket(
                                TestWindowConfig.onDemand(),
                                BucketType.TUMBLING,
                                now,
                                now.plusNanos(1),
                                mapOf(),
                            )
                        val bucket2 =
                            OnDemandBucketedWindowBucket(
                                TestWindowConfig.onDemand(),
                                BucketType.TUMBLING,
                                now.plusNanos(2),
                                now.plusNanos(3),
                                mapOf(),
                            )
                        (bucket1 == bucket2).shouldBeFalse()
                    }
                }
            }
        }

        context("hashCode") {
            given("an instance") {
                `when`("invoked") {
                    then("the correct hash code is returned") {
                        val now = Instant.now()

                        with(
                            OnDemandBucketedWindowBucket(
                                TestWindowConfig.onDemand(),
                                BucketType.TUMBLING,
                                now,
                                now.plusNanos(1),
                                mapOf(),
                            )
                        ) {
                            this.hashCode().shouldBe((31 * start.hashCode()) + end.hashCode())
                        }
                    }
                }
            }
        }

        context("toString") {
            given("an instance") {
                `when`("invoked") {
                    then("the appropriate format is returned") {
                        val now = Instant.now()

                        with(
                            OnDemandBucketedWindowBucket(
                                TestWindowConfig.onDemand(
                                    forDataClasses = listOf(TestWindowBucketData::class, String::class, Int::class)
                                ),
                                BucketType.TUMBLING,
                                now,
                                now.plus(TestWindowConfig.DEFAULT_BUCKET_LENGTH),
                                // Deliberately don't explicitly supply Int data - should still be in output
                                mapOf(
                                    TestWindowBucketData::class to listOf(),
                                    String::class to listOf(BucketData.TimestampedData(now, "test")),
                                ),
                            )
                        ) {
                            this.toString()
                                .shouldBe(
                                    "OnDemandBucketedWindowBucket(" +
                                        "type=${BucketType.TUMBLING}, " +
                                        "start=$now, " +
                                        "end=${now.plus(TestWindowConfig.DEFAULT_BUCKET_LENGTH)}, " +
                                        "entriesPerDataClass=[${TestWindowBucketData::class.simpleName}=0, String=1, Int=0]" +
                                        ")"
                                )
                        }
                    }
                }
            }
        }
    })
