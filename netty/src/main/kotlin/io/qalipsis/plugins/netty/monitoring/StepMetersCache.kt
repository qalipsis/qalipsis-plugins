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

package io.qalipsis.plugins.netty.monitoring

import java.util.concurrent.ConcurrentHashMap

/**
 * Cache of the meters of a step, shared by the monitoring collectors created for each of its requests.
 *
 * Without it, each request looks up all its meters in the meter registry of the campaign. The meters belong to a
 * campaign, so the cache has to be cleared when the step starts and stops.
 *
 * @author Eric Jessé
 */
internal class StepMetersCache {

    private val meters = ConcurrentHashMap<MeterKey, Any>()

    /**
     * Returns the meter named [name] with the [tags], creating it with [factory] if it is not cached yet.
     */
    @Suppress("UNCHECKED_CAST")
    fun <M : Any> getOrCreate(name: String, tags: Map<String, String>, factory: () -> M?): M? =
        meters[MeterKey(name, tags)] as M? ?: factory()?.let { meters.putIfAbsent(MeterKey(name, tags.toMap()), it) as M? ?: it }

    fun clear() = meters.clear()

    /**
     * Key of a meter: the tags might be a mutable map when looking up, a copy is stored when caching.
     */
    private data class MeterKey(val name: String, val tags: Map<String, String>)
}
