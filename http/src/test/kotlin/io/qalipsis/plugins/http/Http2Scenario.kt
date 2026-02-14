/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
                assertThat(it.code).isNotNull().isEqualTo(200)
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
                assertThat(it.code).isNotNull().isEqualTo(200)
            }
    }
}