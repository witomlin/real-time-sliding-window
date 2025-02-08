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

import io.github.witomlin.timeslidingwindow.BucketedWindow
import io.github.witomlin.timeslidingwindow.WindowName
import io.github.witomlin.timeslidingwindow.observability.LoggerFactory
import io.github.witomlin.timeslidingwindow.observability.Metrics
import io.github.witomlin.timeslidingwindow.observability.MicrometerMetrics
import io.github.witomlin.timeslidingwindow.test.*
import io.kotest.assertions.throwables.shouldNotThrowAnyUnit
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.assertions.throwables.shouldThrowExactlyUnit
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import org.slf4j.Logger

class FixedTumblingBucketedWindowTest :
    BehaviorSpec({
        beforeTest { WindowName.unregister(TestWindowConfig.DEFAULT_NAME) }

        context("init") {
            given("the class is being initialized") {
                `when`("the same window name has already been registered") {
                    then("an exception is thrown") {
                        FixedTumblingBucketedWindow(TestWindowConfig.fixed())
                        shouldThrowExactly<IllegalStateException> {
                                FixedTumblingBucketedWindow(TestWindowConfig.fixed())
                            }
                            .message
                            .shouldBe(WindowName.EXCEPTION_MESSAGE_NAME_ALREADY_REGISTERED)
                    }
                }
            }

            given("the class has been initialized") {
                `when`("the metrics registry is inspected") {
                    then("window-specific metrics are correctly registered") {
                        val meterRegistry = SimpleMeterRegistry()
                        FixedTumblingBucketedWindow(TestWindowConfig.fixed(metrics = MicrometerMetrics(meterRegistry)))

                        meterRegistry
                            .find(FixedTumblingBucketedWindow.METRICS_METRIC_CONFIG_BUCKET_LENGTH_NAME)
                            .shouldNotBeNull()
                        meterRegistry
                            .find(FixedTumblingBucketedWindow.METRICS_METRIC_ACTUAL_BUCKET_DURATION_NAME)
                            .shouldNotBeNull()
                    }
                }
            }
        }

        context("buckets") {
            given("the window has not been started") {
                `when`("accessed") {
                    then("an exception is thrown") {
                        with(FixedTumblingBucketedWindow(TestWindowConfig.fixed())) {
                            shouldThrowExactlyUnit<IllegalStateException> { this.buckets }
                                .message
                                .shouldBe(BucketedWindow.EXCEPTION_MESSAGE_NOT_STARTED)
                        }
                    }
                }
            }

            given("the window has been started") {
                `when`("accessed") {
                    then("an exception is not thrown") {
                        with(FixedTumblingBucketedWindow(TestWindowConfig.fixed()).apply { start() }) {
                            shouldNotThrowAnyUnit { this.buckets }
                        }
                    }
                }
            }
        }

        context("currentBucket") {
            given("the window has not been started") {
                `when`("accessed") {
                    then("an exception is thrown") {
                        with(FixedTumblingBucketedWindow(TestWindowConfig.fixed())) {
                            shouldThrowExactlyUnit<IllegalStateException> { this.currentBucket }
                                .message
                                .shouldBe(BucketedWindow.EXCEPTION_MESSAGE_NOT_STARTED)
                        }
                    }
                }
            }

            given("the window has been started") {
                `when`("accessed") {
                    then("an exception is not thrown") {
                        with(FixedTumblingBucketedWindow(TestWindowConfig.fixed()).apply { start() }) {
                            shouldNotThrowAnyUnit { this.currentBucket }
                        }
                    }
                }
            }
        }

        context("nonCurrentBuckets") {
            given("the window has not been started") {
                `when`("accessed") {
                    then("an exception is thrown") {
                        with(FixedTumblingBucketedWindow(TestWindowConfig.fixed())) {
                            shouldThrowExactlyUnit<IllegalStateException> { this.nonCurrentBuckets }
                                .message
                                .shouldBe(BucketedWindow.EXCEPTION_MESSAGE_NOT_STARTED)
                        }
                    }
                }
            }

            given("the window has been started") {
                `when`("accessed") {
                    then("an exception is not thrown") {
                        with(FixedTumblingBucketedWindow(TestWindowConfig.fixed()).apply { start() }) {
                            shouldNotThrowAnyUnit { this.nonCurrentBuckets }
                        }
                    }
                }
            }
        }

        context("start") {
            given("the window has already been started") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        with(FixedTumblingBucketedWindow(TestWindowConfig.fixed()).apply { start() }) {
                            shouldThrowExactly<IllegalStateException> { this.start() }
                                .message
                                .shouldBe(BucketedWindow.EXCEPTION_MESSAGE_ALREADY_STARTED)
                        }
                    }
                }
            }

            given("the window has not already been started") {
                `when`("invoked") {
                    then("the window is correctly started") {
                        var isObsStartedOk = false
                        val observer =
                            { event: FixedTumblingBucketedWindow.Event, buckets: List<FixedTumblingBucketedWindowBucket>
                                ->
                                if (
                                    event == FixedTumblingBucketedWindow.Event.CurrentBucketStarted &&
                                        buckets[0].status == FixedTumblingBucketedWindowBucket.Status.CURRENT
                                )
                                    isObsStartedOk = true
                            }

                        withConstantNow(Instant.MIN) {
                            with(
                                FixedTumblingBucketedWindow(TestWindowConfig.fixed()).apply {
                                    addObserver(observer)
                                    start()
                                }
                            ) {
                                this.buckets.size.shouldBe(1)

                                with(this.currentBucket) {
                                    this.status.shouldBe(
                                        io.github.witomlin.timeslidingwindow.windowimpl.fixedtumbling
                                            .FixedTumblingBucketedWindowBucket
                                            .Status
                                            .CURRENT
                                    )
                                    this.start.shouldBe(Instant.MIN)
                                }

                                this.nonCurrentBuckets.shouldBeEmpty()
                            }
                        }

                        isObsStartedOk.shouldBeTrue()
                    }
                }
            }
        }

        context("addData") {
            given("the window has not been started") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        with(FixedTumblingBucketedWindow(TestWindowConfig.fixed())) {
                            shouldThrowExactly<IllegalStateException> { this.addData(TestWindowBucketData()) }
                                .message
                                .shouldBe(BucketedWindow.EXCEPTION_MESSAGE_NOT_STARTED)
                        }
                    }
                }
            }

            given("an invalid data class is provided") {
                `when`("invoking") {
                    then("an exception is thrown") {
                        with(
                            FixedTumblingBucketedWindow(
                                    TestWindowConfig.fixed(forDataClasses = listOf(TestWindowBucketData::class))
                                )
                                .apply { start() }
                        ) {
                            shouldThrowExactly<IllegalArgumentException> { this.addData("test") }
                                .message
                                .shouldBe(BucketedWindow.EXCEPTION_MESSAGE_CLASS_NOT_STORED)
                        }
                    }
                }
            }

            given("the window has been started") {
                `when`("invoked") {
                    then("the data is correctly added") {
                        with(
                            FixedTumblingBucketedWindow(
                                    TestWindowConfig.fixed(
                                        forDataClasses = listOf(TestWindowBucketData::class, String::class)
                                    )
                                )
                                .apply { start() }
                        ) {
                            this.addData(TestWindowBucketData())
                            this.addData("test")

                            with(this.currentBucket) {
                                this.dataForClass(TestWindowBucketData::class).entries.size.shouldBe(1)
                                this.dataForClass(String::class).entries.size.shouldBe(1)
                            }
                        }
                    }
                }
            }
        }

        context("currentBucketEnd") {
            given("the window has not been started") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        val scheduler = TestRunOnlyNTimesTaskScheduler(forTimes = 1, forRunAsync = false)
                        FixedTumblingBucketedWindow(TestWindowConfig.fixed(taskScheduler = scheduler)).apply { start() }
                        scheduler.forExceptions[1]!!
                            .shouldBeInstanceOf<IllegalStateException>()
                            .message
                            .shouldBe(BucketedWindow.EXCEPTION_MESSAGE_NOT_STARTED)
                    }
                }
            }

            given("the bucket is not marked as current") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        val runEnd = CountDownLatch(1)
                        val scheduler =
                            TestRunOnlyNTimesTaskScheduler(forTimes = 1, forWaitUntilPerTime = listOf(runEnd))
                        val window =
                            FixedTumblingBucketedWindow(TestWindowConfig.fixed(taskScheduler = scheduler)).apply {
                                start()
                            }

                        TestReflection.setFieldValue(
                            window.currentBucket,
                            "_endInfo",
                            FixedTumblingBucketedWindowBucket.EndInfo(Instant.MIN, 0),
                        )

                        runEnd.countDown()
                        scheduler.forAllTimesRun.awaitDefault().shouldBeTrue()
                        scheduler.forExceptions[1]!!
                            .shouldBeInstanceOf<IllegalArgumentException>()
                            .message
                            .shouldBe(FixedTumblingBucketedWindow.EXCEPTION_MESSAGE_ENDED_BUCKET_NOT_MARKED_CURRENT)
                    }
                }
            }

            given("the first ending bucket is valid") {
                `when`("invoked") {
                    then("the bucket is correctly ended") {
                        var isMetricsObsEndingOk = false
                        var isMetricsObsStartedOk = false
                        var isMetricsObsUpdatedOk = false
                        var isMetricsBucketOk = false
                        var isMetricsCount1Ok = false
                        var isMetricsCount2Ok = false
                        var isMetricsMaintenanceOk = false
                        val metrics =
                            TestCallbackMetrics(
                                onUpdateTimer = { metric, _ ->
                                    if (
                                        metric.name == Metrics.METRIC_OBSERVER_DURATION_NAME &&
                                            metric.tags[Metrics.TAG_OBSERVER_DURATION_EVENT_NAME] ==
                                                FixedTumblingBucketedWindow.METRICS_OBSERVER_ENDING_NAME
                                    )
                                        isMetricsObsEndingOk = true
                                    if (
                                        metric.name == Metrics.METRIC_OBSERVER_DURATION_NAME &&
                                            metric.tags[Metrics.TAG_OBSERVER_DURATION_EVENT_NAME] ==
                                                FixedTumblingBucketedWindow.METRICS_OBSERVER_STARTED_NAME
                                    )
                                        isMetricsObsStartedOk = true
                                    if (
                                        metric.name == Metrics.METRIC_OBSERVER_DURATION_NAME &&
                                            metric.tags[Metrics.TAG_OBSERVER_DURATION_EVENT_NAME] ==
                                                FixedTumblingBucketedWindow.METRICS_OBSERVER_UPDATED_NAME
                                    )
                                        isMetricsObsUpdatedOk = true
                                    if (
                                        metric.name ==
                                            FixedTumblingBucketedWindow.METRICS_METRIC_ACTUAL_BUCKET_DURATION_NAME
                                    )
                                        isMetricsBucketOk = true
                                    if (metric.name == Metrics.METRIC_MAINTENANCE_DURATION_NAME)
                                        isMetricsMaintenanceOk = true
                                },
                                onUpdateDistributionSummary = { metric, value ->
                                    if (
                                        metric.name == Metrics.METRIC_DATA_ITEM_COUNT_NAME &&
                                            metric.tags[Metrics.TAG_DATA_ITEM_COUNT_CLASS_NAME] ==
                                                TestWindowBucketData::class.simpleName &&
                                            value == 2.0
                                    )
                                        isMetricsCount1Ok = true
                                    if (
                                        metric.name == Metrics.METRIC_DATA_ITEM_COUNT_NAME &&
                                            metric.tags[Metrics.TAG_DATA_ITEM_COUNT_CLASS_NAME] ==
                                                String::class.simpleName &&
                                            value == 1.0
                                    )
                                        isMetricsCount2Ok = true
                                },
                            )

                        val dataPopulated = CountDownLatch(1)
                        val scheduler =
                            TestRunOnlyNTimesTaskScheduler(
                                forTimes = 1,
                                forWaitUntilPerTime = listOf(dataPopulated),
                                executeTimes = 1,
                            )

                        var isObsEndingOk = false
                        var isObsStartedOk = false
                        var isObsUpdatedOk = false
                        val observer =
                            { event: FixedTumblingBucketedWindow.Event, buckets: List<FixedTumblingBucketedWindowBucket>
                                ->
                                if (
                                    event == FixedTumblingBucketedWindow.Event.CurrentBucketEnding &&
                                        buckets[0].status == FixedTumblingBucketedWindowBucket.Status.CURRENT
                                )
                                    isObsEndingOk = true
                                if (
                                    event == FixedTumblingBucketedWindow.Event.CurrentBucketStarted &&
                                        buckets[0].status == FixedTumblingBucketedWindowBucket.Status.CURRENT
                                )
                                    isObsStartedOk = true
                                if (event == FixedTumblingBucketedWindow.Event.NonCurrentBucketsUpdated)
                                    isObsUpdatedOk = true
                            }

                        with(
                            FixedTumblingBucketedWindow(
                                    TestWindowConfig.fixed(
                                        forDataClasses = listOf(TestWindowBucketData::class, String::class),
                                        metrics = metrics,
                                        taskScheduler = scheduler,
                                    )
                                )
                                .apply {
                                    addObserver(observer)
                                    start()
                                }
                        ) {
                            this.addData(TestWindowBucketData())
                            this.addData(TestWindowBucketData())
                            this.addData("test")

                            val beforeCurrentBucket = this.currentBucket

                            dataPopulated.countDown() // Trigger scheduler
                            scheduler.forAllTimesRun.awaitDefault().shouldBeTrue()

                            isObsEndingOk.shouldBeTrue()
                            isMetricsObsEndingOk.shouldBeTrue()

                            this.buckets.size.shouldBe(2)

                            with(this.currentBucket) {
                                this.status.shouldBe(
                                    io.github.witomlin.timeslidingwindow.windowimpl.fixedtumbling
                                        .FixedTumblingBucketedWindowBucket
                                        .Status
                                        .CURRENT
                                )
                                this.start.shouldBe(beforeCurrentBucket.endInfo!!.actualEnd.plusNanos(1))
                            }

                            isObsStartedOk.shouldBeTrue()
                            isMetricsObsStartedOk.shouldBeTrue()

                            with(this.nonCurrentBuckets) {
                                this.size.shouldBe(1)
                                this[0]
                                    .status
                                    .shouldBe(
                                        io.github.witomlin.timeslidingwindow.windowimpl.fixedtumbling
                                            .FixedTumblingBucketedWindowBucket
                                            .Status
                                            .NON_CURRENT
                                    )
                                this[0].shouldBeEqual(beforeCurrentBucket)
                            }

                            scheduler.executeAllTimesRun.awaitDefault().shouldBeTrue()
                            isObsUpdatedOk.shouldBeTrue()
                            isMetricsObsUpdatedOk.shouldBeTrue()
                            isMetricsBucketOk.shouldBeTrue()
                            isMetricsCount1Ok.shouldBeTrue()
                            isMetricsCount2Ok.shouldBeTrue()
                            isMetricsMaintenanceOk.shouldBeTrue()
                        }
                    }
                }
            }

            given("enough buckets have already ended to fill the window") {
                `when`("invoked") {
                    then("the window maintains the correct non-current bucket size") {
                        val scheduler = TestRunOnlyNTimesTaskScheduler(forTimes = 4)

                        val window =
                            FixedTumblingBucketedWindow(
                                TestWindowConfig.fixed(
                                    length = Duration.ofSeconds(3),
                                    bucketLength = Duration.ofSeconds(1),
                                    taskScheduler = scheduler,
                                )
                            )

                        var isObsRemovingOk = false
                        val observer =
                            { event: FixedTumblingBucketedWindow.Event, buckets: List<FixedTumblingBucketedWindowBucket>
                                ->
                                if (
                                    event == FixedTumblingBucketedWindow.Event.NonCurrentBucketRemoving &&
                                        buckets[0] == window.nonCurrentBuckets[0]
                                )
                                    isObsRemovingOk = true
                            }

                        with(
                            window.apply {
                                addObserver(observer)
                                start()
                            }
                        ) {
                            scheduler.forAllTimesRun.awaitDefault().shouldBeTrue()
                            isObsRemovingOk.shouldBeTrue()

                            with(this.nonCurrentBuckets) {
                                this.size.shouldBe(3)
                                this.forEach {
                                    it.status.shouldBe(
                                        io.github.witomlin.timeslidingwindow.windowimpl.fixedtumbling
                                            .FixedTumblingBucketedWindowBucket
                                            .Status
                                            .NON_CURRENT
                                    )
                                }
                            }
                        }
                    }
                }
            }

            given("a bucket exceeds its intended duration by 10%") {
                `when`("invoked") {
                    then("a warning is logged") {
                        val scheduler = TestRunOnlyNTimesTaskScheduler(forTimes = 1)

                        mockkObject(LoggerFactory) {
                            val mockLogger = mockk<Logger>(relaxed = true)
                            every { LoggerFactory.getLogger(FixedTumblingBucketedWindow::class.java) } returns
                                mockLogger

                            mockkStatic(Instant::class) {
                                every { Instant.now() } returnsMany
                                    listOf(Instant.MIN, Instant.MIN.plus(Duration.ofMillis(1101)))

                                FixedTumblingBucketedWindow(
                                        TestWindowConfig.fixed(
                                            length = Duration.ofMillis(1000),
                                            bucketLength = Duration.ofMillis(1000),
                                            taskScheduler = scheduler,
                                        )
                                    )
                                    .apply { start() }

                                scheduler.forAllTimesRun.awaitDefault().shouldBeTrue()
                                verify {
                                    mockLogger.warn(
                                        match { it.contains("greater than 110% of intended duration") },
                                        *anyVararg(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    })
