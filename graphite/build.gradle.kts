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
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
}

description = "QALIPSIS plugin for Graphite"

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

tasks.withType<Test> {
    // Enables the search of memory leaks in the Netty buffers when running all tests.
    systemProperties("io.netty.leakDetectionLevel" to "paranoid")
}

kapt {
    correctErrorTypes = true
    useBuildCache = false
}

allOpen {
    annotations(
        "io.micronaut.aop.Around",
        "jakarta.inject.Singleton",
        "io.qalipsis.api.annotations.StepConverter",
        "io.qalipsis.api.annotations.StepDecorator",
        "io.qalipsis.api.annotations.PluginComponent",
        "io.qalipsis.api.annotations.Spec",
        "io.micronaut.validation.Validated"
    )
}


kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt.useBuildCache = false

val pluginPlatformVersion: String by project
val ktorVersion = "2.2.4"

dependencies {
    implementation(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    implementation("io.ktor:ktor-client-java:2.2.4")
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