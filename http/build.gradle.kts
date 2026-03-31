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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("qalipsis-plugin")
    `java-test-fixtures`
}

description = "QALIPSIS plugin for HTTP using Apache HTTP Components"

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xallow-result-return-type",
            "-Xemit-jvm-type-annotations",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
}

tasks.withType<Test> {
    systemProperty("testcontainers.platform", "linux/amd64")
    jvmArgs("-XX:-MaxFDLimit")
}

val pluginPlatformVersion: String by project
val apacheHttpComponentsClientVersion = "5.5.1"
val apacheHttpComponentsCoreVersion = "5.3.6"

dependencies {
    implementation(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    compileOnly("io.aeris-consulting:catadioptre-annotations")
    compileOnly("io.micronaut:micronaut-runtime")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    implementation("io.reactivex.rxjava3:rxjava:3.+")
    implementation("org.apache.httpcomponents.client5:httpclient5:$apacheHttpComponentsClientVersion")
    implementation("org.apache.httpcomponents.core5:httpcore5:$apacheHttpComponentsCoreVersion")
    implementation("org.apache.httpcomponents.core5:httpcore5-h2:$apacheHttpComponentsCoreVersion")
    implementation("org.apache.httpcomponents.core5:httpcore5-reactive:$apacheHttpComponentsCoreVersion")

    api("io.qalipsis:qalipsis-api-common")
    api("io.qalipsis:qalipsis-api-dsl")

    kapt(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    kapt("io.qalipsis:qalipsis-api-processors")
    kapt("io.qalipsis:qalipsis-api-dsl")
    kapt("io.qalipsis:qalipsis-api-common")
    kapt("io.aeris-consulting:catadioptre-annotations")

    testFixturesImplementation(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    testFixturesImplementation("io.qalipsis:qalipsis-api-common")
    testFixturesImplementation("io.qalipsis:qalipsis-test")
    testFixturesImplementation("io.micronaut.reactor:micronaut-reactor")
    testFixturesImplementation("io.aeris-consulting:catadioptre-kotlin")
    kaptTestFixtures("io.micronaut:micronaut-inject-java")

    testImplementation(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    testImplementation("io.qalipsis:qalipsis-test")
    testImplementation("io.qalipsis:qalipsis-api-dsl")
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-dsl"))
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-common"))
    testImplementation(testFixtures("io.qalipsis:qalipsis-runtime"))
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("org.apache.commons:commons-lang3")
    testImplementation("io.aeris-consulting:catadioptre-kotlin")
    testRuntimeOnly("io.qalipsis:qalipsis-runtime")
    testRuntimeOnly("io.qalipsis:qalipsis-head")
    testRuntimeOnly("io.qalipsis:qalipsis-factory")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")

    kaptTest(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:qalipsis-api-processors")
}
