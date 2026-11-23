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

package io.qalipsis.gradle.build

import java.io.IOException
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

private const val MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2"

private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"

private val MAVEN_CENTRAL_TIMEOUT = Duration.ofSeconds(20)

/**
 * Tasks of JReleaser that deploy the staged artifacts to Maven Central.
 */
private val JRELEASER_DEPLOYMENT_TASKS = setOf("jreleaserFullRelease", "jreleaserDeploy")

/**
 * Skips the publication of a release version of the project when the exact same version is already on Maven Central,
 * which refuses to overwrite it. Snapshot versions are always published.
 *
 * Maven Central is only queried when a publication task is about to run.
 */
fun Project.skipPublicationOfReleasedVersion() {
    if (!version.toString().endsWith(SNAPSHOT_SUFFIX)) {
        val released by lazy { isOnMavenCentral() }
        val reason = "$group:$name:$version is already on Maven Central"
        tasks.withType(PublishToMavenRepository::class.java).configureEach { onlyIf(reason) { !released } }
        tasks.matching { it.name in JRELEASER_DEPLOYMENT_TASKS }.configureEach { onlyIf(reason) { !released } }
    }
}

/**
 * Returns whether the POM of each Maven publication of the project exists on Maven Central. When Maven Central cannot
 * be reached, the version is considered as not released, letting the publication report the actual failure.
 */
private fun Project.isOnMavenCentral(): Boolean {
    val publications = extensions.getByType(PublishingExtension::class.java).publications
        .withType(MavenPublication::class.java)
    return publications.isNotEmpty() && publications.all { publication ->
        val path = "${publication.groupId.replace('.', '/')}/${publication.artifactId}/${publication.version}"
        pomExists("$MAVEN_CENTRAL_URL/$path/${publication.artifactId}-${publication.version}.pom")
    }.also { released ->
        if (released) logger.lifecycle("$group:$name:$version is already on Maven Central, its publication is skipped")
    }
}

private fun Project.pomExists(url: String): Boolean = try {
    val request = HttpRequest.newBuilder(URI.create(url))
        .method("HEAD", HttpRequest.BodyPublishers.noBody())
        .timeout(MAVEN_CENTRAL_TIMEOUT)
        .build()
    HttpClient.newBuilder().connectTimeout(MAVEN_CENTRAL_TIMEOUT).build()
        .send(request, HttpResponse.BodyHandlers.discarding())
        .statusCode() == HTTP_OK
} catch (e: IOException) {
    logger.warn("Maven Central could not be queried for $url: ${e.message}")
    false
}
