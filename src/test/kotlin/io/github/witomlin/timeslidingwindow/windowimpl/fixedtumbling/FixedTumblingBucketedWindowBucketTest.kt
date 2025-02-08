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

package io.github.witomlin.timeslidingwindow.windowimpl.fixedtumbling

import io.github.witomlin.timeslidingwindow.Bucket
import io.github.witomlin.timeslidingwindow.BucketType
import io.github.witomlin.timeslidingwindow.test.TestRunOnlyNTimesTaskScheduler
import io.github.witomlin.timeslidingwindow.test.TestWindowBucketData
import io.github.witomlin.timeslidingwindow.test.TestWindowConfig
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class FixedTumblingBucketedWindowBucketTest :
    BehaviorSpec({
        context("init") {
            given("the class has just been instantiated") {
                `when`("public properties are accessed") {
                    then("correct defaults are returned") {
                        val now = Instant.now()

                        with(FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), now) {}) {
                            this.start.shouldBe(now)
                            this.status.shouldBe(FixedTumblingBucketedWindowBucket.Status.CURRENT)
                            this.scheduledEnd.shouldBe(now.plus(TestWindowConfig.DEFAULT_BUCKET_LENGTH).minusNanos(1))
                            this.endInfo.shouldBeNull()
                        }
                    }
                }
                `when`("the bucket ends") {
                    then("onEnd is invoked") {
                        val config =
                            TestWindowConfig.fixed(
                                taskScheduler = TestRunOnlyNTimesTaskScheduler(forTimes = 1, forRunAsync = false)
                            )
                        var onEndInvoked = false

                        FixedTumblingBucketedWindowBucket(config, Instant.now()) { onEndInvoked = true }
                        onEndInvoked.shouldBeTrue()
                    }
                }
            }
        }

        context("status") {
            given("the bucket has not ended") {
                `when`("accessed") {
                    then("CURRENT is returned") {
                        with(FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}) {
                            this.status.shouldBe(FixedTumblingBucketedWindowBucket.Status.CURRENT)
                        }
                    }
                }
            }

            given("the bucket has ended") {
                `when`("accessed") {
                    then("NON_CURRENT is returned") {
                        with(
                            FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                                .apply { end() }
                        ) {
                            this.status.shouldBe(FixedTumblingBucketedWindowBucket.Status.NON_CURRENT)
                        }
                    }
                }
            }
        }

        context("endInfo") {
            given("the bucket has not ended") {
                `when`("accessed") {
                    then("null is returned") {
                        with(FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}) {
                            this.endInfo.shouldBeNull()
                        }
                    }
                }
            }

            given("the bucket has ended") {
                `when`("accessed") {
                    then("an object is returned") {
                        with(
                            FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                                .apply { end() }
                        ) {
                            this.endInfo.shouldNotBeNull()
                        }
                    }
                }
            }
        }

        context("type") {
            given("an instance") {
                `when`("accessed") {
                    then("TUMBLING is returned") {
                        with(FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}) {
                            this.type.shouldBe(BucketType.TUMBLING)
                        }
                    }
                }
            }
        }

        context("isMutationAllowed") {
            given("the bucket has not ended") {
                `when`("accessed") {
                    then("true is returned") {
                        with(FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}) {
                            this.isMutationAllowed.shouldBeTrue()
                        }
                    }
                }
            }

            given("the bucket has ended") {
                `when`("accessed") {
                    then("false is returned") {
                        with(
                            FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                                .apply { end() }
                        ) {
                            this.isMutationAllowed.shouldBeFalse()
                        }
                    }
                }
            }
        }

        context("dataForClass") {
            given("an invalid data class is provided") {
                `when`("invoking") {
                    then("an exception is thrown") {
                        with(
                            FixedTumblingBucketedWindowBucket(
                                TestWindowConfig.fixed(forDataClasses = listOf(TestWindowBucketData::class)),
                                Instant.now(),
                            ) {}
                        ) {
                            shouldThrowExactly<IllegalArgumentException> { this.dataForClass(String::class) }
                                .message
                                .shouldBe(Bucket.EXCEPTION_MESSAGE_CLASS_NOT_STORED)
                        }
                    }
                }
            }

            given("a valid data class is provided") {
                `when`("invoking") {
                    then("an exception is not thrown") {
                        with(FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}) {
                            shouldNotThrowAny { this.dataForClass(TestWindowBucketData::class) }
                        }
                    }
                }
            }
        }

        context("end") {
            given("the bucket is not current") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        with(
                            FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                                .apply { end() }
                        ) {
                            shouldThrowExactly<IllegalStateException> { this.end() }
                                .message
                                .shouldBe(FixedTumblingBucketedWindowBucket.EXCEPTION_MESSAGE_ALREADY_ENDED)
                        }
                    }
                }
            }

            given("the bucket is current") {
                `when`("invoked") {
                    then("the bucket is correctly ended") {
                        val durationMillis = 5

                        withConstantNow(Instant.MIN.plus(Duration.ofMillis(durationMillis.toLong()))) {
                            with(
                                FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.MIN) {}
                                    .apply { end() }
                            ) {
                                this.endInfo.shouldBe(
                                    FixedTumblingBucketedWindowBucket.EndInfo(
                                        Instant.MIN.plus(Duration.ofMillis(durationMillis.toLong())),
                                        durationMillis,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        context("equals") {
            given("both buckets have referential equality") {
                `when`("invoked") {
                    then("true is returned") {
                        val bucket = FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                        (bucket == bucket).shouldBeTrue()
                    }
                }
            }
            given("'other' is not a bucket") {
                `when`("invoked") {
                    then("false is returned") {
                        (FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}.equals("test"))
                            .shouldBeFalse()
                    }
                }
            }
            given("'other' does not have referential equality but is a bucket") {
                `when`("both buckets have the same start and scheduled end") {
                    then("true is returned") {
                        val bucket1 = FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.MIN) {}
                        val bucket2 = FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.MIN) {}
                        (bucket1 == bucket2).shouldBeTrue()
                    }
                }
                `when`("the buckets have a different start") {
                    then("false is returned") {
                        val bucket1 = FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.MIN) {}
                        val bucket2 =
                            FixedTumblingBucketedWindowBucket(
                                TestWindowConfig.fixed(),
                                Instant.MIN.plus(Duration.ofMillis(1)),
                            ) {}
                        (bucket1 == bucket2).shouldBeFalse()
                    }
                }
            }
        }

        context("hashCode") {
            given("an instance") {
                `when`("invoked") {
                    then("the correct hash code is returned") {
                        with(FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}) {
                            this.hashCode().shouldBe((31 * start.hashCode()) + scheduledEnd.hashCode())
                        }
                    }
                }
            }
        }

        context("toString") {
            given("the bucket is current") {
                `when`("invoked") {
                    then("the appropriate format is returned") {
                        with(
                            FixedTumblingBucketedWindowBucket(
                                    TestWindowConfig.fixed(
                                        forDataClasses = listOf(TestWindowBucketData::class, String::class)
                                    ),
                                    Instant.MIN,
                                ) {}
                                .apply { dataForClass(String::class).add("test") }
                        ) {
                            this.toString()
                                .shouldBe(
                                    "FixedTumblingBucketedWindowBucket(" +
                                        "status=${this.status}, " +
                                        "type=${BucketType.TUMBLING}, " +
                                        "start=${Instant.MIN}, " +
                                        "scheduledEnd=${Instant.MIN.plus(TestWindowConfig.DEFAULT_BUCKET_LENGTH).minusNanos(1)}, " +
                                        "isMutationAllowed=${this.isMutationAllowed}, " +
                                        "entriesPerDataClass=[${TestWindowBucketData::class.simpleName}=0, String=1]" +
                                        ")"
                                )
                        }
                    }
                }
            }

            given("the bucket is not current") {
                `when`("invoked") {
                    then("the appropriate format is returned") {
                        val durationMillis = 5

                        withConstantNow(Instant.MIN.plus(Duration.ofMillis(durationMillis.toLong()))) {
                            with(
                                FixedTumblingBucketedWindowBucket(
                                        TestWindowConfig.fixed(
                                            forDataClasses = listOf(TestWindowBucketData::class, String::class)
                                        ),
                                        Instant.MIN,
                                    ) {}
                                    .apply {
                                        dataForClass(String::class).add("test")
                                        end()
                                    }
                            ) {
                                this.toString()
                                    .shouldBe(
                                        "FixedTumblingBucketedWindowBucket(" +
                                            "status=${this.status}, " +
                                            "type=${BucketType.TUMBLING}, " +
                                            "start=${Instant.MIN}, " +
                                            "endInfo=EndInfo(actualEnd=${Instant.MIN.plus(Duration.ofMillis(durationMillis.toLong()))}, durationMillis=$durationMillis), " +
                                            "isMutationAllowed=${this.isMutationAllowed}, " +
                                            "entriesPerDataClass=[${TestWindowBucketData::class.simpleName}=0, String=1]" +
                                            ")"
                                    )
                            }
                        }
                    }
                }
            }
        }
    })
