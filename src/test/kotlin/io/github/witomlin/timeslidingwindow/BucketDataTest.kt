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

package io.github.witomlin.timeslidingwindow

import io.github.witomlin.timeslidingwindow.test.TestWindowBucketData
import io.github.witomlin.timeslidingwindow.test.TestWindowConfig
import io.github.witomlin.timeslidingwindow.windowimpl.fixedtumbling.FixedTumblingBucketedWindowBucket
import io.kotest.assertions.throwables.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockkStatic
import java.time.Instant

class BucketDataTest :
    BehaviorSpec({
        context("init") {
            given("the class has just been instantiated") {
                `when`("public properties are accessed") {
                    then("correct defaults are returned") {
                        with(
                            BucketData<TestWindowBucketData>(
                                FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                            )
                        ) {
                            this.entries.shouldBeEmpty()
                            this.size.shouldBe(0)
                            this.firstEntryTimestamp.shouldBeNull()
                            this.lastEntryTimestamp.shouldBeNull()
                            this.isMutationAllowed.shouldBeTrue()
                        }
                    }
                }
            }
        }

        context("entries") {
            given("no data has been added") {
                `when`("accessed") {
                    then("an empty list is returned") {
                        with(
                            BucketData<TestWindowBucketData>(
                                FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                            )
                        ) {
                            entries.shouldBeEmpty()
                        }
                    }
                }
            }

            given("data has been added") {
                `when`("accessed") {
                    then("a non-empty, correctly ordered list is returned") {
                        with(
                            BucketData<TestWindowBucketData>(
                                    FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                                )
                                .apply {
                                    add(TestWindowBucketData("1"))
                                    add(TestWindowBucketData("2"))
                                }
                        ) {
                            this.entries.size.shouldBe(2)
                            this.entries.first().data.prop1.shouldBe("1")
                            this.entries.last().data.prop1.shouldBe("2")
                        }
                    }
                }
            }
        }

        context("size") {
            given("no data has been added") {
                `when`("accessed") {
                    then("the correct size is returned") {
                        with(
                            BucketData<TestWindowBucketData>(
                                FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                            )
                        ) {
                            size.shouldBe(0)
                        }
                    }
                }
            }

            given("data has been added") {
                `when`("accessed") {
                    then("the correct size is returned") {
                        with(
                            BucketData<TestWindowBucketData>(
                                    FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                                )
                                .apply {
                                    add(TestWindowBucketData("1"))
                                    add(TestWindowBucketData("2"))
                                }
                        ) {
                            size.shouldBe(2)
                        }
                    }
                }
            }
        }

        context("firstEntryTimestamp") {
            given("no data has been added") {
                `when`("accessed") {
                    then("null is returned") {
                        BucketData<TestWindowBucketData>(
                                FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                            )
                            .firstEntryTimestamp
                            .shouldBeNull()
                    }
                }
            }

            given("data has been added") {
                `when`("accessed") {
                    then("the timestamp of the first entry is returned") {
                        mockkStatic(Instant::class) {
                            every { Instant.now() } returnsMany listOf(Instant.MIN, Instant.MAX)

                            with(
                                BucketData<TestWindowBucketData>(
                                        FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.MIN) {}
                                    )
                                    .apply {
                                        add(TestWindowBucketData("1"))
                                        add(TestWindowBucketData("2"))
                                    }
                            ) {
                                this.firstEntryTimestamp.shouldBe(Instant.MIN)
                            }
                        }
                    }
                }
            }
        }

        context("lastEntryTimestamp") {
            given("no data has been added") {
                `when`("accessed") {
                    then("null is returned") {
                        BucketData<TestWindowBucketData>(
                                FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                            )
                            .lastEntryTimestamp
                            .shouldBeNull()
                    }
                }
            }

            given("data has been added") {
                `when`("accessed") {
                    then("the timestamp of the last entry is returned") {
                        mockkStatic(Instant::class) {
                            every { Instant.now() } returnsMany listOf(Instant.MIN, Instant.MAX)

                            with(
                                BucketData<TestWindowBucketData>(
                                        FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.MIN) {}
                                    )
                                    .apply {
                                        add(TestWindowBucketData("1"))
                                        add(TestWindowBucketData("2"))
                                    }
                            ) {
                                this.lastEntryTimestamp.shouldBe(Instant.MAX)
                            }
                        }
                    }
                }
            }
        }

        context("isMutationAllowed") {
            given("mutations are allowed") {
                `when`("accessed") {
                    then("true is returned") {
                        with(
                            BucketData<TestWindowBucketData>(
                                FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                            )
                        ) {
                            this.isMutationAllowed.shouldBeTrue()
                        }
                    }
                }
            }

            given("mutations are not allowed") {
                `when`("accessed") {
                    then("false is returned") {
                        with(
                            BucketData<TestWindowBucketData>(
                                FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                                    .apply { end() }
                            )
                        ) {
                            this.isMutationAllowed.shouldBeFalse()
                        }
                    }
                }
            }
        }

        context("add") {
            given("mutations are allowed") {
                `when`("invoked") {
                    then("the entry is successfully added") {
                        mockkStatic(Instant::class) {
                            every { Instant.now() } returnsMany listOf(Instant.MAX)

                            with(
                                BucketData<TestWindowBucketData>(
                                        FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.MIN) {}
                                    )
                                    .apply { add(TestWindowBucketData()) }
                            ) {
                                this.entries.size.shouldBe(1)
                                with(entries.first()) {
                                    this.timestamp.shouldBe(Instant.MAX)
                                    this.data.prop1.shouldBe(TestWindowBucketData.DEFAULT_PROP1_VALUE)
                                }
                                this.size.shouldBe(1)
                            }
                        }
                    }
                }
            }

            given("mutations are not allowed") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        with(
                            BucketData<TestWindowBucketData>(
                                FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                                    .apply { end() }
                            )
                        ) {
                            shouldThrowExactly<IllegalStateException> { this.add(TestWindowBucketData()) }
                                .message
                                .shouldBe(BucketData.EXCEPTION_MESSAGE_MUTATIONS_NO_LONGER_ALLOWED)
                        }
                    }
                }
            }
        }

        context("addNoCheck") {
            given("mutations are allowed") {
                `when`("invoked") {
                    then("the entry is successfully added") {
                        withConstantNow(Instant.MAX) {
                            with(
                                BucketData<TestWindowBucketData>(
                                        FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.MIN) {}
                                    )
                                    .apply { addNoCheck(TestWindowBucketData()) }
                            ) {
                                this.entries.size.shouldBe(1)
                                with(entries.first()) {
                                    this.timestamp.shouldBe(Instant.MAX)
                                    this.data.prop1.shouldBe(TestWindowBucketData.DEFAULT_PROP1_VALUE)
                                }
                                this.size.shouldBe(1)
                            }
                        }
                    }
                }
            }
        }

        context("addAll") {
            given("an instance with no entries") {
                `when`("invoked") {
                    then("the entries are correctly added") {
                        val now = Instant.now()

                        with(
                            BucketData<TestWindowBucketData>(
                                    FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), now) {}
                                )
                                .apply {
                                    addAll(
                                        listOf(
                                            BucketData.TimestampedData(now, TestWindowBucketData("1")),
                                            BucketData.TimestampedData(now.plusNanos(1), TestWindowBucketData("2")),
                                        )
                                    )
                                }
                        ) {
                            this.entries.size.shouldBe(2)
                            with(this.entries) {
                                this[0].timestamp.shouldBe(now)
                                this[0].data.prop1.shouldBe("1")
                                this[1].timestamp.shouldBe(now.plusNanos(1))
                                this[1].data.prop1.shouldBe("2")
                            }
                            this.size.shouldBe(2)
                        }
                    }
                }
            }

            given("an instance with entries") {
                `when`("invoked") {
                    then("the entries are correctly added") {
                        with(
                            BucketData<TestWindowBucketData>(
                                    FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                                )
                                .apply {
                                    add(TestWindowBucketData("1"))
                                    addAll(
                                        listOf(
                                            BucketData.TimestampedData(Instant.now(), TestWindowBucketData("2")),
                                            BucketData.TimestampedData(Instant.now(), TestWindowBucketData("3")),
                                        )
                                    )
                                }
                        ) {
                            this.entries.size.shouldBe(3)
                            with(this.entries) {
                                this[0].data.prop1.shouldBe("1")
                                this[1].data.prop1.shouldBe("2")
                                this[2].data.prop1.shouldBe("3")
                            }
                            this.size.shouldBe(3)
                        }
                    }
                }
            }
        }

        context("equals") {
            given("both data objects have referential equality") {
                `when`("invoked") {
                    then("true is returned") {
                        val data =
                            BucketData<TestWindowBucketData>(
                                FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                            )
                        (data == data).shouldBeTrue()
                    }
                }
            }
            given("'other' is not a data object") {
                `when`("invoked") {
                    then("false is returned") {
                        (BucketData<TestWindowBucketData>(
                                    FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                                )
                                .equals("test"))
                            .shouldBeFalse()
                    }
                }
            }
        }

        context("hashCode") {
            given("an instance") {
                `when`("invoked") {
                    then("the non-zero hash code is returned") {
                        with(
                            BucketData<TestWindowBucketData>(
                                FixedTumblingBucketedWindowBucket(TestWindowConfig.fixed(), Instant.now()) {}
                            )
                        ) {
                            this.hashCode().shouldNotBe(0)
                        }
                    }
                }
            }
        }
    })
