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

package io.github.witomlin.timeslidingwindow.windowimpl.fixedtumbling

import io.github.witomlin.timeslidingwindow.Bucket
import io.github.witomlin.timeslidingwindow.BucketType
import java.time.Duration
import java.time.Instant

class FixedTumblingBucketedWindowBucket(
    windowConfig: FixedTumblingBucketedWindowConfig,
    start: Instant,
    onEnd: (FixedTumblingBucketedWindowBucket) -> Unit,
) : Bucket(windowConfig, start) {
    internal companion object {
        const val EXCEPTION_MESSAGE_ALREADY_ENDED = "Already ended"
    }

    enum class Status {
        CURRENT,
        NON_CURRENT,
    }

    data class EndInfo(val actualEnd: Instant, val durationMillis: Int)

    @Volatile private var _endInfo: EndInfo? = null

    val status: Status
        get() =
            when (_endInfo == null) {
                true -> Status.CURRENT
                false -> Status.NON_CURRENT
            }

    val scheduledEnd: Instant = start.plus(windowConfig.bucketLength).minusNanos(1)
    val endInfo: EndInfo?
        get() = _endInfo

    override val type: BucketType = BucketType.TUMBLING
    override val isMutationAllowed: Boolean
        get() = status == Status.CURRENT

    init {
        windowConfig.taskScheduler.scheduleFor(scheduledEnd) { onEnd(this) }
    }

    @Synchronized
    internal fun end() {
        check(status == Status.CURRENT) { EXCEPTION_MESSAGE_ALREADY_ENDED }
        val actualEnd = Instant.now()
        _endInfo = EndInfo(actualEnd, Duration.between(start, actualEnd).toMillis().toInt())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FixedTumblingBucketedWindowBucket) return false
        return start == other.start && scheduledEnd == other.scheduledEnd
    }

    override fun hashCode(): Int = (31 * start.hashCode()) + scheduledEnd.hashCode()

    override fun toString(): String {
        val countsString =
            data.mapValues { (_, data) -> data.size }.entries.joinToString(", ") { "${it.key.simpleName}=${it.value}" }

        return when (status) {
            Status.CURRENT ->
                "FixedTumblingBucketedWindowBucket(" +
                    "status=${Status.CURRENT}, " +
                    "type=${BucketType.TUMBLING}, " +
                    "start=$start, " +
                    "scheduledEnd=$scheduledEnd, " +
                    "isMutationAllowed=$isMutationAllowed, " +
                    "entriesPerDataClass=[$countsString]" +
                    ")"
            Status.NON_CURRENT ->
                "FixedTumblingBucketedWindowBucket(" +
                    "status=${Status.NON_CURRENT}, " +
                    "type=${BucketType.TUMBLING}, " +
                    "start=$start, " +
                    "endInfo=${_endInfo.toString()}, " +
                    "isMutationAllowed=$isMutationAllowed, " +
                    "entriesPerDataClass=[$countsString]" +
                    ")"
        }
    }
}
