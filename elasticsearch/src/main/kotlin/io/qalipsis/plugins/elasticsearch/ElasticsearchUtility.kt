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

package io.qalipsis.plugins.elasticsearch

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.TextNode
import org.apache.http.util.EntityUtils
import org.elasticsearch.client.Request
import org.elasticsearch.client.RestClient


internal object ElasticsearchUtility {

        /**
         * Utility method to check the version of elasticsearch.
         *
         * @param jsonMapper JSON mapper to interpret the results
         * @param restClient Elasticsearch rest client used to execute the request
         */
        fun checkElasticsearchVersionIsGreaterThanSeven(jsonMapper: JsonMapper, restClient: RestClient): Int {
            val versionTree =
                jsonMapper.readTree(EntityUtils.toByteArray(restClient.performRequest(Request("GET", "/")).entity))
            val version = (versionTree.get("version")?.get("number") as TextNode).textValue()
            return version.substringBefore(".").toInt()
        }

}