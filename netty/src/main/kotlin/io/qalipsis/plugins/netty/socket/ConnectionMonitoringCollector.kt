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

package io.qalipsis.plugins.netty.socket

import java.time.Duration

/**
 * Collector dedicated to the opening of a single connection, forwarding all the records to [delegate].
 *
 * The collectors of the pooled and warm-up steps are shared by all the connections of the step: their [cause] keeps
 * the latest failure of any connection, and cannot tell whether a given connection failed. This collector only keeps
 * the failures of the connection it is used for.
 *
 * @author Eric Jessé
 */
internal class ConnectionMonitoringCollector(
    private val delegate: SocketMonitoringCollector
) : SocketMonitoringCollector by delegate {

    @Volatile
    override var cause: Throwable? = null
        private set

    override fun recordConnectionFailure(timeToFailure: Duration, throwable: Throwable) {
        cause = throwable
        delegate.recordConnectionFailure(timeToFailure, throwable)
    }

    override fun recordTlsHandshakeFailure(timeToFailure: Duration, throwable: Throwable) {
        cause = throwable
        delegate.recordTlsHandshakeFailure(timeToFailure, throwable)
    }
}
