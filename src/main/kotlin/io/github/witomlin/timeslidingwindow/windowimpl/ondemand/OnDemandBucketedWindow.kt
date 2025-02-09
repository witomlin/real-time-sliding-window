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

import io.github.witomlin.timeslidingwindow.*
import io.github.witomlin.timeslidingwindow.core.GenericSubject
import io.github.witomlin.timeslidingwindow.observability.LoggerFactory
import io.github.witomlin.timeslidingwindow.observability.Metrics
import io.github.witomlin.timeslidingwindow.observability.prefixForWindowLog
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentSkipListSet

class OnDemandBucketedWindow(private val config: OnDemandBucketedWindowConfig) :
    BucketedWindow<OnDemandBucketedWindowBucket, OnDemandBucketedWindow.Event>(config) {
    internal companion object {
        const val METRICS_METRIC_ON_DEMAND_TUMBLING_DURATION_NAME = "window_on_demand_tumbling_buckets_duration_ms"

        const val EXCEPTION_MESSAGE_ODB_LENGTH_INSUFFICIENT = "'length' must be > 0ms"
        const val EXCEPTION_MESSAGE_ODB_BUCKET_INSUFFICIENT = "'bucket' must be > 0ms"
        const val EXCEPTION_MESSAGE_ODB_START_IN_FUTURE = "'start' must not be in the future"
        const val EXCEPTION_MESSAGE_ODB_START_TOO_EARLY = "'start' must be later than the start of the window"
        const val EXCEPTION_MESSAGE_ODB_START_LENGTH_IN_FUTURE = "'start' + 'length' must not be in the future"
        const val EXCEPTION_MESSAGE_ODB_LENGTH_LESS_THAN_BUCKET = "'length' must be >= 'bucket'"
        const val EXCEPTION_MESSAGE_ODB_LENGTH_MULTIPLE = "'length' must be an exact multiple of 'bucket'"

        fun metricsConfig() =
            WindowMetricsConfig(
                listOf(),
                listOf(Metrics.timer(METRICS_METRIC_ON_DEMAND_TUMBLING_DURATION_NAME, mapOf())),
            )
    }

    sealed class Event : GenericSubject.Event()

    private val logger = LoggerFactory.getLogger(OnDemandBucketedWindow::class.java)
    private val data =
        config.forDataClasses.associateWith {
            ConcurrentSkipListSet(
                compareBy<BucketData.TimestampedData<*>> { it.timestamp }.thenBy { it.data.hashCode() }
            )
        }

    override val metricsConfig: WindowMetricsConfig = metricsConfig()

    override fun startCore() {
        config.taskScheduler.scheduleEvery(config.maintenanceInterval, ::dataMaintenance)
        logger.debug(
            "maintenance scheduled for every ${config.maintenanceInterval.toMillis()}ms".prefixForWindowLog(config)
        )
    }

    override fun <T : Any> addDataCore(data: T) {
        this.data[data::class]!!.add(BucketData.TimestampedData(Instant.now(), data))
    }

    fun onDemandTumblingBuckets(
        start: Instant? = null,
        length: Duration? = null,
        bucketLength: Duration? = null,
    ): List<OnDemandBucketedWindowBucket> {
        check(hasStarted) { EXCEPTION_MESSAGE_NOT_STARTED }

        val now = Instant.now()
        val computedStart = start ?: now.minus(config.length)
        val computedLength = length ?: config.length
        val computedBucketLength = bucketLength ?: config.length

        require(computedLength > Duration.ofMillis(0)) { EXCEPTION_MESSAGE_ODB_LENGTH_INSUFFICIENT }
        require(computedBucketLength > Duration.ofMillis(0)) { EXCEPTION_MESSAGE_ODB_BUCKET_INSUFFICIENT }
        require(!computedStart.isAfter(now)) { EXCEPTION_MESSAGE_ODB_START_IN_FUTURE }
        require(!computedStart.isBefore(now.minus(config.length))) { EXCEPTION_MESSAGE_ODB_START_TOO_EARLY }
        require(!computedStart.plus(computedLength).isAfter(now)) { EXCEPTION_MESSAGE_ODB_START_LENGTH_IN_FUTURE }
        require(computedBucketLength <= computedLength) { EXCEPTION_MESSAGE_ODB_LENGTH_LESS_THAN_BUCKET }
        require(computedLength.toMillis() % computedBucketLength.toMillis() == 0L) {
            EXCEPTION_MESSAGE_ODB_LENGTH_MULTIPLE
        }

        val startMillis = System.currentTimeMillis()

        val startPlusLength = computedStart.plus(computedLength)
        val filteredData = data.mapValues { (_, set) -> set.filter { it.timestamp in computedStart..startPlusLength } }

        val buckets = mutableListOf<OnDemandBucketedWindowBucket>()
        var bucketStart = computedStart
        var bucketEnd = computedStart.plus(computedBucketLength).minusNanos(1)

        repeat(computedLength.dividedBy(computedBucketLength).toInt()) {
            buckets.add(
                OnDemandBucketedWindowBucket(
                    config,
                    BucketType.TUMBLING,
                    bucketStart,
                    bucketEnd,
                    filteredData.mapValues { (_, list) -> list.filter { it.timestamp in bucketStart..bucketEnd } },
                )
            )

            bucketStart = bucketEnd.plusNanos(1)
            bucketEnd = bucketStart.plus(computedBucketLength).minusNanos(1)
        }

        config.metrics.updateTimer(
            config.metrics.renderedMetrics.first { it.name == METRICS_METRIC_ON_DEMAND_TUMBLING_DURATION_NAME },
            (System.currentTimeMillis() - startMillis).toDouble(),
        )
        return buckets
    }

    @Synchronized
    private fun dataMaintenance() {
        val startMillis = System.currentTimeMillis()
        val removedEntries = config.forDataClasses.associateWithTo(mutableMapOf()) { 0 }
        val cutOff = Instant.now().minus(config.length)

        data.forEach { mapEntry ->
            val set = mapEntry.value

            if (set.isNotEmpty()) {
                val iterator = set.iterator()

                while (iterator.hasNext()) {
                    if (iterator.next().timestamp.isBefore(cutOff)) {
                        iterator.remove()
                        removedEntries[mapEntry.key] = removedEntries[mapEntry.key]!! + 1
                    } else {
                        // Set timestamp ordering is guaranteed, so we can break early
                        break
                    }
                }
            }
        }

        val removedEntriesMessage =
            removedEntries.entries.joinToString(", ") { (clazz, count) -> "${clazz.simpleName}=$count" }
        logger.debug("expired entries removed: $removedEntriesMessage".prefixForWindowLog(config))

        config.forDataClasses.forEach { dataClass ->
            config.metrics.updateDataItemCount(dataClass, data[dataClass]!!.size)
        }

        config.metrics.updateMaintenanceDuration((System.currentTimeMillis() - startMillis).toDouble())
    }
}
