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
 * Exception thrown by the [HttpClientStep] when an HTTP request execution fails. The full
 * [HttpResult] — including the populated meters and any partial response — is exposed via
 * [result] so downstream observers can inspect what was captured before the failure.
 *
 * @author Eric Jessé
 */
data class HttpRequestException(val result: HttpResult<*, *>) : RuntimeException(result.cause)
