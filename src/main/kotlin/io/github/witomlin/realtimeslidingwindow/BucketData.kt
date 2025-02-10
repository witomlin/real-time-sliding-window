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

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class BucketData<T>(private val bucket: Bucket) {
    internal companion object {
        const val EXCEPTION_MESSAGE_MUTATIONS_NO_LONGER_ALLOWED = "Mutations no longer allowed"
    }

    data class TimestampedData<T>(val timestamp: Instant, val data: T)

    private val _entries = ConcurrentLinkedQueue<TimestampedData<T>>()
    private val _size = AtomicInteger(0) // _entries.size is O(n), so provide O(1) means

    val entries: List<TimestampedData<T>>
        get() = _entries.toList()

    val size: Int
        get() = _size.get()

    val firstEntryTimestamp: Instant?
        get() = _entries.firstOrNull()?.timestamp

    val lastEntryTimestamp: Instant?
        get() = _entries.lastOrNull()?.timestamp

    val isMutationAllowed: Boolean
        get() = bucket.isMutationAllowed

    fun add(data: T) {
        check(isMutationAllowed) { EXCEPTION_MESSAGE_MUTATIONS_NO_LONGER_ALLOWED }
        _entries.offer(TimestampedData(Instant.now(), data))
        _size.incrementAndGet()
    }

    internal fun addNoCheck(data: T) {
        _entries.offer(TimestampedData(Instant.now(), data))
        _size.incrementAndGet()
    }

    internal fun addAll(data: List<TimestampedData<*>>) {
        @Suppress("UNCHECKED_CAST") _entries.addAll(data as List<TimestampedData<T>>)
        _size.addAndGet(data.size)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BucketData<*>) return false
        return _entries == other._entries // Referential
    }

    override fun hashCode(): Int = _entries.hashCode()
}
