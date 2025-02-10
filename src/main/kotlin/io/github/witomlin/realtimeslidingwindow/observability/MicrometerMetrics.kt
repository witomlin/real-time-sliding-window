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

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag as MicrometerTag
import java.util.concurrent.TimeUnit

class MicrometerMetrics(private val meterRegistry: MeterRegistry) : Metrics() {
    override fun initializeCore() {
        renderedMetrics.forEach { metric ->
            when (metric.type) {
                MetricType.Gauge -> {
                    meterRegistry.gauge(
                        metric.name,
                        metric.tags.map { MicrometerTag.of(it.key, it.value) },
                        metric,
                        (metric as ValueProvidingMetric).valueProvider,
                    )
                }

                MetricType.Timer -> {
                    meterRegistry.timer(metric.name, metric.tags.map { MicrometerTag.of(it.key, it.value) })
                }

                MetricType.DistributionSummary -> {
                    meterRegistry.summary(metric.name, metric.tags.map { MicrometerTag.of(it.key, it.value) })
                }
            }
        }
    }

    override fun updateTimer(metric: Metric, durationMs: Double) {
        meterRegistry
            .timer(metric.name, metric.tags.map { MicrometerTag.of(it.key, it.value) })
            .record(durationMs.toLong(), TimeUnit.MILLISECONDS)
    }

    override fun updateDistributionSummary(metric: Metric, value: Double) {
        meterRegistry.summary(metric.name, metric.tags.map { MicrometerTag.of(it.key, it.value) }).record(value)
    }
}
