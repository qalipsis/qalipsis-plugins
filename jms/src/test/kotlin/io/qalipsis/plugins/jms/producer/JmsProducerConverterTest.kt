/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.jms.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.micrometer.core.instrument.Counter
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.plugins.jms.JmsDeserializer
import io.qalipsis.test.mockk.CleanMockkRecordedCalls
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyExactly
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.apache.activemq.command.ActiveMQDestination
import org.apache.activemq.command.ActiveMQTextMessage
import org.apache.activemq.command.ActiveMQTopic
import org.apache.activemq.command.MessageId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.beans.ConstructorProperties
import java.io.Serializable
import java.util.concurrent.atomic.AtomicLong
import javax.jms.Message
import javax.jms.Session
import javax.jms.TextMessage
import org.junit.jupiter.api.assertThrows


/**
 *
 * @author Alexander Sosnovsky
 */
@CleanMockkRecordedCalls
@WithMockk
internal class JmsProducerConverterTest {

    private val converter = JmsProducerConverter()

    @RelaxedMockK
    private lateinit var session: Session

    @Test
    @Timeout(2)
    internal fun `should create byte message`() {

        val bytes = "byte-array".toByteArray()

        val producerRecord = JmsProducerRecord(
            destination = ActiveMQTopic().createDestination("dest-1"),
            messageType = JmsMessageType.BYTES,
            value = bytes
        )

        converter.convert(producerRecord, session)

        verify {
            session.createBytesMessage()
        }
    }

    @Test
    @Timeout(2)
    internal fun `should create object message`() {

        val userObj = User("id", "name")
        
        val producerRecord = JmsProducerRecord(
            destination = ActiveMQTopic().createDestination("dest-1"),
            messageType = JmsMessageType.OBJECT,
            value = userObj
        )

        converter.convert(producerRecord, session)

        verify {
            session.createObjectMessage(producerRecord.value as Serializable)
        }
    }

    @Test
    @Timeout(2)
    internal fun `should create text message`() {

        val text = "text"

        val producerRecord = JmsProducerRecord(
            destination = ActiveMQTopic().createDestination("dest-1"),
            messageType = JmsMessageType.TEXT,
            value = text
        )

        converter.convert(producerRecord, session)

        verify {
            session.createTextMessage(producerRecord.value as String)
        }
    }

    @Test
    @Timeout(2)
    internal fun `should throw exception if prvided object are not serializable`() {

        val userObj = UserNotSerializable("id", "name")

        val producerRecord = JmsProducerRecord(
            destination = ActiveMQTopic().createDestination("dest-1"),
            messageType = JmsMessageType.OBJECT,
            value = userObj
        )

        assertThrows<IllegalArgumentException> {
            converter.convert(producerRecord, session)
        }
    }

    @Test
    @Timeout(2)
    internal fun `should create auto message`() {

        val text = "text"

        val producerRecordText = JmsProducerRecord(
            destination = ActiveMQTopic().createDestination("dest-1"),
            value = text
        )

        val bytes = "byte-array".toByteArray()

        val producerRecordBytes = JmsProducerRecord(
            destination = ActiveMQTopic().createDestination("dest-1"),
            value = bytes
        )

        val userObj = User("id", "name")

        val producerRecordObject = JmsProducerRecord(
            destination = ActiveMQTopic().createDestination("dest-1"),
            value = userObj
        )

        converter.convert(producerRecordText, session)
        converter.convert(producerRecordBytes, session)
        converter.convert(producerRecordObject, session)

        verify {
            session.createBytesMessage()
            session.createTextMessage(producerRecordText.value as String)
            session.createObjectMessage(producerRecordObject.value as Serializable)
        }
    }

    data class User @ConstructorProperties("id", "name") constructor(val id: String, val name: String) : Serializable

    data class UserNotSerializable @ConstructorProperties("id", "name") constructor(val id: String, val name: String)

}
