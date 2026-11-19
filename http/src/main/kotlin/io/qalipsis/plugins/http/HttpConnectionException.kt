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

package io.qalipsis.plugins.http

/**
 * Exception thrown when the connections to the HTTP server cannot be established while the step is starting.
 *
 * It fails the start-up of the step, hence the warm-up of the campaign, and its message is passed to the campaign
 * report as the initialization error of the step.
 *
 * @author Eric Jessé
 */
class HttpConnectionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
