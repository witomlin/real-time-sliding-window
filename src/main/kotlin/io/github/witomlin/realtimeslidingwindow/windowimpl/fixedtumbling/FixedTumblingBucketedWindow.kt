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

import io.github.witomlin.realtimeslidingwindow.*
import io.github.witomlin.realtimeslidingwindow.core.GenericSubject
import io.github.witomlin.realtimeslidingwindow.core.WrappedReentrantReadWriteLock
import io.github.witomlin.realtimeslidingwindow.core.measureTimeMsDouble
import io.github.witomlin.realtimeslidingwindow.observability.LoggerFactory
import io.github.witomlin.realtimeslidingwindow.observability.Metrics
import io.github.witomlin.realtimeslidingwindow.observability.prefixForWindowLog
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

class FixedTumblingBucketedWindow(private val config: FixedTumblingBucketedWindowConfig) :
    BucketedWindow<FixedTumblingBucketedWindowBucket, FixedTumblingBucketedWindow.Event>(config) {
    internal companion object {
        const val METRICS_OBSERVER_STARTED_NAME = "current_bucket_started"
        const val METRICS_OBSERVER_ENDING_NAME = "current_bucket_ending"
        const val METRICS_OBSERVER_UPDATED_NAME = "non_current_buckets_updated"
        const val METRICS_OBSERVER_REMOVING_NAME = "non_current_bucket_removing"

        const val METRICS_METRIC_CONFIG_BUCKET_LENGTH_NAME = "window.config.bucket.length_ms"
        const val METRICS_METRIC_BUCKET_DURATION_NAME = "window.bucket.duration"

        const val EXCEPTION_MESSAGE_ENDED_BUCKET_NOT_MARKED_CURRENT = "Ended bucket is not marked as current"

        fun metricsConfig(config: FixedTumblingBucketedWindowConfig, dataItemCount: (dataClass: KClass<*>) -> Int) =
            WindowMetricsConfig(
                listOf(
                    METRICS_OBSERVER_STARTED_NAME,
                    METRICS_OBSERVER_ENDING_NAME,
                    METRICS_OBSERVER_UPDATED_NAME,
                    METRICS_OBSERVER_REMOVING_NAME,
                ),
                listOf(
                    Metrics.gauge(METRICS_METRIC_CONFIG_BUCKET_LENGTH_NAME, mapOf()) {
                        config.bucketLength.toMillis().toDouble()
                    },
                    Metrics.timer(METRICS_METRIC_BUCKET_DURATION_NAME, mapOf()),
                ),
                dataItemCount,
            )
    }

    sealed class Event : GenericSubject.Event() {
        data object CurrentBucketStarted : Event()

        data object CurrentBucketEnding : Event()

        data object NonCurrentBucketsUpdated : Event()

        data object NonCurrentBucketRemoving : Event()
    }

    private val logger = LoggerFactory.getLogger(FixedTumblingBucketedWindow::class.java)
    private val bucketQueue = ConcurrentLinkedQueue<FixedTumblingBucketedWindowBucket>()
    private val bucketsLock = WrappedReentrantReadWriteLock()
    private val currentBucketLock = WrappedReentrantReadWriteLock()
    @Volatile private lateinit var _currentBucket: FixedTumblingBucketedWindowBucket

    override val metricsConfig: WindowMetricsConfig = metricsConfig(config) { dataClass -> dataItemCounts[dataClass]!! }

    val buckets: List<FixedTumblingBucketedWindowBucket>
        get() {
            check(hasStarted) { EXCEPTION_MESSAGE_NOT_STARTED }
            return nonCurrentBuckets + currentBucket
        }

    val currentBucket: FixedTumblingBucketedWindowBucket
        get() {
            check(hasStarted) { EXCEPTION_MESSAGE_NOT_STARTED }
            return currentBucketLock.readLock.withLock { _currentBucket }
        }

    val nonCurrentBuckets: List<FixedTumblingBucketedWindowBucket>
        get() {
            check(hasStarted) { EXCEPTION_MESSAGE_NOT_STARTED }
            return bucketsLock.readLock.withLock {
                bucketQueue.filter { it.status == FixedTumblingBucketedWindowBucket.Status.NON_CURRENT }
            }
        }

    override fun startCore() {
        // Create initial current bucket. Following non-current buckets accrue over time as current buckets end.
        setAndAddCurrentBucket(Instant.now())

        config.metrics.updateObserverDuration(
            METRICS_OBSERVER_STARTED_NAME,
            measureTimeMsDouble { notifyObservers(Event.CurrentBucketStarted, listOf(_currentBucket), false) },
        )
    }

    override fun <T : Any> addDataCore(data: T) {
        @Suppress("UNCHECKED_CAST") (currentBucket.dataForClass(data::class) as BucketData<T>).addNoCheck(data)
    }

    @Synchronized
    private fun currentBucketEnd(bucket: FixedTumblingBucketedWindowBucket) {
        // Should only occur via start() if the configured task scheduler does not execute on a separate thread
        check(hasStarted) { EXCEPTION_MESSAGE_NOT_STARTED }
        require(bucket.status == FixedTumblingBucketedWindowBucket.Status.CURRENT) {
            EXCEPTION_MESSAGE_ENDED_BUCKET_NOT_MARKED_CURRENT
        }

        val startMillis = System.currentTimeMillis()

        config.metrics.updateObserverDuration(
            METRICS_OBSERVER_ENDING_NAME,
            measureTimeMsDouble { notifyObservers(Event.CurrentBucketEnding, listOf(bucket), false) },
        )

        val nonCurrentBucketsSnapshot = currentBucketEndCritical(bucket)

        config.metrics.updateObserverDuration(
            METRICS_OBSERVER_UPDATED_NAME,
            measureTimeMsDouble { notifyObservers(Event.NonCurrentBucketsUpdated, nonCurrentBucketsSnapshot, true) },
        )

        config.metrics.updateTimer(
            config.metrics.renderedMetrics.first { it.name == METRICS_METRIC_BUCKET_DURATION_NAME },
            bucket.endInfo!!.durationMillis.toDouble(),
        )
        if (bucket.endInfo!!.durationMillis > config.bucketLength.toMillis() * 1.1)
            logger.warn(
                "actual bucket duration ({}ms) greater than 110% of intended duration ({}ms): {}"
                    .prefixForWindowLog(config),
                bucket.endInfo!!.durationMillis,
                config.bucketLength.toMillis(),
                bucket,
            )

        config.forDataClasses.forEach { dataClass ->
            dataItemCounts[dataClass] = nonCurrentBucketsSnapshot.sumOf { it.dataForClass(dataClass).size }
        }

        config.metrics.updateMaintenanceDuration((System.currentTimeMillis() - startMillis).toDouble())
    }

    private fun currentBucketEndCritical(
        bucket: FixedTumblingBucketedWindowBucket
    ): List<FixedTumblingBucketedWindowBucket> {
        return bucketsLock.writeLock.withLock {
            currentBucketLock.writeLock.withLock {
                bucket.end()
                setAndAddCurrentBucket(bucket.endInfo!!.actualEnd.plusNanos(1))
            }

            config.metrics.updateObserverDuration(
                METRICS_OBSERVER_STARTED_NAME,
                measureTimeMsDouble { notifyObservers(Event.CurrentBucketStarted, listOf(_currentBucket), false) },
            )

            // Only start removing when we have the desired number of non-current buckets
            if (bucketQueue.size == config.nonCurrentBucketCount + 2) {
                config.metrics.updateObserverDuration(
                    METRICS_OBSERVER_REMOVING_NAME,
                    measureTimeMsDouble {
                        notifyObservers(Event.NonCurrentBucketRemoving, listOf(bucketQueue.peek()), false)
                    },
                )
                bucketQueue.poll()
            }

            maybeLogBuckets("buckets after processing current bucket end".prefixForWindowLog(config))

            // Return a snapshot of non-current buckets
            nonCurrentBuckets
        }
    }

    private fun setAndAddCurrentBucket(start: Instant) {
        _currentBucket = FixedTumblingBucketedWindowBucket(config, start, ::currentBucketEnd)
        bucketsLock.writeLock.withLock { bucketQueue.offer(_currentBucket) }
    }

    private fun maybeLogBuckets(header: String) {
        if (!logger.isDebugEnabled) return
        logger.debug("$header:")
        buckets.forEach { logger.debug(it.toString().prefixForWindowLog(config)) }
    }
}
