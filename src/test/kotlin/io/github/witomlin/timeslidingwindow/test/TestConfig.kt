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

package io.github.witomlin.timeslidingwindow.test

object TestConfig {
    const val COUNT_DOWN_LATCH_AWAIT_SECS = 10
    const val COUNT_DOWN_LATCH_AWAIT_SECS_LONG = COUNT_DOWN_LATCH_AWAIT_SECS.toLong()

    const val TIME_TESTS_PERIOD_MS = 500
    const val TIME_TESTS_PERIOD_MS_LONG = TIME_TESTS_PERIOD_MS.toLong()
    const val TIME_TESTS_PERIOD_MS_DOUBLE = TIME_TESTS_PERIOD_MS.toDouble()

    const val TIME_TESTS_GRACE_MS = 25
    const val TIME_TESTS_GRACE_MS_LONG = TIME_TESTS_GRACE_MS.toLong()
    const val TIME_TESTS_GRACE_MS_DOUBLE = TIME_TESTS_GRACE_MS.toDouble()
}
