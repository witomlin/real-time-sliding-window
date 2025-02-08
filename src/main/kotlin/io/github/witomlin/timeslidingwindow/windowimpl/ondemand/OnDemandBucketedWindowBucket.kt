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

import io.github.witomlin.timeslidingwindow.Bucket
import io.github.witomlin.timeslidingwindow.BucketData
import io.github.witomlin.timeslidingwindow.BucketType
import java.time.Instant
import kotlin.reflect.KClass

class OnDemandBucketedWindowBucket(
    windowConfig: OnDemandBucketedWindowConfig,
    override val type: BucketType,
    start: Instant,
    val end: Instant,
    data: Map<KClass<*>, List<BucketData.TimestampedData<*>>>,
) : Bucket(windowConfig, start) {
    override val isMutationAllowed: Boolean = false

    init {
        data.forEach { require(windowConfig.forDataClasses.contains(it.key)) { EXCEPTION_MESSAGE_CLASS_NOT_STORED } }

        data.forEach { mapEntry -> this.data[mapEntry.key]!!.addAll(mapEntry.value) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OnDemandBucketedWindowBucket) return false
        return start == other.start && end == other.end
    }

    override fun hashCode(): Int = (31 * start.hashCode()) + end.hashCode()

    override fun toString(): String {
        val countsString =
            data.mapValues { (_, data) -> data.size }.entries.joinToString(", ") { "${it.key.simpleName}=${it.value}" }

        return "OnDemandBucketedWindowBucket(" +
            "type=$type, " +
            "start=$start, " +
            "end=$end, " +
            "entriesPerDataClass=[$countsString]" +
            ")"
    }
}
