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

package io.qalipsis.plugins.jakarta.producer

import io.qalipsis.plugins.jakarta.destination.Destination

/**
 * Qalipsis representation of a Jakarta message to be produced.
 *
 * @property destination name of the topic or queue where message should be produced
 * @property messageType it is a type used for creating native JMS [jakarta.jms.Message]
 * @property value the payload of the [jakarta.jms.Message]
 *
 * @author Krawist Ngoben
 */
data class JakartaProducerRecord(
    val destination: Destination,
    val messageType: JakartaMessageType = JakartaMessageType.AUTO,
    val value: Any
)
