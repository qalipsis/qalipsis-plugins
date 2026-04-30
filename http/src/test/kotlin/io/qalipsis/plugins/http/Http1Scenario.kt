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
object Http1Scenario {

    const val MINIONS = 20

    const val POOLED_MINIONS = 200

    const val REPEAT = 50L

    var httpPort = 0

    private val request = SimpleHttpRequest(HttpMethod.GET, "/").addHeader("Arbitrary", "Header")

    @Scenario("apache-http-1-scenario")
    fun simpleScenario() {
        scenario {
            minionsCount = MINIONS
        }
            .start()
            .httpApache()
            .http {
                name = "my-http"
                connect {
                    version = HttpVersion.HTTP_1_0
                    url("http://localhost:${httpPort}/")
                    noDelay = true
                    keepConnectionAlive = true
                    connectionStrategy {
                        strategyType = ConnectionStrategyType.ON_DEMAND
                    }
                }
                request { _, _ -> request }
                iterate(REPEAT)
            }.verify {
                assertThat(it.response?.code).isNotNull().isEqualTo(200)
            }
    }

    @Scenario("apache-http-1-pooled-scenario")
    fun myPooledScenario() {

        scenario {
            minionsCount = POOLED_MINIONS
        }
            .start()
            .httpApache()
            .http {
                name = "my-http"
                connect {
                    url("http://localhost:${httpPort}/")
                    noDelay = true
                    connectionStrategy {
                        shared = false
                        strategyType = ConnectionStrategyType.POOL
                    }
                }
                request { _, _ -> request }
                iterate(REPEAT)
            }.verify {
                assertThat(it.response?.code).isNotNull().isEqualTo(200)
            }
    }
}