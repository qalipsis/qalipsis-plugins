/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.http

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.verify
import io.qalipsis.plugins.http.request.HttpMethod
import io.qalipsis.plugins.http.request.SimpleHttpRequest
import org.apache.hc.core5.http.HttpVersion

/**
 * @author Francisca Eze
 */
object Http2Scenario {

    const val MINIONS = 20

    const val POOLED_MINIONS = 200

    const val REPEAT = 50L

    var httpPort = 0

    private val request = SimpleHttpRequest(HttpMethod.GET, "/").addHeader("Arbitrary", "Header")

    @Scenario("apache-http-2-scenario")
    fun mySimpleScenario() {

        scenario {
            minionsCount = MINIONS
        }
            .start()
            .httpApache()
            .http {
                name = "my-http"
                connect {
                    version = HttpVersion.HTTP_2_0
                    url("https://localhost:${httpPort}/")
                    noDelay = true
                    keepConnectionAlive = true
                    tls {
                        disableCertificateVerification = true
                    }
                    connectionStrategy {
                        strategyType = ConnectionStrategyType.ON_DEMAND
                    }
                }
                request { _, _ -> request }
                iterate(REPEAT)
            }
            .verify {
                assertThat(it.response?.code).isNotNull().isEqualTo(200)
            }
    }

    @Scenario("apache-http-2-pooled-scenario")
    fun myPooledScenario() {

        scenario {
            minionsCount = POOLED_MINIONS
        }
            .start()
            .httpApache()
            .http {
                name = "my-http"
                connect {
                    version = HttpVersion.HTTP_2_0
                    url("https://localhost:${httpPort}/")
                    noDelay = true
                    tls {
                        disableCertificateVerification = true
                    }
                    connectionStrategy {
                        shared = false
                        strategyType = ConnectionStrategyType.POOL
                    }
                }
                request { _, _ -> request }
                iterate(Http1Scenario.REPEAT)
            }.verify {
                assertThat(it.response?.code).isNotNull().isEqualTo(200)
            }
    }
}