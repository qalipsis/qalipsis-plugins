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

description = "QALIPSIS plugin for TimescaleDB"

val r2dbcPostgresqlVersion = "1.1.1.RELEASE"
val r2dbcPoolVersion = "1.0.2.RELEASE"
val commonsTextVersion = "1.15.0"
val pluginPlatformVersion: String by project

dependencies {
    implementation(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    compileOnly("io.aeris-consulting:catadioptre-annotations")
    compileOnly("io.micronaut:micronaut-runtime")

    api("io.qalipsis:qalipsis-api-common")
    api("io.qalipsis:qalipsis-api-dsl")

    implementation("org.liquibase:liquibase-core")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.postgresql:r2dbc-postgresql:${r2dbcPostgresqlVersion}")
    implementation("io.r2dbc:r2dbc-pool:${r2dbcPoolVersion}")
    implementation("org.postgresql:postgresql")
    implementation("io.r2dbc:r2dbc-spi")
    implementation("org.apache.commons:commons-text:${commonsTextVersion}")
    implementation(
        group = "io.netty",
        name = "netty-transport-native-epoll",
        classifier = "linux-x86_64"
    )
    implementation(
        group = "io.netty",
        name = "netty-transport-native-kqueue",
        classifier = "osx-x86_64"
    )

    kapt(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    kapt("io.qalipsis:qalipsis-api-processors")
    kapt("io.qalipsis:qalipsis-api-dsl")
    kapt("io.qalipsis:qalipsis-api-common")
    kapt("io.aeris-consulting:catadioptre-annotations")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("io.qalipsis:qalipsis-test")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.aeris-consulting:catadioptre-kotlin")
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-dsl"))
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-common"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("io.qalipsis:qalipsis-runtime")
    testRuntimeOnly("io.qalipsis:qalipsis-head")
    testRuntimeOnly("io.qalipsis:qalipsis-factory")


    kaptTest(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:qalipsis-api-processors")
}
