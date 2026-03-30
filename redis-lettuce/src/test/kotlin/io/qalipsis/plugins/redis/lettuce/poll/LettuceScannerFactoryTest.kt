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
