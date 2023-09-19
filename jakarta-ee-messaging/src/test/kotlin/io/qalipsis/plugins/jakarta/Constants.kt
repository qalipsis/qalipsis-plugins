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