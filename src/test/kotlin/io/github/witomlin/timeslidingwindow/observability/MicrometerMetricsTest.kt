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

package io.github.witomlin.timeslidingwindow.observability

import io.github.witomlin.timeslidingwindow.WindowMetricsConfig
import io.github.witomlin.timeslidingwindow.test.TestWindowBucketData
import io.github.witomlin.timeslidingwindow.test.TestWindowConfig
import io.github.witomlin.timeslidingwindow.windowimpl.fixedtumbling.FixedTumblingBucketedWindow
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class MicrometerMetricsTest :
    BehaviorSpec({
        context("initialize") {
            given("metrics have already been initialized") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        val config = TestWindowConfig.fixed()

                        with(
                            MicrometerMetrics(SimpleMeterRegistry()).apply {
                                initialize(config, FixedTumblingBucketedWindow.metricsConfig(config))
                            }
                        ) {
                            shouldThrowExactly<IllegalStateException> {
                                    initialize(config, FixedTumblingBucketedWindow.metricsConfig(config))
                                }
                                .message
                                .shouldBe(Metrics.EXCEPTION_MESSAGE_ALREADY_INITIALIZED)
                        }
                    }
                }
            }
        }

        context("updateMaintenanceDuration") {
            given("metrics have not been initialized") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        with(MicrometerMetrics(SimpleMeterRegistry())) {
                            shouldThrowExactly<IllegalStateException> { this.updateMaintenanceDuration(0.0) }
                                .message
                                .shouldBe(Metrics.EXCEPTION_MESSAGE_NOT_INITIALIZED)
                        }
                    }
                }
            }

            given("metrics have been initialized") {
                `when`("invoked") {
                    then("the meter registry correctly reflects the duration") {
                        val config = TestWindowConfig.fixed()
                        val meterRegistry = SimpleMeterRegistry()

                        with(
                            MicrometerMetrics(meterRegistry).apply {
                                initialize(config, FixedTumblingBucketedWindow.metricsConfig(config))
                            }
                        ) {
                            this.updateMaintenanceDuration(50.0)
                        }

                        meterRegistry
                            .get(Metrics.METRIC_MAINTENANCE_DURATION_NAME)
                            .tags(listOf(Tag.of(Metrics.TAG_WINDOW_NAME_NAME, TestWindowConfig.DEFAULT_NAME)))
                            .timer()
                            .totalTime(TimeUnit.MILLISECONDS)
                            .shouldBe(50.0)
                    }
                }
            }
        }

        context("updateObserverDuration") {
            given("metrics have not been initialized") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        with(MicrometerMetrics(SimpleMeterRegistry())) {
                            shouldThrowExactly<IllegalStateException> {
                                    this.updateObserverDuration(
                                        FixedTumblingBucketedWindow.METRICS_OBSERVER_STARTED_NAME,
                                        0.0,
                                    )
                                }
                                .message
                                .shouldBe(Metrics.EXCEPTION_MESSAGE_NOT_INITIALIZED)
                        }
                    }
                }
            }

            given("metrics have been initialized but with no observer event names registered") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        with(
                            MicrometerMetrics(SimpleMeterRegistry()).apply {
                                initialize(TestWindowConfig.fixed(), WindowMetricsConfig())
                            }
                        ) {
                            shouldThrowExactly<IllegalArgumentException> {
                                    this.updateObserverDuration(
                                        FixedTumblingBucketedWindow.METRICS_OBSERVER_STARTED_NAME,
                                        0.0,
                                    )
                                }
                                .message
                                .shouldBe(Metrics.EXCEPTION_MESSAGE_UNKNOWN_OBSERVER_EVENT)
                        }
                    }
                }
            }

            given("metrics have been initialized") {
                `when`("all observer durations are updated") {
                    then("the meter registry correctly reflects the durations") {
                        val config = TestWindowConfig.fixed()
                        val meterRegistry = SimpleMeterRegistry()

                        with(
                            MicrometerMetrics(meterRegistry).apply {
                                initialize(config, FixedTumblingBucketedWindow.metricsConfig(config))
                            }
                        ) {
                            this.updateObserverDuration(FixedTumblingBucketedWindow.METRICS_OBSERVER_STARTED_NAME, 10.0)
                            this.updateObserverDuration(FixedTumblingBucketedWindow.METRICS_OBSERVER_ENDING_NAME, 20.0)
                            this.updateObserverDuration(FixedTumblingBucketedWindow.METRICS_OBSERVER_UPDATED_NAME, 30.0)
                            this.updateObserverDuration(
                                FixedTumblingBucketedWindow.METRICS_OBSERVER_REMOVING_NAME,
                                40.0,
                            )
                        }

                        fun observerDurationTimer(observerEvent: String): Timer {
                            return meterRegistry
                                .get(Metrics.METRIC_OBSERVER_DURATION_NAME)
                                .tags(
                                    listOf(
                                        Tag.of(Metrics.TAG_WINDOW_NAME_NAME, TestWindowConfig.DEFAULT_NAME),
                                        Tag.of(Metrics.TAG_OBSERVER_DURATION_EVENT_NAME, observerEvent),
                                    )
                                )
                                .timer()
                        }

                        observerDurationTimer(FixedTumblingBucketedWindow.METRICS_OBSERVER_STARTED_NAME)
                            .totalTime(TimeUnit.MILLISECONDS)
                            .shouldBe(10.0)
                        observerDurationTimer(FixedTumblingBucketedWindow.METRICS_OBSERVER_ENDING_NAME)
                            .totalTime(TimeUnit.MILLISECONDS)
                            .shouldBe(20.0)
                        observerDurationTimer(FixedTumblingBucketedWindow.METRICS_OBSERVER_UPDATED_NAME)
                            .totalTime(TimeUnit.MILLISECONDS)
                            .shouldBe(30.0)
                        observerDurationTimer(FixedTumblingBucketedWindow.METRICS_OBSERVER_REMOVING_NAME)
                            .totalTime(TimeUnit.MILLISECONDS)
                            .shouldBe(40.0)
                    }
                }
            }
        }

        context("updateDataItemCount") {
            given("metrics have not been initialized") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        with(MicrometerMetrics(SimpleMeterRegistry())) {
                            shouldThrowExactly<IllegalStateException> { this.updateDataItemCount(String::class, 0) }
                                .message
                                .shouldBe(Metrics.EXCEPTION_MESSAGE_NOT_INITIALIZED)
                        }
                    }
                }
            }

            given("metrics have been initialized") {
                `when`("invoked for an invalid data class") {
                    then("an exception is thrown") {
                        val config = TestWindowConfig.fixed(forDataClasses = listOf(TestWindowBucketData::class))

                        with(
                            MicrometerMetrics(SimpleMeterRegistry()).apply {
                                initialize(config, FixedTumblingBucketedWindow.metricsConfig(config))
                            }
                        ) {
                            shouldThrowExactly<IllegalArgumentException> { this.updateDataItemCount(String::class, 0) }
                                .message
                                .shouldBe(Metrics.EXCEPTION_MESSAGE_CLASS_NOT_STORED)
                        }
                    }
                }
                `when`("the counts for 2 data items are updated") {
                    then("the meter registry correctly reflects the counts") {
                        val config =
                            TestWindowConfig.fixed(forDataClasses = listOf(TestWindowBucketData::class, String::class))
                        val meterRegistry = SimpleMeterRegistry()

                        with(
                            MicrometerMetrics(meterRegistry).apply {
                                initialize(config, FixedTumblingBucketedWindow.metricsConfig(config))
                            }
                        ) {
                            this.updateDataItemCount(TestWindowBucketData::class, 70)
                            this.updateDataItemCount(String::class, 80)
                        }

                        fun dataItemCountSummary(kClass: KClass<*>): DistributionSummary {
                            return meterRegistry
                                .get(Metrics.METRIC_DATA_ITEM_COUNT_NAME)
                                .tags(
                                    listOf(
                                        Tag.of(Metrics.TAG_WINDOW_NAME_NAME, TestWindowConfig.DEFAULT_NAME),
                                        Tag.of(Metrics.TAG_DATA_ITEM_COUNT_CLASS_NAME, kClass.simpleName!!),
                                    )
                                )
                                .summary()
                        }

                        dataItemCountSummary(TestWindowBucketData::class).totalAmount().shouldBe(70.0)
                        dataItemCountSummary(String::class).totalAmount().shouldBe(80.0)
                    }
                }
            }
        }
    })
