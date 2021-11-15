package io.qalipsis.plugins.redis.lettuce

interface Meters {
    var bytesToBeSent: Int
    var sentBytes: Int
}

internal data class MetersImpl(
    override var bytesToBeSent: Int = 0,
    override var sentBytes: Int = 0,
) : Meters