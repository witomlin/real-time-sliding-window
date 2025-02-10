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

import io.github.witomlin.realtimeslidingwindow.BucketedWindowConfig
import io.github.witomlin.realtimeslidingwindow.WindowMetricsConfig

abstract class Metrics {
    companion object {
        internal const val METRIC_CONFIG_LENGTH_NAME = "window.config.length_ms"
        internal const val METRIC_MAINTENANCE_DURATION_NAME = "window.maintenance.duration"
        internal const val METRIC_OBSERVER_DURATION_NAME = "window.observer.duration"
        internal const val METRIC_DATA_ITEMS_NAME = "window.data.items"

        internal const val TAG_WINDOW_NAME_NAME = "window_name"
        internal const val TAG_OBSERVER_DURATION_EVENT_NAME = "event"
        internal const val TAG_DATA_ITEM_COUNT_CLASS_NAME = "class"

        internal const val EXCEPTION_MESSAGE_ALREADY_INITIALIZED = "Already initialized"
        internal const val EXCEPTION_MESSAGE_NOT_INITIALIZED = "Not initialized"
        internal const val EXCEPTION_MESSAGE_UNKNOWN_OBSERVER_EVENT = "Unknown observer event"

        fun gauge(name: String, tags: Map<String, String>, valueProvider: (_: Any) -> Double): Metric {
            return ValueProvidingMetric(MetricType.Gauge, name, tags.toMutableMap(), valueProvider)
        }

        fun timer(name: String, tags: Map<String, String>): Metric {
            return Metric(MetricType.Timer, name, tags.toMutableMap())
        }
    }

    sealed class MetricType {
        data object Gauge : MetricType()

        data object Timer : MetricType()

        data object DistributionSummary : MetricType()
    }

    open class Metric(val type: MetricType, val name: String, val tags: MutableMap<String, String>)

    class ValueProvidingMetric(
        type: MetricType,
        name: String,
        tags: MutableMap<String, String>,
        val valueProvider: (_: Any) -> Double,
    ) : Metric(type, name, tags)

    private lateinit var windowConfig: BucketedWindowConfig
    private lateinit var metricsConfig: WindowMetricsConfig
    private lateinit var _renderedMetrics: List<Metric>
    @Volatile private var hasInitialized = false

    val renderedMetrics: List<Metric>
        get() {
            check(hasInitialized) { EXCEPTION_MESSAGE_NOT_INITIALIZED }
            return _renderedMetrics
        }

    @Synchronized
    fun initialize(windowConfig: BucketedWindowConfig, metricsConfig: WindowMetricsConfig) {
        check(!hasInitialized) { EXCEPTION_MESSAGE_ALREADY_INITIALIZED }

        this.windowConfig = windowConfig
        this.metricsConfig = metricsConfig
        _renderedMetrics = renderMetrics()
        hasInitialized = true

        initializeCore()
    }

    protected abstract fun initializeCore()

    fun updateMaintenanceDuration(durationMs: Double) {
        check(hasInitialized) { EXCEPTION_MESSAGE_NOT_INITIALIZED }

        val metric = _renderedMetrics.first { it.name == METRIC_MAINTENANCE_DURATION_NAME }
        updateTimer(metric, durationMs)
    }

    fun updateObserverDuration(observerEvent: String, durationMs: Double) {
        check(hasInitialized) { EXCEPTION_MESSAGE_NOT_INITIALIZED }
        require(metricsConfig.observerEventNames.contains(observerEvent)) { EXCEPTION_MESSAGE_UNKNOWN_OBSERVER_EVENT }

        val metric =
            _renderedMetrics.first {
                it.name == METRIC_OBSERVER_DURATION_NAME && it.tags[TAG_OBSERVER_DURATION_EVENT_NAME] == observerEvent
            }
        updateTimer(metric, durationMs)
    }

    abstract fun updateTimer(metric: Metric, durationMs: Double)

    private fun renderMetrics(): List<Metric> {
        val commonTags = mapOf(TAG_WINDOW_NAME_NAME to windowConfig.name)

        val observerTimers =
            metricsConfig.observerEventNames
                .map { timer(METRIC_OBSERVER_DURATION_NAME, mapOf(TAG_OBSERVER_DURATION_EVENT_NAME to it)) }
                .onEach { it.tags.putAll(commonTags) }

        val dataItemCountGauges =
            windowConfig.forDataClasses
                .map { dataClass ->
                    gauge(METRIC_DATA_ITEMS_NAME, mapOf(TAG_DATA_ITEM_COUNT_CLASS_NAME to dataClass.simpleName!!)) {
                        metricsConfig.dataItemCount(dataClass).toDouble()
                    }
                }
                .onEach { it.tags.putAll(commonTags) }

        val standardMetrics =
            listOf(
                    listOf(gauge(METRIC_CONFIG_LENGTH_NAME, commonTags) { windowConfig.length.toMillis().toDouble() }),
                    listOf(timer(METRIC_MAINTENANCE_DURATION_NAME, commonTags)),
                    observerTimers,
                    dataItemCountGauges,
                )
                .flatten()

        metricsConfig.extraMetrics.forEach { it.tags.putAll(commonTags) }

        return standardMetrics + metricsConfig.extraMetrics
    }
}
