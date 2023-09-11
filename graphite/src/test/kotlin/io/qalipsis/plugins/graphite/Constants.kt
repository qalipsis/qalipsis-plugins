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

package io.qalipsis.plugins.graphite


/**
 * @author Joël Valère
 */
object Constants {
    const val GRAPHITE_IMAGE_NAME = "graphiteapp/graphite-statsd:latest"
    const val HTTP_PORT = 80
    const val GRAPHITE_PLAINTEXT_PORT = 2003
    const val GRAPHITE_PICKLE_PORT = 2004
    const val CARBON_CONFIG_PATH = "/opt/graphite/conf/carbon.conf"
}