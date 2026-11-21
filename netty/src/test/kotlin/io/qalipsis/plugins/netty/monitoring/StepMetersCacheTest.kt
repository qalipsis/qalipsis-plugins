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

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Test

internal class StepMetersCacheTest {

    private val cache = StepMetersCache()

    @Test
    fun `given a cached meter when getting it with equal tags then the factory is not called again`() {
        // given
        val creations = AtomicInteger()
        val tags = mutableMapOf("method" to "GET")
        val meter = cache.getOrCreate("meter", tags) { Any().also { creations.incrementAndGet() } }

        // when
        val cached = cache.getOrCreate("meter", mapOf("method" to "GET")) { Any().also { creations.incrementAndGet() } }

        // then
        assertThat(cached).isSameInstanceAs(meter)
        assertThat(creations.get()).isEqualTo(1)
    }

    @Test
    fun `given a cached meter when the tags used to create it change then the cached meter keeps its tags`() {
        // given
        val tags = mutableMapOf("method" to "GET")
        val getMeter = cache.getOrCreate("meter", tags) { Any() }

        // when
        tags["method"] = "POST"
        val postMeter = cache.getOrCreate("meter", tags) { Any() }

        // then
        assertThat(cache.getOrCreate("meter", mapOf("method" to "GET")) { Any() }).isSameInstanceAs(getMeter)
        assertThat(cache.getOrCreate("meter", mapOf("method" to "POST")) { Any() }).isSameInstanceAs(postMeter)
    }

    @Test
    fun `given a factory returning null when getting the meter then nothing is cached`() {
        // when
        val meter = cache.getOrCreate<Any>("meter", emptyMap()) { null }

        // then
        assertThat(meter).isNull()
        val created = Any()
        assertThat(cache.getOrCreate("meter", emptyMap()) { created }).isSameInstanceAs(created)
    }

    @Test
    fun `given a cached meter when clearing the cache then the meter is created again`() {
        // given
        cache.getOrCreate("meter", emptyMap()) { Any() }

        // when
        cache.clear()

        // then
        val created = Any()
        assertThat(cache.getOrCreate("meter", emptyMap()) { created }).isSameInstanceAs(created)
    }
}
