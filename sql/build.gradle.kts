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

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
}

description = "QALIPSIS plugin for SQL"

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

val calciteVersion = "1.38.0"
val mariadbClientVersion = "3.5.7"
val mysqlClientVersion = "8.0.30"
val r2dbcPoolVersion = "1.0.2.RELEASE"
val r2dbcPostgresqlVersion = "1.1.1.RELEASE"
val r2dbcMysqlVersion = "1.4.1"
val r2dbcMariadbVersion = "1.4.0"
val r2dbcMssqlVersion = "1.0.4.RELEASE"
val r2dbcOracleVersion = "1.3.0"
val mssqlJdbcVersion = "12.8.1.jre11"
val pluginPlatformVersion: String by project

dependencies {
    implementation(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    compileOnly("io.aeris-consulting:catadioptre-annotations")
    compileOnly("io.micronaut:micronaut-runtime")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.apache.calcite:calcite-core:${calciteVersion}")
    implementation("io.r2dbc:r2dbc-spi")
    implementation("io.r2dbc:r2dbc-pool:${r2dbcPoolVersion}")
    implementation("org.postgresql:r2dbc-postgresql:${r2dbcPostgresqlVersion}")
    implementation("io.asyncer:r2dbc-mysql:${r2dbcMysqlVersion}")
    implementation("org.mariadb:r2dbc-mariadb:${r2dbcMariadbVersion}")
    implementation("io.r2dbc:r2dbc-mssql:${r2dbcMssqlVersion}")
    implementation("com.oracle.database.r2dbc:oracle-r2dbc:${r2dbcOracleVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    api("io.qalipsis:qalipsis-api-common")
    api("io.qalipsis:qalipsis-api-dsl")

    kapt(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    kapt("io.qalipsis:qalipsis-api-processors")
    kapt("io.qalipsis:qalipsis-api-dsl")
    kapt("io.qalipsis:qalipsis-api-common")
    kapt("io.aeris-consulting:catadioptre-annotations")

    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.postgresql:postgresql")
    testImplementation("org.testcontainers:testcontainers-mariadb")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:${mariadbClientVersion}")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("mysql:mysql-connector-java:${mysqlClientVersion}")
    testImplementation("org.testcontainers:testcontainers-mssqlserver:2.0.3")
    testImplementation("com.microsoft.sqlserver:mssql-jdbc:${mssqlJdbcVersion}")
    testImplementation("org.testcontainers:testcontainers-oracle-free:2.0.3")
    testImplementation("io.qalipsis:qalipsis-test")
    testImplementation("io.qalipsis:qalipsis-api-dsl")
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-dsl"))
    testImplementation(testFixtures("io.qalipsis:qalipsis-api-common"))
    testImplementation(testFixtures("io.qalipsis:qalipsis-runtime"))
    testImplementation("javax.annotation:javax.annotation-api")
    testImplementation("io.micronaut:micronaut-runtime")
    testImplementation("io.aeris-consulting:catadioptre-kotlin")
    testRuntimeOnly("io.qalipsis:qalipsis-runtime")
    testRuntimeOnly("io.qalipsis:qalipsis-head")
    testRuntimeOnly("io.qalipsis:qalipsis-factory")

    kaptTest(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    kaptTest("io.micronaut:micronaut-inject-java")
    kaptTest("io.qalipsis:qalipsis-api-processors")
}


