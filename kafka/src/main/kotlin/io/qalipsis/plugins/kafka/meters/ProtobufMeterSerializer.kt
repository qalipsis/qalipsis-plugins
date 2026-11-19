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

package io.qalipsis.plugins.kafka.meters

import io.qalipsis.api.meters.MeterSnapshot
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for the meters using Protobuf.
 * It first converts the meter to a protobuf object before serializing.
 */
internal class ProtobufMeterSerializer(
    private val protobufMeterConverter: ProtobufMeterConverter,
) : Serializer<MeterSnapshot> {

    override fun serialize(topic: String, data: MeterSnapshot): ByteArray {
        return protobufMeterConverter.convert(data).toByteArray()
    }
}
