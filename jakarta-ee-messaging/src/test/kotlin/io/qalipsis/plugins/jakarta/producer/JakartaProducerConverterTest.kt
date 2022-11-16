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

package io.qalipsis.plugins.jakarta.producer

import assertk.assertions.*
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.test.mockk.CleanMockkRecordedCalls
import io.qalipsis.test.mockk.WithMockk
import jakarta.jms.Session
import org.apache.activemq.artemis.jms.client.ActiveMQDestination
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.beans.ConstructorProperties
import java.io.Serializable

/**
 *
 * @author Alexander Sosnovsky
 */
@CleanMockkRecordedCalls
@WithMockk
internal class JakartaProducerConverterTest {

    private val converter = JakartaProducerConverter()

    @RelaxedMockK
    private lateinit var session: Session

    @Test
    @Timeout(2)
    internal fun `should create byte message`() {

        val bytes = "byte-array".toByteArray()

        val producerRecord = JakartaProducerRecord(
            destination = ActiveMQDestination.createDestination("dest-1", ActiveMQDestination.TYPE.DESTINATION),
            messageType = JakartaMessageType.BYTES,
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
        
        val producerRecord = JakartaProducerRecord(
            destination = ActiveMQDestination.createDestination("dest-1", ActiveMQDestination.TYPE.DESTINATION),
            messageType = JakartaMessageType.OBJECT,
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

        val producerRecord = JakartaProducerRecord(
            destination = ActiveMQDestination.createDestination("dest-1", ActiveMQDestination.TYPE.DESTINATION),
            messageType = JakartaMessageType.TEXT,
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

        val producerRecord = JakartaProducerRecord(
            destination = ActiveMQDestination.createDestination("dest-1", ActiveMQDestination.TYPE.DESTINATION),
            messageType = JakartaMessageType.OBJECT,
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

        val producerRecordText = JakartaProducerRecord(
            destination = ActiveMQDestination.createDestination("dest-1", ActiveMQDestination.TYPE.DESTINATION),
            value = text
        )

        val bytes = "byte-array".toByteArray()

        val producerRecordBytes = JakartaProducerRecord(
            destination = ActiveMQDestination.createDestination("dest-1", ActiveMQDestination.TYPE.DESTINATION),
            value = bytes
        )

        val userObj = User("id", "name")

        val producerRecordObject = JakartaProducerRecord(
            destination = ActiveMQDestination.createDestination("dest-1", ActiveMQDestination.TYPE.DESTINATION),
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
