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

package io.qalipsis.plugins.http.request

/**
 * Builder for HTTP requests.
 */
interface HttpRequestBuilder {

    /**
     * Creates a new simple request, that can carry an optional body.
     */
    fun simple(method: HttpMethod, uri: String) = SimpleHttpRequest(method, uri)

    /**
     * Creates a form request.
     */
    fun form(method: HttpMethod, uri: String) = FormHttpRequest(method, uri)

    /**
     * Creates a multipart request.
     */
    fun multipart(method: HttpMethod, uri: String) = MultipartHttpRequest(method, uri)

}

internal object HttpRequestBuilderImpl : HttpRequestBuilder