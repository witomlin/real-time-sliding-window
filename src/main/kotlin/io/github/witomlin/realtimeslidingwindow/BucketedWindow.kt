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

package io.github.witomlin.realtimeslidingwindow

import io.github.witomlin.realtimeslidingwindow.core.GenericSubject

abstract class BucketedWindow<B : Bucket, E : GenericSubject.Event>(private val config: BucketedWindowConfig) :
    GenericSubject<E, List<B>>(config.taskScheduler) {
    internal companion object {
        const val EXCEPTION_MESSAGE_NOT_STARTED = "Not started"
        const val EXCEPTION_MESSAGE_ALREADY_STARTED = "Already started"
        const val EXCEPTION_MESSAGE_CLASS_NOT_STORED = "Class not stored"
    }

    internal abstract val metricsConfig: WindowMetricsConfig
    @Volatile protected var hasStarted = false

    init {
        WindowName.register(config.name)
    }

    @Synchronized
    fun start() {
        check(!hasStarted) { EXCEPTION_MESSAGE_ALREADY_STARTED }

        config.metrics.initialize(config, metricsConfig)
        startCore()
        hasStarted = true
    }

    protected abstract fun startCore()

    fun <T : Any> addData(data: T) {
        check(hasStarted) { EXCEPTION_MESSAGE_NOT_STARTED }
        require(config.forDataClasses.contains(data::class)) { EXCEPTION_MESSAGE_CLASS_NOT_STORED }

        addDataCore(data)
    }

    protected abstract fun <T : Any> addDataCore(data: T)
}
