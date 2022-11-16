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

package io.qalipsis.plugins.jakarta

import jakarta.jms.Message


/**
 * Deserializer from a JMS [jakarta.jms.Message] to a user-defined type.
 */
interface JakartaDeserializer<V> {

    /**
     * Deserializes the [message] to the specified type [V].
     *
     * @param message consumed from Jakarta.
     * @return [V] the specified type to return after deserialization.
     */
    fun deserialize(message: Message): V
}
