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

package io.qalipsis.plugins.jakarta


/**
 * @author Krawist Ngoben
 */
object Constants {
    const val DOCKER_IMAGE = "quay.io/artemiscloud/activemq-artemis-broker-init:artemis.2.28.0"
    const val CONTAINER_USER_NAME_ENV_KEY = "AMQ_USER"
    const val CONTAINER_PASSWORD_ENV_KEY = "AMQ_PASSWORD"
    const val CONTAINER_USER_NAME = "qalipsis_user"
    const val CONTAINER_PASSWORD = "qalipsis_password"
}