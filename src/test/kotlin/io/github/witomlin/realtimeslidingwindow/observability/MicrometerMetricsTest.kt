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

package io.github.witomlin.realtimeslidingwindow.observability

import io.github.witomlin.realtimeslidingwindow.WindowMetricsConfig
import io.github.witomlin.realtimeslidingwindow.test.TestWindowBucketData
import io.github.witomlin.realtimeslidingwindow.test.TestWindowConfig
import io.github.witomlin.realtimeslidingwindow.windowimpl.fixedtumbling.FixedTumblingBucketedWindow
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContainKeys
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.concurrent.TimeUnit

class MicrometerMetricsTest :
    BehaviorSpec({
        context("initialize") {
            given("metrics have already been initialized") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        val config = TestWindowConfig.fixed()

                        with(
                            MicrometerMetrics(SimpleMeterRegistry()).apply {
                                initialize(config, FixedTumblingBucketedWindow.metricsConfig(config) { 0 })
                            }
                        ) {
                            shouldThrowExactly<IllegalStateException> {
                                    initialize(config, FixedTumblingBucketedWindow.metricsConfig(config) { 0 })
                                }
                                .message
                                .shouldBe(Metrics.EXCEPTION_MESSAGE_ALREADY_INITIALIZED)
                        }
                    }
                }
            }

            given("metrics have not already been initialized") {
                `when`("invoked") {
                    then("the correct standard and extra metrics are rendered") {
                        val config =
                            TestWindowConfig.fixed(forDataClasses = listOf(TestWindowBucketData::class, String::class))
                        val metricsConfig =
                            WindowMetricsConfig(
                                listOf("event1", "event2"),
                                listOf(
                                    Metrics.gauge("extra.gauge", mapOf()) { 0.0 },
                                    Metrics.timer("extra.timer", mapOf()),
                                ),
                            ) {
                                1
                            }

                        fun validateMetric(metrics: List<Metrics.Metric>, name: String, expectedSize: Int) {
                            with(metrics.filter { it.name == name }) {
                                this.size.shouldBe(expectedSize)
                                this.forEach { it.tags.shouldContainKeys(Metrics.TAG_WINDOW_NAME_NAME) }
                            }
                        }

                        with(
                            MicrometerMetrics(SimpleMeterRegistry())
                                .apply { initialize(config, metricsConfig) }
                                .renderedMetrics
                        ) {
                            validateMetric(this, Metrics.METRIC_CONFIG_LENGTH_NAME, 1)
                            validateMetric(this, Metrics.METRIC_MAINTENANCE_DURATION_NAME, 1)
                            validateMetric(this, Metrics.METRIC_OBSERVER_DURATION_NAME, 2)
                            validateMetric(this, Metrics.METRIC_DATA_ITEMS_NAME, 2)
                            validateMetric(this, "extra.gauge", 1)
                            validateMetric(this, "extra.timer", 1)
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
                                initialize(config, FixedTumblingBucketedWindow.metricsConfig(config) { 0 })
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
                                initialize(TestWindowConfig.fixed(), WindowMetricsConfig { 0 })
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
                                initialize(config, FixedTumblingBucketedWindow.metricsConfig(config) { 0 })
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
    })
