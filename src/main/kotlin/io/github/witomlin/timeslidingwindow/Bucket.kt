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

package io.github.witomlin.timeslidingwindow

import java.time.Instant
import kotlin.reflect.KClass

abstract class Bucket(private val windowConfig: BucketedWindowConfig, val start: Instant) {
    internal companion object {
        const val EXCEPTION_MESSAGE_CLASS_NOT_STORED = "Class not stored in bucket"
    }

    abstract val type: BucketType
    abstract val isMutationAllowed: Boolean

    protected val data: Map<KClass<*>, BucketData<*>> =
        windowConfig.forDataClasses.associateWith { BucketData<Any>(this) }

    fun <T : Any> dataForClass(dataClass: KClass<T>): BucketData<T> {
        require(windowConfig.forDataClasses.contains(dataClass)) { EXCEPTION_MESSAGE_CLASS_NOT_STORED }
        @Suppress("UNCHECKED_CAST")
        return data[dataClass] as BucketData<T>
    }
}
