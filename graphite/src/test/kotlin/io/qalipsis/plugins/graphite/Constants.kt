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
    const val STORAGE_SCHEMA_CONFIG_PATH = "/opt/graphite/conf/storage-schemas.conf"
}