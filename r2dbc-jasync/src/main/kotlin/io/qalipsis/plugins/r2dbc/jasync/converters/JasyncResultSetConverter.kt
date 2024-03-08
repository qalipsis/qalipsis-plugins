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

package io.qalipsis.plugins.r2dbc.jasync.converters;

import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Converts a value read from a datasource into records that can be forwarded to next steps then sends it with the input to the output channel.
 *
 * @param R type of the object to convert
 * @param O type of the result
 * @param I type of the input
 *
 * @author Fiodar Hmyza
 */
interface JasyncResultSetConverter<R, O, I> {

    /**
     * Sends [value] to the [output] channel in any form.
     *
     * @param offset a reference to the offset, it is up to the implementation to increment it
     * @param value input value to send after any conversion to the output
     * @param input received in the channel to be sent along with the data after conversion
     * @param output channel to received the data after conversion
     * @param eventsTags tags to use for the events
     */
    suspend fun supply(offset: AtomicLong, value: R, input: I, output: StepOutput<O>, eventsTags: Map<String, String>)

    fun start(context: StepStartStopContext) = Unit

    fun stop(context: StepStartStopContext) = Unit

}
