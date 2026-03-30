package io.qalipsis.plugins.http

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.http.configuration.defaults
import io.qalipsis.plugins.http.connection.TlsConfiguration
import io.qalipsis.plugins.http.request.HttpMethod
import io.qalipsis.plugins.http.request.HttpRequest
import io.qalipsis.plugins.http.request.HttpRequestBuilder
import io.qalipsis.plugins.http.request.SimpleHttpRequest
import java.time.Duration
import org.apache.hc.core5.http.HttpVersion
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * @author Francisca Eze
 */
internal class HttpClientStepSpecificationImplTest {

    @Nested
    internal inner class `Standard HTTP step` {

        @Test
        internal fun `should add minimal http step as next`() {
            val previousStep = DummyStepSpecification()
            val requestSpecification: suspend HttpRequestBuilder.(ctx: StepContext<*, *>, input: Int) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            previousStep.httpApache().http {
                request(requestSpecification)
                connect {
                    url("http://localhost:12234")
                }
            }

            assertThat(previousStep.nextSteps[0]).isInstanceOf(HttpClientStepSpecificationImpl::class).all {
                prop(HttpClientStepSpecificationImpl<*, *>::requestFactory).isSameInstanceAs(requestSpecification)
                prop(HttpClientStepSpecificationImpl<*, *>::bodyType).isEqualTo(String::class)
                prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                    prop(HttpClientConfiguration::version).isEqualTo(HttpVersion.HTTP_1_1)
                    prop(HttpClientConfiguration::host).isEqualTo("localhost")
                    prop(HttpClientConfiguration::port).isEqualTo(12234)
                    prop(HttpClientConfiguration::scheme).isEqualTo("http")
                    prop(HttpClientConfiguration::contextPath).isEqualTo("")
                    prop(HttpClientConfiguration::connectTimeout).isEqualTo(Duration.ofSeconds(10))
                    prop(HttpClientConfiguration::noDelay).isTrue()
                    prop(HttpClientConfiguration::keepConnectionAlive).isTrue()
                    prop(HttpClientConfiguration::charset).isEqualTo(Charsets.UTF_8)
                    prop(HttpClientConfiguration::maxContentLength).isEqualTo(1048576)
                    prop(HttpClientConfiguration::inflate).isFalse()
                    prop(HttpClientConfiguration::followRedirections).isFalse()
                    prop(HttpClientConfiguration::maxRedirections).isEqualTo(10)
                    prop(HttpClientConfiguration::tlsConfiguration).isNull()
                    prop(HttpClientConfiguration::connectionStrategyConfiguration).all {
                        prop(ConnectionStrategyConfiguration::shared).isFalse()
                        prop(ConnectionStrategyConfiguration::strategyType).isEqualTo(ConnectionStrategyType.ON_DEMAND)
                    }
                }
                prop(HttpClientStepSpecificationImpl<*, *>::monitoringConfig).all {
                    prop(StepMonitoringConfiguration::events).isTrue()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
        }

        @Test
        internal fun `should add http step with pool as next using addresses as string and int`() {
            val previousStep = DummyStepSpecification()
            val requestSpecification: suspend HttpRequestBuilder.(ctx: StepContext<*, *>, input: Int) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            previousStep.httpApache().http {
                request(requestSpecification)
                connect {
                    url("https://localhost:12234/test")
                    connectTimeout = Duration.ofMinutes(1)
                    inflate()
                    charset(Charsets.ISO_8859_1)
                    maxContentLength(123)
                    followRedirections(15)

                    noDelay = false
                    keepConnectionAlive = false

                    tls {
                        disableCertificateVerification = true
                    }

                    proxy {
                        type = HttpProxyType.SOCKS5
                        address("my-proxy", 9876)
                    }
                }
            }

            assertThat(previousStep.nextSteps[0]).isInstanceOf(HttpClientStepSpecificationImpl::class).all {
                prop(HttpClientStepSpecificationImpl<*, *>::requestFactory).isSameInstanceAs(requestSpecification)
                prop(HttpClientStepSpecificationImpl<*, *>::bodyType).isEqualTo(String::class)
                prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                    prop(HttpClientConfiguration::version).isEqualTo(HttpVersion.HTTP_1_1)
                    prop(HttpClientConfiguration::host).isEqualTo("localhost")
                    prop(HttpClientConfiguration::port).isEqualTo(12234)
                    prop(HttpClientConfiguration::scheme).isEqualTo("https")
                    prop(HttpClientConfiguration::contextPath).isEqualTo("/test")
                    prop(HttpClientConfiguration::connectTimeout).isEqualTo(Duration.ofMinutes(1))
                    prop(HttpClientConfiguration::noDelay).isFalse()
                    prop(HttpClientConfiguration::keepConnectionAlive).isFalse()
                    prop(HttpClientConfiguration::charset).isEqualTo(Charsets.ISO_8859_1)
                    prop(HttpClientConfiguration::maxContentLength).isEqualTo(123)
                    prop(HttpClientConfiguration::inflate).isTrue()
                    prop(HttpClientConfiguration::followRedirections).isTrue()
                    prop(HttpClientConfiguration::maxRedirections).isEqualTo(15)
                    prop(HttpClientConfiguration::tlsConfiguration).isNotNull().all {
                        prop(TlsConfiguration::disableCertificateVerification).isTrue()
                    }
                    prop(HttpClientConfiguration::proxyConfiguration).isNotNull().all {
                        prop(HttpProxyConfiguration::type).isEqualTo(HttpProxyType.SOCKS5)
                        prop(HttpProxyConfiguration::host).isEqualTo("my-proxy")
                        prop(HttpProxyConfiguration::port).isEqualTo(9876)
                    }
                }
                prop(HttpClientStepSpecificationImpl<*, *>::monitoringConfig).all {
                    prop(StepMonitoringConfiguration::events).isTrue()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
        }

        @Test
        internal fun `should add http step to scenario`() {
            val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
            val requestSpecification: suspend HttpRequestBuilder.(ctx: StepContext<*, *>, input: Unit) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head").withBasicAuth("foo", "bar") }
            scenario.httpApache().http {
                request(requestSpecification)
                connect {
                    url("http://localhost:12234")
                }
            }.deserialize(Entity::class)

            assertThat(scenario.rootSteps[0]).isInstanceOf(HttpClientStepSpecificationImpl::class).all {
                prop(HttpClientStepSpecificationImpl<*, *>::requestFactory).isSameInstanceAs(requestSpecification)
                prop(HttpClientStepSpecificationImpl<*, *>::bodyType).isEqualTo(Entity::class)
                prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                    prop(HttpClientConfiguration::version).isEqualTo(HttpVersion.HTTP_1_1)
                    prop(HttpClientConfiguration::host).isEqualTo("localhost")
                    prop(HttpClientConfiguration::port).isEqualTo(12234)
                    prop(HttpClientConfiguration::scheme).isEqualTo("http")
                    prop(HttpClientConfiguration::contextPath).isEqualTo("")
                    prop(HttpClientConfiguration::connectTimeout).isEqualTo(Duration.ofSeconds(10))
                    prop(HttpClientConfiguration::noDelay).isTrue()
                    prop(HttpClientConfiguration::keepConnectionAlive).isTrue()
                    prop(HttpClientConfiguration::charset).isEqualTo(Charsets.UTF_8)
                    prop(HttpClientConfiguration::maxContentLength).isEqualTo(1048576)
                    prop(HttpClientConfiguration::inflate).isFalse()
                    prop(HttpClientConfiguration::followRedirections).isFalse()
                    prop(HttpClientConfiguration::maxRedirections).isEqualTo(10)
                    prop(HttpClientConfiguration::tlsConfiguration).isNull()
                    prop(HttpClientConfiguration::proxyConfiguration).isNull()
                }
                prop(HttpClientStepSpecificationImpl<*, *>::monitoringConfig).all {
                    prop(StepMonitoringConfiguration::events).isTrue()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
        }
    }

    @Test
    internal fun `should add the right http headers to the step specification`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        val requestSpecification: suspend HttpRequestBuilder.(ctx: StepContext<*, *>, input: Unit) -> HttpRequest<*> =
            { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head").withBasicAuth("foo", "bar") }
        scenario.httpApache().http {
            request(requestSpecification)
            connect {
                url("http://localhost:12234")
            }
        }.deserialize(Entity::class)

        assertThat(scenario.rootSteps[0]).isInstanceOf(HttpClientStepSpecificationImpl::class).all {
            prop(HttpClientStepSpecificationImpl<*, *>::requestFactory).isEqualTo(requestSpecification)
            prop(HttpClientStepSpecificationImpl<*, *>::bodyType).isEqualTo(Entity::class)
            prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                prop(HttpClientConfiguration::version).isEqualTo(HttpVersion.HTTP_1_1)
                prop(HttpClientConfiguration::host).isEqualTo("localhost")
                prop(HttpClientConfiguration::port).isEqualTo(12234)
                prop(HttpClientConfiguration::scheme).isEqualTo("http")
                prop(HttpClientConfiguration::contextPath).isEqualTo("")
                prop(HttpClientConfiguration::connectTimeout).isEqualTo(Duration.ofSeconds(10))
                prop(HttpClientConfiguration::noDelay).isTrue()
                prop(HttpClientConfiguration::keepConnectionAlive).isTrue()
                prop(HttpClientConfiguration::charset).isEqualTo(Charsets.UTF_8)
                prop(HttpClientConfiguration::maxContentLength).isEqualTo(1048576)
                prop(HttpClientConfiguration::inflate).isFalse()
                prop(HttpClientConfiguration::followRedirections).isFalse()
                prop(HttpClientConfiguration::maxRedirections).isEqualTo(10)
                prop(HttpClientConfiguration::tlsConfiguration).isNull()
                prop(HttpClientConfiguration::proxyConfiguration).isNull()
            }
            prop(HttpClientStepSpecificationImpl<*, *>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Nested
    internal inner class `Defaults extension` {

        @Test
        internal fun `should apply defaults from HttpApacheDefaultsExtension`() {
            val scenario = TestScenarioFactory.scenario("my-scenario", {
                httpApache().defaults {
                    connect {
                        url("https://localhost:8443/api")
                        version = HttpVersion.HTTP_2
                        noDelay = false
                        keepConnectionAlive = false
                    }
                    monitoring {
                        events = true
                        meters = true
                    }
                }
            }) as StepSpecificationRegistry

            val requestSpecification: suspend HttpRequestBuilder.(ctx: StepContext<*, *>, input: Unit) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/test") }

            scenario.httpApache().http {
                request(requestSpecification)
            }

            assertThat(scenario.rootSteps[0]).isInstanceOf(HttpClientStepSpecificationImpl::class).all {
                prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                    prop(HttpClientConfiguration::host).isEqualTo("localhost")
                    prop(HttpClientConfiguration::port).isEqualTo(8443)
                    prop(HttpClientConfiguration::scheme).isEqualTo("https")
                    prop(HttpClientConfiguration::contextPath).isEqualTo("/api")
                    prop(HttpClientConfiguration::version).isEqualTo(HttpVersion.HTTP_2)
                    prop(HttpClientConfiguration::noDelay).isFalse()
                    prop(HttpClientConfiguration::keepConnectionAlive).isFalse()
                }
                prop(HttpClientStepSpecificationImpl<*, *>::monitoringConfig).all {
                    prop(StepMonitoringConfiguration::events).isTrue()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
        }

        @Test
        internal fun `should allow overriding defaults from HttpApacheDefaultsExtension`() {
            val scenario = TestScenarioFactory.scenario("my-scenario", {
                httpApache().defaults {
                    connect {
                        url("https://localhost:8443/api")
                        version = HttpVersion.HTTP_2
                        noDelay = false
                    }
                    monitoring {
                        events = true
                        meters = true
                    }
                }
            }) as StepSpecificationRegistry

            val requestSpecification: suspend HttpRequestBuilder.(ctx: StepContext<*, *>, input: Unit) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/test") }

            scenario.httpApache().http {
                request(requestSpecification)
                connect {
                    url("http://localhost:9090")
                    // version not set -> inherits HTTP_2 from defaults
                    // noDelay not set -> inherits false from defaults
                }
                monitoring {
                    events = false
                    // meters not set -> inherits true from defaults
                }
            }

            assertThat(scenario.rootSteps[0]).isInstanceOf(HttpClientStepSpecificationImpl::class).all {
                prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                    prop(HttpClientConfiguration::host).isEqualTo("localhost")
                    prop(HttpClientConfiguration::port).isEqualTo(9090)
                    prop(HttpClientConfiguration::scheme).isEqualTo("http")
                    prop(HttpClientConfiguration::version).isEqualTo(HttpVersion.HTTP_2)
                    prop(HttpClientConfiguration::noDelay).isFalse()
                }
                prop(HttpClientStepSpecificationImpl<*, *>::monitoringConfig).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
        }

        @Test
        internal fun `should allow overriding defaults from defaults step then from step config`() {
            val scenario = TestScenarioFactory.scenario("my-scenario", {
                httpApache().defaults {
                    connect {
                        url("https://localhost:8443/api")
                        version = HttpVersion.HTTP_2
                        noDelay = false
                    }
                    monitoring {
                        events = true
                        meters = true
                    }
                }
            }) as StepSpecificationRegistry

            val previousStep = DummyStepSpecification()
            previousStep.scenario = scenario

            val requestSpecification: suspend HttpRequestBuilder.(ctx: StepContext<*, *>, input: Int) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/test") }

            previousStep
                .httpApache().defaults {
                    connect {
                        url("http://localhost:9999/v2")
                        // version stays HTTP_2 from scenario defaults
                        noDelay = true // override scenario's false
                    }
                }
                .httpApache().http {
                    request(requestSpecification)
                    connect {
                        url("http://localhost:7777")
                        // version stays HTTP_2 from scenario defaults
                        // noDelay stays true from step-level defaults
                    }
                    monitoring {
                        events = false
                        // meters stays true from scenario defaults
                    }
                }

            assertThat(previousStep.nextSteps[0]).isInstanceOf(HttpClientStepSpecificationImpl::class).all {
                prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                    prop(HttpClientConfiguration::host).isEqualTo("localhost")
                    prop(HttpClientConfiguration::port).isEqualTo(7777)
                    prop(HttpClientConfiguration::scheme).isEqualTo("http")
                    prop(HttpClientConfiguration::version).isEqualTo(HttpVersion.HTTP_2)
                    prop(HttpClientConfiguration::noDelay).isTrue()
                }
                prop(HttpClientStepSpecificationImpl<*, *>::monitoringConfig).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
        }
    }

    private data class Entity(val field: String)
}