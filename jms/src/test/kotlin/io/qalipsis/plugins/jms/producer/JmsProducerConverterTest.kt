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

package io.qalipsis.plugins.jms.producer

import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.test.mockk.CleanMockkRecordedCalls
import io.qalipsis.test.mockk.WithMockk
import org.apache.activemq.command.ActiveMQTopic
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.beans.ConstructorProperties
import java.io.Serializable
import javax.jms.Session


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
