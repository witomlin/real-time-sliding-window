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

package io.github.witomlin.timeslidingwindow.core

import io.github.witomlin.timeslidingwindow.test.TestSubject
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class GenericSubjectEventTest :
    BehaviorSpec({
        context("name") {
            given("an event") {
                `when`("accessed") {
                    then("the correct name is returned") { TestSubject.Event.Event1.name.shouldBe("Event1") }
                }
            }
        }
    })
