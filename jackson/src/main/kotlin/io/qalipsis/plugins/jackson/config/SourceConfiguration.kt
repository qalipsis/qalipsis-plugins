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

package io.qalipsis.plugins.jackson.config

import io.qalipsis.api.annotations.Spec
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.validation.constraints.NotNull

/**
 * Configuration describing the resource to read to receive the data.
 *
 * @author Eric Jessé
 */
@Spec
internal data class SourceConfiguration(
        var url: @NotNull URL? = null,
        var encoding: @NotNull Charset = StandardCharsets.UTF_8
)