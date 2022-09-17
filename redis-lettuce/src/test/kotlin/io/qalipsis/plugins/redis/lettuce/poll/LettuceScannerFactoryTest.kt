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

package io.qalipsis.plugins.redis.lettuce.poll

import io.qalipsis.plugins.redis.lettuce.poll.scanners.LettuceHScanner
import io.qalipsis.plugins.redis.lettuce.poll.scanners.LettuceKeysScanner
import io.qalipsis.plugins.redis.lettuce.poll.scanners.LettuceSScanner
import io.qalipsis.plugins.redis.lettuce.poll.scanners.LettuceZScanner
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LettuceScannerFactoryTest {

    @Test
    fun `should get LettuceScanMethodExecutor when SCAN type is provided for executor factory`() {
        val typeExecutor = LettuceScanExecutorFactory.newInstance(RedisLettuceScanMethod.SCAN, relaxedMockk { })
        assertTrue(typeExecutor is LettuceKeysScanner)
    }

    @Test
    fun `should get LettuceSScanMethodExecutor when SSCAN type is provided for executor factory`() {
        val typeExecutor = LettuceScanExecutorFactory.newInstance(RedisLettuceScanMethod.SSCAN, relaxedMockk { })
        assertTrue(typeExecutor is LettuceSScanner)
    }

    @Test
    fun `should get LettuceHScanMethodExecutor when HSCAN type is provided for executor factory`(){
        val typeExecutor = LettuceScanExecutorFactory.newInstance(RedisLettuceScanMethod.HSCAN, relaxedMockk())
        assertTrue(typeExecutor is LettuceHScanner)
    }

    @Test
    fun `should get LettuceZScanMethodExecutor when ZSCAN type is provided for executor factory`(){
        val typeExecutor = LettuceScanExecutorFactory.newInstance(RedisLettuceScanMethod.ZSCAN, relaxedMockk())
        assertTrue(typeExecutor is LettuceZScanner)
    }
}
