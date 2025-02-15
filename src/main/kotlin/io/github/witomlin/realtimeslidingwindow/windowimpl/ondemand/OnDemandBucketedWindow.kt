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

import io.github.witomlin.realtimeslidingwindow.*
import io.github.witomlin.realtimeslidingwindow.core.GenericSubject
import io.github.witomlin.realtimeslidingwindow.observability.LoggerFactory
import io.github.witomlin.realtimeslidingwindow.observability.Metrics
import io.github.witomlin.realtimeslidingwindow.observability.prefixForWindowLog
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.reflect.KClass

class OnDemandBucketedWindow(private val config: OnDemandBucketedWindowConfig) :
    BucketedWindow<OnDemandBucketedWindowBucket, OnDemandBucketedWindow.Event>(config) {
    internal companion object {
        const val METRICS_METRIC_VIEW_TUMBLING_DURATION_NAME = "window.view.tumbling.duration"

        const val EXCEPTION_MESSAGE_ODTB_LENGTH_INSUFFICIENT = "'length' must be > 0ms"
        const val EXCEPTION_MESSAGE_ODTB_BUCKET_INSUFFICIENT = "'bucket' must be > 0ms"
        const val EXCEPTION_MESSAGE_ODTB_START_IN_FUTURE = "'start' must not be in the future"
        const val EXCEPTION_MESSAGE_ODTB_START_TOO_EARLY = "'start' must be later than the start of the window"
        const val EXCEPTION_MESSAGE_ODTB_START_LENGTH_IN_FUTURE = "'start' + 'length' must not be in the future"
        const val EXCEPTION_MESSAGE_ODTB_LENGTH_LESS_THAN_BUCKET = "'length' must be >= 'bucket'"
        const val EXCEPTION_MESSAGE_ODTB_LENGTH_MULTIPLE = "'length' must be an exact multiple of 'bucket'"

        fun metricsConfig(dataItemCount: (dataClass: KClass<*>) -> Int) =
            WindowMetricsConfig(
                listOf(),
                listOf(Metrics.timer(METRICS_METRIC_VIEW_TUMBLING_DURATION_NAME, mapOf())),
                dataItemCount,
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

    override val metricsConfig: WindowMetricsConfig = metricsConfig { dataClass -> dataItemCounts[dataClass]!! }

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

        require(computedLength > Duration.ofMillis(0)) { EXCEPTION_MESSAGE_ODTB_LENGTH_INSUFFICIENT }
        require(computedBucketLength > Duration.ofMillis(0)) { EXCEPTION_MESSAGE_ODTB_BUCKET_INSUFFICIENT }
        require(!computedStart.isAfter(now)) { EXCEPTION_MESSAGE_ODTB_START_IN_FUTURE }
        require(!computedStart.isBefore(now.minus(config.length))) { EXCEPTION_MESSAGE_ODTB_START_TOO_EARLY }
        require(!computedStart.plus(computedLength).isAfter(now)) { EXCEPTION_MESSAGE_ODTB_START_LENGTH_IN_FUTURE }
        require(computedBucketLength <= computedLength) { EXCEPTION_MESSAGE_ODTB_LENGTH_LESS_THAN_BUCKET }
        require(computedLength.toMillis() % computedBucketLength.toMillis() == 0L) {
            EXCEPTION_MESSAGE_ODTB_LENGTH_MULTIPLE
        }

        val startMillis = System.currentTimeMillis()

        val startPlusLength = computedStart.plus(computedLength)
        val dataSnapshot = data.mapValues { (_, set) -> set.toList() }
        val filteredData =
            dataSnapshot.mapValues { mapEntry ->
                filterListForTimestampRange(mapEntry.value, computedStart, startPlusLength)
            }

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
                    filteredData.mapValues { (_, list) -> filterListForTimestampRange(list, bucketStart, bucketEnd) },
                )
            )

            bucketStart = bucketEnd.plusNanos(1)
            bucketEnd = bucketStart.plus(computedBucketLength).minusNanos(1)
        }

        config.metrics.updateTimer(
            config.metrics.renderedMetrics.first { it.name == METRICS_METRIC_VIEW_TUMBLING_DURATION_NAME },
            (System.currentTimeMillis() - startMillis).toDouble(),
        )
        return buckets
    }

    private fun filterListForTimestampRange(
        list: List<BucketData.TimestampedData<*>>,
        fromInclusive: Instant,
        toInclusive: Instant,
    ): List<BucketData.TimestampedData<*>> {
        if (list.isEmpty()) return listOf()

        var lowerIndex: Int? = null
        var upperIndex: Int? = null

        for ((index, data) in list.withIndex()) {
            if (data.timestamp == fromInclusive || data.timestamp.isAfter(fromInclusive)) {
                lowerIndex = index
                break
            }
        }

        if (lowerIndex == null) return listOf()

        for (index in list.indices.reversed()) {
            val data = list[index]
            if (data.timestamp == toInclusive || data.timestamp.isBefore(toInclusive)) {
                upperIndex = index
                break
            }
        }

        return list.subList(lowerIndex, (upperIndex ?: (list.size - 1)) + 1)
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

        config.forDataClasses.forEach { dataClass -> dataItemCounts[dataClass] = data[dataClass]!!.size }

        config.metrics.updateMaintenanceDuration((System.currentTimeMillis() - startMillis).toDouble())
    }
}
