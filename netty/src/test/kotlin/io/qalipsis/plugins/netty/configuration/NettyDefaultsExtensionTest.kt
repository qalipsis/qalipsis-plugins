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

package io.qalipsis.plugins.netty.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.http.spec.HttpClientStepSpecificationImpl
import io.qalipsis.plugins.netty.mqtt.publisher.spec.MqttPublishStepSpecificationImpl
import io.qalipsis.plugins.netty.mqtt.subscriber.deserializer.MqttByteArrayDeserializer
import io.qalipsis.plugins.netty.mqtt.subscriber.spec.MqttSubscribeStepSpecificationImpl
import io.qalipsis.plugins.netty.socket.ConnectionStrategyConfiguration
import io.qalipsis.plugins.netty.socket.ConnectionStrategyType
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration
import io.qalipsis.plugins.netty.tcp.spec.TcpClientStepSpecificationImpl
import io.qalipsis.plugins.netty.udp.spec.UdpClientStepSpecification
import java.time.Duration
import org.junit.jupiter.api.Test

/**
 * @author Eric Jesse
 */
internal class NettyDefaultsExtensionTest {

    @Test
    fun `should apply defaults to TCP step`() {
        val defaults = NettyDefaultsExtensionImpl()
        defaults.tcpConnection {
            address("localhost", 9000)
            readTimeout = Duration.ofSeconds(30)
            pool {
                size = 5
                checkHealthBeforeUse = true
            }
        }
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = TcpClientStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(TcpClientStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(TcpClientConfiguration::host).isEqualTo("localhost")
                prop(TcpClientConfiguration::port).isEqualTo(9000)
                prop(TcpClientConfiguration::readTimeout).isEqualTo(Duration.ofSeconds(30))
                prop(TcpClientConfiguration::poolConfiguration).isNotNull().all {
                    prop("size") { it.size }.isEqualTo(5)
                    prop("checkHealthBeforeUse") { it.checkHealthBeforeUse }.isTrue()
                }
            }
            prop(TcpClientStepSpecificationImpl<*>::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to HTTP step`() {
        val defaults = NettyDefaultsExtensionImpl()
        defaults.httpConnection {
            address("localhost", 8080)
            readTimeout = Duration.ofSeconds(60)
            pool {
                size = 10
            }
        }
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = HttpClientStepSpecificationImpl<Any, String>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                prop(HttpClientConfiguration::host).isEqualTo("localhost")
                prop(HttpClientConfiguration::port).isEqualTo(8080)
                prop(HttpClientConfiguration::readTimeout).isEqualTo(Duration.ofSeconds(60))
                prop(HttpClientConfiguration::poolConfiguration).isNotNull().all {
                    prop("size") { it.size }.isEqualTo(10)
                }
            }
            prop(HttpClientStepSpecificationImpl<*, *>::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to UDP step`() {
        val defaults = NettyDefaultsExtensionImpl()
        defaults.udpConnection {
            address("localhost", 5000)
            readTimeout = Duration.ofSeconds(5)
        }
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = UdpClientStepSpecification<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(UdpClientStepSpecification<*>::connectionConfiguration).all {
                prop("host") { it.host }.isEqualTo("localhost")
                prop("port") { it.port }.isEqualTo(5000)
                prop("readTimeout") { it.readTimeout }.isEqualTo(Duration.ofSeconds(5))
            }
            prop(UdpClientStepSpecification<*>::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to MQTT publish step`() {
        val defaults = NettyDefaultsExtensionImpl()
        defaults.mqttConnection {
            host = "mqtt-host"
            port = 1884
        }
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = MqttPublishStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(MqttPublishStepSpecificationImpl<*>::mqttPublishConfiguration).all {
                prop("connectionConfiguration") { it.connectionConfiguration }.all {
                    prop("host") { it.host }.isEqualTo("mqtt-host")
                    prop("port") { it.port }.isEqualTo(1884)
                }
            }
            prop(MqttPublishStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to MQTT subscribe step`() {
        val defaults = NettyDefaultsExtensionImpl()
        defaults.mqttConnection {
            host = "mqtt-host"
            port = 1884
        }
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = MqttSubscribeStepSpecificationImpl(MqttByteArrayDeserializer())
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(MqttSubscribeStepSpecificationImpl<*>::mqttSubscribeConfiguration).all {
                prop("connectionConfiguration") { it.connectionConfiguration }.all {
                    prop("host") { it.host }.isEqualTo("mqtt-host")
                    prop("port") { it.port }.isEqualTo(1884)
                }
            }
            prop(MqttSubscribeStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply connection strategy defaults to TCP step`() {
        val defaults = NettyDefaultsExtensionImpl()
        defaults.tcpConnection {
            connectionStrategy {
                strategyType = ConnectionStrategyType.WARMUP
                shared = true
            }
        }

        val spec = TcpClientStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(TcpClientStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(TcpClientConfiguration::connectionStrategyConfiguration).all {
                    prop(ConnectionStrategyConfiguration::strategyType).isEqualTo(ConnectionStrategyType.WARMUP)
                    prop(ConnectionStrategyConfiguration::shared).isTrue()
                }
            }
        }
    }

    @Test
    fun `should apply connection strategy defaults to HTTP step`() {
        val defaults = NettyDefaultsExtensionImpl()
        defaults.httpConnection {
            connectionStrategy {
                strategyType = ConnectionStrategyType.POOL
                shared = false
            }
        }

        val spec = HttpClientStepSpecificationImpl<Any, String>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(HttpClientStepSpecificationImpl<*, *>::connectionConfiguration).all {
                prop(HttpClientConfiguration::connectionStrategyConfiguration).all {
                    prop(ConnectionStrategyConfiguration::strategyType).isEqualTo(ConnectionStrategyType.POOL)
                    prop(ConnectionStrategyConfiguration::shared).isFalse()
                }
            }
        }
    }

    @Test
    fun `should only apply monitoring when no connection is configured`() {
        val defaults = NettyDefaultsExtensionImpl()
        defaults.monitoring {
            events = true
        }

        val tcpSpec = TcpClientStepSpecificationImpl<Any>()
        defaults.applyTo(tcpSpec)

        assertThat(tcpSpec).all {
            prop(TcpClientStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(TcpClientConfiguration::host).isEqualTo("localhost")
                prop(TcpClientConfiguration::port).isEqualTo(0)
            }
            prop(TcpClientStepSpecificationImpl<*>::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }
    }
}
