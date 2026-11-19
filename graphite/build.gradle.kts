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

plugins {
    id("qalipsis-plugin")
}

description = "QALIPSIS plugin for Graphite"

tasks.withType<Test> {
    // Enables the search of memory leaks in the Netty buffers when running all tests.
    systemProperties("io.netty.leakDetectionLevel" to "paranoid")
}

val pluginPlatformVersion: String by project
val ktorVersion = "2.3.13"

dependencies {
    implementation(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    implementation("io.ktor:ktor-client-java:$ktorVersion")
    compileOnly("io.aeris-consulting:catadioptre-annotations")
    compileOnly("io.micronaut:micronaut-runtime")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    api("io.qalipsis:qalipsis-api-common")

    implementation("io.netty:netty-handler")
    implementation("io.netty:netty-transport")
    implementation(group = "io.netty", name = "netty-transport-native-epoll", classifier = "linux-x86_64")
    implementation(group = "io.netty", name = "netty-transport-native-kqueue", classifier = "osx-x86_64")
    implementation("io.netty:netty-buffer")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    kapt(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    kapt("io.qalipsis:qalipsis-api-processors")
    kapt("io.qalipsis:qalipsis-api-dsl")
    kapt("io.qalipsis:qalipsis-api-common")
    kapt("io.aeris-consulting:catadioptre-annotations")
    kapt("io.micronaut:micronaut-inject-java")

    testImplementation(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("io.qalipsis:qalipsis-test")
    testImplementation("io.qalipsis:qalipsis-api-common")
    testImplementation("io.qalipsis:qalipsis-api-dsl")
    testImplementation("io.qalipsis:qalipsis-api-dev")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-dsl"))
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-common"))
    testImplementation(testFixtures("io.qalipsis:qalipsis-runtime"))
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.aeris-consulting:catadioptre-kotlin")
    testImplementation("org.awaitility:awaitility-kotlin")
    testRuntimeOnly("io.qalipsis:qalipsis-runtime")
    testRuntimeOnly("io.qalipsis:qalipsis-head")
    testRuntimeOnly("io.qalipsis:qalipsis-factory")

    kaptTest(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:qalipsis-api-processors")
}
