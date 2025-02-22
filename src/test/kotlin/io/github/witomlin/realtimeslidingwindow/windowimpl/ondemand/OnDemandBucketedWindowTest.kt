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

import io.github.witomlin.realtimeslidingwindow.BucketData
import io.github.witomlin.realtimeslidingwindow.BucketedWindow
import io.github.witomlin.realtimeslidingwindow.WindowName
import io.github.witomlin.realtimeslidingwindow.observability.Metrics
import io.github.witomlin.realtimeslidingwindow.observability.MicrometerMetrics
import io.github.witomlin.realtimeslidingwindow.test.*
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.reflect.KClass

class OnDemandBucketedWindowTest :
    BehaviorSpec({
        beforeTest { WindowName.unregister(TestWindowConfig.DEFAULT_NAME) }

        context("init") {
            given("the class has been initialized") {
                `when`("the metrics registry is inspected") {
                    then("window-specific metrics are correctly registered") {
                        val meterRegistry = SimpleMeterRegistry()
                        OnDemandBucketedWindow(TestWindowConfig.onDemand(metrics = MicrometerMetrics(meterRegistry)))

                        meterRegistry
                            .find(OnDemandBucketedWindow.METRICS_METRIC_VIEW_TUMBLING_DURATION_NAME)
                            .shouldNotBeNull()
                    }
                }
            }
        }

        context("startCore") {
            val scheduler = TestRunOnlyNTimesTaskScheduler(everyTimes = 1, everyRunAsync = false)

            OnDemandBucketedWindow(TestWindowConfig.onDemand(taskScheduler = scheduler)).apply { start() }
            scheduler.everyAllTimesRun.awaitDefault().shouldBeTrue()
        }

        context("addDataCore") {
            given("the window has been started") {
                `when`("invoked") {
                    then("the data is correctly added") {
                        val window =
                            OnDemandBucketedWindow(
                                    TestWindowConfig.onDemand(
                                        forDataClasses = listOf(TestWindowBucketData::class, String::class)
                                    )
                                )
                                .apply { start() }
                        window.addData(TestWindowBucketData())
                        window.addData("test")

                        val data =
                            TestReflection.getFieldValue<
                                Map<KClass<*>, ConcurrentSkipListSet<BucketData.TimestampedData<*>>>
                            >(
                                window,
                                OnDemandBucketedWindow::class,
                                "data",
                            )
                        data[TestWindowBucketData::class]!!.size.shouldBe(1)
                        data[String::class]!!.size.shouldBe(1)
                    }
                }
            }
        }

        context("onDemandTumblingBuckets") {
            given("the window has not been started") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        shouldThrowExactly<IllegalStateException> {
                                OnDemandBucketedWindow(TestWindowConfig.onDemand()).onDemandTumblingBuckets()
                            }
                            .message
                            .shouldBe(BucketedWindow.EXCEPTION_MESSAGE_NOT_STARTED)
                    }
                }
            }

            `when`("invoked with 'start' in the future") {
                then("an exception is thrown") {
                    with(OnDemandBucketedWindow(TestWindowConfig.onDemand()).apply { start() }) {
                        shouldThrowExactly<IllegalArgumentException> {
                                this.onDemandTumblingBuckets(start = Instant.MAX)
                            }
                            .message
                            .shouldBe(OnDemandBucketedWindow.EXCEPTION_MESSAGE_ODTB_START_IN_FUTURE)
                    }
                }
            }

            `when`("invoked with 'start' earlier than the start of the window") {
                then("an exception is thrown") {
                    with(OnDemandBucketedWindow(TestWindowConfig.onDemand()).apply { start() }) {
                        shouldThrowExactly<IllegalArgumentException> {
                                this.onDemandTumblingBuckets(start = Instant.MIN)
                            }
                            .message
                            .shouldBe(OnDemandBucketedWindow.EXCEPTION_MESSAGE_ODTB_START_TOO_EARLY)
                    }
                }
            }

            given("the window has been started") {
                `when`("invoked with 'length' = 0ms") {
                    then("an exception is thrown") {
                        with(OnDemandBucketedWindow(TestWindowConfig.onDemand()).apply { start() }) {
                            shouldThrowExactly<IllegalArgumentException> {
                                    this.onDemandTumblingBuckets(length = Duration.ofMillis(0))
                                }
                                .message
                                .shouldBe(OnDemandBucketedWindow.EXCEPTION_MESSAGE_ODTB_LENGTH_INSUFFICIENT)
                        }
                    }
                }

                `when`("invoked with 'start' + 'length' in the future") {
                    then("an exception is thrown") {
                        with(OnDemandBucketedWindow(TestWindowConfig.onDemand()).apply { start() }) {
                            shouldThrowExactly<IllegalArgumentException> {
                                    this.onDemandTumblingBuckets(start = Instant.now(), length = Duration.ofSeconds(5))
                                }
                                .message
                                .shouldBe(OnDemandBucketedWindow.EXCEPTION_MESSAGE_ODTB_START_LENGTH_IN_FUTURE)
                        }
                    }
                }

                `when`("invoked with 'bucketLength' = 0ms") {
                    then("an exception is thrown") {
                        with(OnDemandBucketedWindow(TestWindowConfig.onDemand()).apply { start() }) {
                            shouldThrowExactly<IllegalArgumentException> {
                                    this.onDemandTumblingBuckets(bucketLength = Duration.ofMillis(0))
                                }
                                .message
                                .shouldBe(OnDemandBucketedWindow.EXCEPTION_MESSAGE_ODTB_BUCKET_LENGTH_INSUFFICIENT)
                        }
                    }
                }

                `when`("invoked with 'length' < 'bucketLength'") {
                    then("an exception is thrown") {
                        with(OnDemandBucketedWindow(TestWindowConfig.onDemand()).apply { start() }) {
                            shouldThrowExactly<IllegalArgumentException> {
                                    this.onDemandTumblingBuckets(
                                        length = TestWindowConfig.DEFAULT_LENGTH.minusNanos(2),
                                        bucketLength = TestWindowConfig.DEFAULT_LENGTH.minusNanos(1),
                                    )
                                }
                                .message
                                .shouldBe(OnDemandBucketedWindow.EXCEPTION_MESSAGE_ODTB_LENGTH_LESS_THAN_BUCKET_LENGTH)
                        }
                    }
                }

                `when`("invoked with 'length' not an exact multiple of 'bucketLength'") {
                    then("an exception is thrown") {
                        with(OnDemandBucketedWindow(TestWindowConfig.onDemand()).apply { start() }) {
                            shouldThrowExactly<IllegalArgumentException> {
                                    this.onDemandTumblingBuckets(
                                        length = TestWindowConfig.DEFAULT_LENGTH,
                                        bucketLength = TestWindowConfig.DEFAULT_LENGTH.minusNanos(1),
                                    )
                                }
                                .message
                                .shouldBe(OnDemandBucketedWindow.EXCEPTION_MESSAGE_ODTB_LENGTH_MULTIPLE)
                        }
                    }
                }
            }

            given("the window has been started and data has been added") {
                `when`("invoked for a subset of the window") {
                    then("the correct non-empty buckets are returned") {
                        var isMetricsDurationOk = false
                        val metrics =
                            TestCallbackMetrics(
                                onUpdateTimer = { metric, _ ->
                                    if (
                                        metric.name == OnDemandBucketedWindow.METRICS_METRIC_VIEW_TUMBLING_DURATION_NAME
                                    )
                                        isMetricsDurationOk = true
                                }
                            )
                        val window =
                            OnDemandBucketedWindow(
                                    TestWindowConfig.onDemand(
                                        length = Duration.ofSeconds(5),
                                        forDataClasses = listOf(TestWindowBucketData::class, String::class),
                                        metrics = metrics,
                                    )
                                )
                                .apply { start() }
                        val data =
                            TestReflection.getFieldValue<
                                Map<KClass<*>, ConcurrentSkipListSet<BucketData.TimestampedData<*>>>
                            >(
                                window,
                                OnDemandBucketedWindow::class,
                                "data",
                            )

                        val now = Instant.now()
                        val start = now.minusSeconds(4)
                        val length = Duration.ofSeconds(3)
                        val bucketLength = Duration.ofSeconds(1)
                        val minusSecondsFromNow = listOf(5L, 4L, 3L, 2L, 1L)
                        val strings = listOf("1", "2", "3", "4", "5")

                        for (i in minusSecondsFromNow.indices) {
                            data[TestWindowBucketData::class]!!.add(
                                BucketData.TimestampedData(
                                    now.minusSeconds(minusSecondsFromNow[i]),
                                    TestWindowBucketData(strings[i]),
                                )
                            )
                            data[TestWindowBucketData::class]!!.add(
                                BucketData.TimestampedData(
                                    now.minusSeconds(minusSecondsFromNow[i]).plus(bucketLength).minusNanos(1),
                                    TestWindowBucketData(strings[i]),
                                )
                            )

                            data[String::class]!!.add(
                                BucketData.TimestampedData(now.minusSeconds(minusSecondsFromNow[i]), strings[i])
                            )
                            data[String::class]!!.add(
                                BucketData.TimestampedData(
                                    now.minusSeconds(minusSecondsFromNow[i]).plus(bucketLength).minusNanos(1),
                                    strings[i],
                                )
                            )
                        }

                        withConstantNow(now) {
                            with(window.onDemandTumblingBuckets(start, length, bucketLength)) {
                                this.size.shouldBe(3)
                                Duration.between(this[0].start, this[2].end).shouldBe(length.minusNanos(1))

                                with(this[0]) {
                                    val bucketStart = this.start.also { it.shouldBe(now.minusSeconds(4)) }
                                    val bucketEnd = this.end.also { it.shouldBe(now.minusSeconds(3).minusNanos(1)) }

                                    with(this.dataForClass(TestWindowBucketData::class)) {
                                        this.size.shouldBe(2)
                                        this.entries.forEach {
                                            it.timestamp.shouldBeBetweenInclusive(bucketStart, bucketEnd)
                                            it.data.prop1.shouldBe("2")
                                        }
                                    }

                                    with(this.dataForClass(String::class)) {
                                        this.size.shouldBe(2)
                                        this.entries.forEach {
                                            it.timestamp.shouldBeBetweenInclusive(bucketStart, bucketEnd)
                                            it.data.shouldBe("2")
                                        }
                                    }
                                }

                                with(this[1]) {
                                    val bucketStart = this.start.also { it.shouldBe(now.minusSeconds(3)) }
                                    val bucketEnd = this.end.also { it.shouldBe(now.minusSeconds(2).minusNanos(1)) }

                                    with(this.dataForClass(TestWindowBucketData::class)) {
                                        this.size.shouldBe(2)
                                        this.entries.forEach {
                                            it.timestamp.shouldBeBetweenInclusive(bucketStart, bucketEnd)
                                            it.data.prop1.shouldBe("3")
                                        }
                                    }

                                    with(this.dataForClass(String::class)) {
                                        this.size.shouldBe(2)
                                        this.entries.forEach {
                                            it.timestamp.shouldBeBetweenInclusive(bucketStart, bucketEnd)
                                            it.data.shouldBe("3")
                                        }
                                    }
                                }

                                with(this[2]) {
                                    val bucketStart = this.start.also { it.shouldBe(now.minusSeconds(2)) }
                                    val bucketEnd = this.end.also { it.shouldBe(now.minusSeconds(1).minusNanos(1)) }

                                    with(this.dataForClass(TestWindowBucketData::class)) {
                                        this.size.shouldBe(2)
                                        this.entries.forEach {
                                            it.timestamp.shouldBeBetweenInclusive(bucketStart, bucketEnd)
                                            it.data.prop1.shouldBe("4")
                                        }
                                    }

                                    with(this.dataForClass(String::class)) {
                                        this.size.shouldBe(2)
                                        this.entries.forEach {
                                            it.timestamp.shouldBeBetweenInclusive(bucketStart, bucketEnd)
                                            it.data.shouldBe("4")
                                        }
                                    }
                                }
                            }
                        }

                        isMetricsDurationOk.shouldBeTrue()
                    }
                }

                `when`("invoked with all defaults") {
                    then("a correct single bucket is returned") {
                        val configLength = Duration.ofSeconds(3)
                        val window =
                            OnDemandBucketedWindow(
                                    TestWindowConfig.onDemand(
                                        length = configLength,
                                        forDataClasses = listOf(TestWindowBucketData::class, String::class),
                                    )
                                )
                                .apply { start() }
                        val data =
                            TestReflection.getFieldValue<
                                Map<KClass<*>, ConcurrentSkipListSet<BucketData.TimestampedData<*>>>
                            >(
                                window,
                                OnDemandBucketedWindow::class,
                                "data",
                            )

                        val now = Instant.now()
                        data[TestWindowBucketData::class]!!.add(
                            BucketData.TimestampedData(now.minus(configLength), TestWindowBucketData("1"))
                        )
                        data[TestWindowBucketData::class]!!.add(
                            BucketData.TimestampedData(now.minusNanos(1), TestWindowBucketData("2"))
                        )
                        data[String::class]!!.add(BucketData.TimestampedData(now.minus(configLength), "1"))
                        data[String::class]!!.add(BucketData.TimestampedData(now.minusNanos(1), "2"))

                        withConstantNow(now) {
                            with(window.onDemandTumblingBuckets()) {
                                this.size.shouldBe(1)
                                Duration.between(this[0].start, this[0].end).shouldBe(configLength.minusNanos(1))

                                with(this[0]) {
                                    this.start.shouldBe(now.minus(configLength))
                                    this.end.shouldBe(now.minusNanos(1))
                                    this.dataForClass(TestWindowBucketData::class).size.shouldBe(2)
                                    this.dataForClass(String::class).size.shouldBe(2)
                                }
                            }
                        }
                    }
                }

                `when`("invoked with with only 'start'") {
                    then("a correct single bucket is returned") {
                        val configLength = Duration.ofSeconds(3)
                        val window =
                            OnDemandBucketedWindow(
                                    TestWindowConfig.onDemand(
                                        length = configLength,
                                        forDataClasses = listOf(TestWindowBucketData::class, String::class),
                                    )
                                )
                                .apply { start() }
                        val data =
                            TestReflection.getFieldValue<
                                Map<KClass<*>, ConcurrentSkipListSet<BucketData.TimestampedData<*>>>
                            >(
                                window,
                                OnDemandBucketedWindow::class,
                                "data",
                            )

                        val now = Instant.now()
                        val start = now.minus(configLength.dividedBy(2))
                        data[TestWindowBucketData::class]!!.add(
                            BucketData.TimestampedData(start, TestWindowBucketData("1"))
                        )
                        data[TestWindowBucketData::class]!!.add(
                            BucketData.TimestampedData(now.minusNanos(1), TestWindowBucketData("2"))
                        )
                        data[String::class]!!.add(BucketData.TimestampedData(start, "1"))
                        data[String::class]!!.add(BucketData.TimestampedData(now.minusNanos(1), "2"))

                        withConstantNow(now) {
                            with(window.onDemandTumblingBuckets(start = start)) {
                                this.size.shouldBe(1)
                                Duration.between(this[0].start, this[0].end)
                                    .shouldBe(Duration.between(start, now).minusNanos(1))

                                with(this[0]) {
                                    this.start.shouldBe(start)
                                    this.end.shouldBe(now.minusNanos(1))
                                    this.dataForClass(TestWindowBucketData::class).size.shouldBe(2)
                                    this.dataForClass(String::class).size.shouldBe(2)
                                }
                            }
                        }
                    }
                }
            }

            given("the window has been started and no data has been added") {
                `when`("invoked") {
                    then("the correct empty buckets are returned") {
                        val window =
                            OnDemandBucketedWindow(
                                    TestWindowConfig.onDemand(
                                        length = Duration.ofSeconds(5),
                                        forDataClasses = listOf(TestWindowBucketData::class, String::class),
                                    )
                                )
                                .apply { start() }

                        val now = Instant.now()
                        val start = now.minusSeconds(4)
                        val length = Duration.ofSeconds(3)
                        val bucketLength = Duration.ofSeconds(1)

                        withConstantNow(now) {
                            with(window.onDemandTumblingBuckets(start, length, bucketLength)) {
                                this.size.shouldBe(3)
                                Duration.between(this[0].start, this[2].end).shouldBe(length.minusNanos(1))

                                with(this[0]) {
                                    this.start.shouldBe(now.minusSeconds(4))
                                    this.end.shouldBe(now.minusSeconds(3).minusNanos(1))
                                    this.dataForClass(TestWindowBucketData::class).size.shouldBe(0)
                                    this.dataForClass(String::class).size.shouldBe(0)
                                }

                                with(this[1]) {
                                    this.start.shouldBe(now.minusSeconds(3))
                                    this.end.shouldBe(now.minusSeconds(2).minusNanos(1))
                                    this.dataForClass(TestWindowBucketData::class).size.shouldBe(0)
                                    this.dataForClass(String::class).size.shouldBe(0)
                                }

                                with(this[2]) {
                                    this.start.shouldBe(now.minusSeconds(2))
                                    this.end.shouldBe(now.minusSeconds(1).minusNanos(1))
                                    this.dataForClass(TestWindowBucketData::class).size.shouldBe(0)
                                    this.dataForClass(String::class).size.shouldBe(0)
                                }
                            }
                        }
                    }
                }
            }
        }

        context("dataMaintenance") {
            given("a window length of 3 seconds and data earlier than the start of the window is present") {
                `when`("invoked") {
                    then("data earlier than the start of the window is removed") {
                        val configLength = Duration.ofSeconds(3)

                        var isMetricsMaintenanceOk = false
                        val metrics =
                            TestCallbackMetrics(
                                onUpdateTimer = { metric, _ ->
                                    if (metric.name == Metrics.METRIC_MAINTENANCE_DURATION_NAME)
                                        isMetricsMaintenanceOk = true
                                }
                            )
                        val window =
                            OnDemandBucketedWindow(
                                TestWindowConfig.onDemand(
                                    length = configLength,
                                    forDataClasses = listOf(TestWindowBucketData::class, String::class),
                                    taskScheduler =
                                        TestRunOnlyNTimesTaskScheduler(everyTimes = 1, everyRunAsync = false),
                                    metrics = metrics,
                                )
                            )

                        val data =
                            TestReflection.getFieldValue<
                                Map<KClass<*>, ConcurrentSkipListSet<BucketData.TimestampedData<*>>>
                            >(
                                window,
                                OnDemandBucketedWindow::class,
                                "data",
                            )

                        val now = Instant.now()
                        val minusSecondsFromNow = listOf(5L, 4L, 3L, 2L, 1L)

                        for (i in minusSecondsFromNow.indices) {
                            data[TestWindowBucketData::class]!!.add(
                                BucketData.TimestampedData(
                                    now.minusSeconds(minusSecondsFromNow[i]),
                                    TestWindowBucketData(),
                                )
                            )

                            data[String::class]!!.add(
                                BucketData.TimestampedData(now.minusSeconds(minusSecondsFromNow[i]), "test")
                            )
                        }

                        withConstantNow(now) { window.start() }

                        with(data[TestWindowBucketData::class]!!) {
                            this.size.shouldBe(3)
                            this.forEach { it.timestamp.shouldBeBetweenInclusive(now.minus(configLength), now) }
                        }

                        with(data[String::class]!!) {
                            this.size.shouldBe(3)
                            this.forEach { it.timestamp.shouldBeBetweenInclusive(now.minus(configLength), now) }
                        }

                        with(
                            TestReflection.getFieldValue<ConcurrentHashMap<KClass<*>, Int>>(
                                window,
                                BucketedWindow::class,
                                "dataItemCounts",
                            )
                        ) {
                            this[TestWindowBucketData::class].shouldBe(3)
                            this[String::class].shouldBe(3)
                        }

                        isMetricsMaintenanceOk.shouldBeTrue()
                    }
                }
            }

            given("a window length of 3 seconds and no data earlier than the start of the window is present") {
                `when`("invoked") {
                    then("no data is removed") {
                        val configLength = Duration.ofSeconds(3)
                        val window =
                            OnDemandBucketedWindow(
                                TestWindowConfig.onDemand(
                                    length = configLength,
                                    forDataClasses = listOf(TestWindowBucketData::class, String::class),
                                    taskScheduler =
                                        TestRunOnlyNTimesTaskScheduler(everyTimes = 1, everyRunAsync = false),
                                )
                            )

                        val data =
                            TestReflection.getFieldValue<
                                Map<KClass<*>, ConcurrentSkipListSet<BucketData.TimestampedData<*>>>
                            >(
                                window,
                                OnDemandBucketedWindow::class,
                                "data",
                            )

                        val now = Instant.now()
                        val minusSecondsFromNow = listOf(3L, 2L, 1L)

                        for (i in minusSecondsFromNow.indices) {
                            data[TestWindowBucketData::class]!!.add(
                                BucketData.TimestampedData(
                                    now.minusSeconds(minusSecondsFromNow[i]),
                                    TestWindowBucketData(),
                                )
                            )

                            data[String::class]!!.add(
                                BucketData.TimestampedData(now.minusSeconds(minusSecondsFromNow[i]), "test")
                            )
                        }

                        withConstantNow(now) { window.start() }

                        data[TestWindowBucketData::class]!!.size.shouldBe(3)
                        data[String::class]!!.size.shouldBe(3)
                    }
                }
            }

            given("a window length of 3 seconds and no data is present") {
                `when`("invoked") {
                    then("an exception is not thrown") {
                        with(
                            OnDemandBucketedWindow(
                                TestWindowConfig.onDemand(
                                    taskScheduler =
                                        TestRunOnlyNTimesTaskScheduler(everyTimes = 1, everyRunAsync = false)
                                )
                            )
                        ) {
                            shouldNotThrowAny { this.start() }
                        }
                    }
                }
            }
        }
    })
