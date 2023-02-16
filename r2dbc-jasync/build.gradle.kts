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

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
}

description = "QALIPSIS plugin for R2DBC using jasync-SQL"

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

val micronautVersion: String by project
val kotlinCoroutinesVersion: String by project
val testContainersVersion: String by project

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")
kapt.useBuildCache = false

val jasyncVersion = "2.0.8"
val calciteVersion = "1.32.0"
val mariadbClientVersion = "3.0.7"
val mysqlClientVersion = "8.0.30"
val pluginPlatformVersion: String by project

dependencies {
    implementation(platform("io.qalipsis:plugin-platform:${pluginPlatformVersion}"))
    compileOnly("io.aeris-consulting:catadioptre-annotations")
    compileOnly("io.micronaut:micronaut-runtime")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.apache.calcite:calcite-core:${calciteVersion}")
    implementation("com.github.jasync-sql:jasync-mysql:${jasyncVersion}") {
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    }
    implementation("com.github.jasync-sql:jasync-postgresql:${jasyncVersion}") {
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    }

    api("io.qalipsis:api-common")
    api("io.qalipsis:api-dsl")

    kapt(platform("io.qalipsis:plugin-platform:${pluginPlatformVersion}"))
    kapt("io.qalipsis:api-processors")
    kapt("io.qalipsis:api-dsl")
    kapt("io.qalipsis:api-common")
    kapt("io.aeris-consulting:catadioptre-annotations")

    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.postgresql:postgresql")
    testImplementation("org.testcontainers:mariadb")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:${mariadbClientVersion}")
    testImplementation("org.testcontainers:mysql")
    testImplementation("mysql:mysql-connector-java:${mysqlClientVersion}")
    testImplementation("io.qalipsis:test")
    testImplementation("io.qalipsis:api-dsl")
    testImplementation(testFixtures("io.qalipsis:api-dsl"))
    testImplementation(testFixtures("io.qalipsis:api-common"))
    testImplementation(testFixtures("io.qalipsis:runtime"))
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.aeris-consulting:catadioptre-kotlin")
    testRuntimeOnly("io.qalipsis:runtime")
    testRuntimeOnly("io.qalipsis:head")
    testRuntimeOnly("io.qalipsis:factory")

    kaptTest(platform("io.qalipsis:plugin-platform:${pluginPlatformVersion}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:api-processors")
}


