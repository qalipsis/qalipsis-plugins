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

package io.qalipsis.plugins.http.response

import org.apache.hc.client5.http.cookie.Cookie
import org.apache.hc.core5.http.ContentType

/**
 * Default implementation of the [HttpResponse].
 *
 * @author Eric Jessé
 */
internal class DefaultHttpResponse<B>(
    override val code: Int,
    override val reason: String,
    override val contentType: ContentType?,
    override val headers: Map<String, String>,
    override val cookies: Map<String, Cookie>,
    override val bodyBytes: ByteArray?,
    override val body: B?,
) : HttpResponse<B>
