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

package io.qalipsis.plugins.jackson

import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.BroadcastSpecification
import io.qalipsis.api.steps.LoopableSpecification
import io.qalipsis.api.steps.NoSingletonSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.UnicastSpecification
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.plugins.jackson.config.SourceConfiguration
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Path

/**
 *
 * @author Eric Jessé
 */
abstract class AbstractJacksonStepSpecification<O : Any?, SELF : AbstractJacksonStepSpecification<O, SELF>> :
    AbstractStepSpecification<Unit, DatasourceRecord<O>, SELF>(),
    JacksonStepSpecification<Unit, DatasourceRecord<O>, SELF>,
    LoopableSpecification, UnicastSpecification, BroadcastSpecification, NoSingletonSpecification {

    internal val sourceConfiguration = SourceConfiguration()

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.SEQUENTIAL)

    /**
     * Reads the CSV data from a plain file on the file system.
     *
     * @param path the path to the file, either absolute or relative to the working directory.
     */
    fun file(path: String): SELF {
        sourceConfiguration.url = Path.of(path).toUri().toURL()
        @Suppress("UNCHECKED_CAST")
        return this as SELF
    }

    /**
     * Reads the CSV data from a class path resource.
     *
     * @param path the path to the resource, the leading slash is ignored.
     */
    fun classpath(path: String): SELF {
        sourceConfiguration.url =
            this::class.java.classLoader.getResource(if (path.startsWith("/")) path.substring(1) else path)
        @Suppress("UNCHECKED_CAST")
        return this as SELF
    }

    /**
     * Reads the CSV data from the a URL.
     *
     * @param url the url to access to the CSV resource.
     */
    fun url(url: String): SELF {
        sourceConfiguration.url = URL(url)
        @Suppress("UNCHECKED_CAST")
        return this as SELF
    }

    /**
     * Sets the charset of the source file. Default is UTF-8.
     *
     * @param encoding the name of the charset
     */
    fun encoding(encoding: String): SELF {
        sourceConfiguration.encoding = Charset.forName(encoding)
        @Suppress("UNCHECKED_CAST")
        return this as SELF
    }

    /**
     * Sets the charset of the source file. Default is UTF-8.
     *
     * @param encoding the charset to use
     */
    fun encoding(encoding: Charset): SELF {
        sourceConfiguration.encoding = encoding
        @Suppress("UNCHECKED_CAST")
        return this as SELF
    }
}
