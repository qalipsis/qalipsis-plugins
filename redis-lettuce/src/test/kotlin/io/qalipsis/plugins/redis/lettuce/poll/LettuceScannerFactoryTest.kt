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
