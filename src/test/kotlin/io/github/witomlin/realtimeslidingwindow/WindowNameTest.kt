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

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class WindowNameTest :
    BehaviorSpec({
        val windowName = "test"

        beforeTest { WindowName.unregister(windowName) }

        context("register") {
            given("a name that is already registered") {
                `when`("invoked") {
                    then("an exception is thrown") {
                        WindowName.register(windowName)
                        shouldThrowExactly<IllegalStateException> { WindowName.register(windowName) }
                            .message
                            .shouldBe(WindowName.EXCEPTION_MESSAGE_NAME_ALREADY_REGISTERED)
                    }
                }
            }

            given("a name that is not registered") {
                `when`("invoked") {
                    then("the name is added to the list") {
                        WindowName.register(windowName)
                        WindowName.windowNames.contains(windowName).shouldBeTrue()
                    }
                }
            }
        }

        context("unregister") {
            given("a name") {
                `when`("invoked") {
                    then("the name is removed from the list") {
                        WindowName.register(windowName)
                        WindowName.unregister(windowName)
                        WindowName.windowNames.contains(windowName).shouldBeFalse()
                    }
                }
            }
        }
    })
