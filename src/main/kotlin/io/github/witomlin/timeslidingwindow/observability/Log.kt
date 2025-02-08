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

package io.github.witomlin.timeslidingwindow.observability

import io.github.witomlin.timeslidingwindow.BucketedWindowConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory as Slf4jLoggerFactory

internal object LoggerFactory {
    fun <T : Any> getLogger(clazz: Class<T>): Logger = Slf4jLoggerFactory.getLogger(clazz)
}

internal fun String.prefixForWindowLog(config: BucketedWindowConfig): String {
    return "${config.name}: $this"
}
