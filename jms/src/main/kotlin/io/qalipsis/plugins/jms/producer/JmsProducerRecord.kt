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

import javax.jms.Destination

/**
 * Qalipsis representation of a JMS message to be produced.
 *
 * @author Alexander Sosnovsky
 *
 * @property destination name of the topic or queue where message should be produced
 * @property messageType it is a type used for creating native JMS [Message]
 * @property value the payload of the [Message]
 */
data class JmsProducerRecord(
    val destination: Destination,
    val messageType: JmsMessageType = JmsMessageType.AUTO,
    val value: Any
)
