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