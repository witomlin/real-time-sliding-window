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

package io.github.witomlin.realtimeslidingwindow.test

import io.github.witomlin.realtimeslidingwindow.observability.Metrics

class TestCallbackMetrics(
    private val onInitializeCore: () -> Unit = {},
    private val onUpdateTimer: (metric: Metric, durationMs: Double) -> Unit = { _, _ -> },
) : Metrics() {
    override fun initializeCore() {
        onInitializeCore()
    }

    override fun updateTimer(metric: Metric, durationMs: Double) {
        onUpdateTimer(metric, durationMs)
    }
}
