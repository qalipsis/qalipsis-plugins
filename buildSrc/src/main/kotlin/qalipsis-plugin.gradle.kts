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

import groovy.lang.Closure
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.jreleaser.model.Active
import org.jreleaser.model.Signing
import org.jreleaser.model.api.deploy.maven.MavenCentralMavenDeployer

plugins {
    java
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    `maven-publish`
    id("org.jreleaser")
    id("com.github.jk1.dependency-license-report")
    id("com.palantir.git-version")
}

apply(plugin = "io.qalipsis.build")

group = "io.qalipsis.plugin"
version = file("project.version").readText().trim()

val testNumCpuCore: String? by project

// ---- QALIPSIS Build Plugin ----

configure<io.qalipsis.gradle.build.QalipsisBuildExtension> {
    metricsReport {
        enabled.set(true)
    }
}

// ---- allOpen ----

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

// ---- kapt ----

kapt {
    correctErrorTypes = true
    useBuildCache = false
}

kotlin.sourceSets["test"].kotlin.srcDir("build/generated/source/kaptKotlin/catadioptre")

// ---- Repositories ----

val pluginPlatformVersion: String by project
repositories {
    mavenLocal()
    if (pluginPlatformVersion.endsWith("-SNAPSHOT")) {
        maven {
            name = "QALIPSIS OSS Snapshots"
            url = uri("https://maven.qalipsis.com/repository/oss-snapshots")
            content {
                includeGroup("io.qalipsis")
            }
        }
    }
    mavenCentral()
}

// ---- Kotlin / Java ----

kotlin {
    javaToolchains {
        jvmToolchain(11)
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

// ---- Compile ----

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        javaParameters = true
        freeCompilerArgs += listOf(
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.ObsoleteCoroutinesApi",
            "-Xallow-result-return-type",
            "-Xemit-jvm-type-annotations"
        )
    }
}

// ---- Test tasks ----

val test = tasks.named<Test>("test") {
    ignoreFailures = System.getProperty("ignoreUnitTestFailures", "false").toBoolean()
    exclude("**/*IntegrationTest.*", "**/*IntegrationTest$*")
}

val integrationTest = tasks.register<Test>("integrationTest") {
    this.group = "verification"
    ignoreFailures = System.getProperty("ignoreIntegrationTestFailures", "false").toBoolean()
    include("**/*IntegrationTest*", "**/*IntegrationTest$*", "**/*IntegrationTest.**")
    exclude("**/*Scenario*.*")
}

val scenariosTest = tasks.register<Test>("scenariosTest") {
    this.group = "verification"
    ignoreFailures = System.getProperty("ignoreIntegrationTestFailures", "false").toBoolean()
    include("**/*Scenario*IntegrationTest.*")
}

tasks.named<Task>("check") {
    dependsOn(test.get(), integrationTest.get(), scenariosTest.get())
}

if (!project.file("src/main/kotlin").isDirectory) {
    project.logger.lifecycle("Disabling publish for ${project.name}")
    tasks.withType<AbstractPublishToMaven> {
        enabled = false
    }
}

tasks.withType<Test> {
    if (!testNumCpuCore.isNullOrBlank()) {
        project.logger.lifecycle("Running tests of ${project.name} with $testNumCpuCore cores")
        jvmArgs("-XX:ActiveProcessorCount=$testNumCpuCore")
    }
    useJUnitPlatform()
    testLogging {
        events(FAILED, STANDARD_OUT, STANDARD_ERROR)
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

        debug {
            events(*org.gradle.api.tasks.testing.logging.TestLogEvent.values())
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

        info {
            events(*org.gradle.api.tasks.testing.logging.TestLogEvent.values())
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

artifacts {
    if (project.plugins.hasPlugin("java-test-fixtures")) {
        archives(tasks.findByName("testFixturesSources") as Jar)
        archives(tasks.findByName("testFixturesJavadoc") as Jar)
        archives(tasks.findByName("testFixturesJar") as Jar)
    }
}

// ---- License Report ----

afterEvaluate {
    licenseReport {
        renderers = arrayOf<com.github.jk1.license.render.ReportRenderer>(
            com.github.jk1.license.render.InventoryHtmlReportRenderer(
                "report.html",
                project.description ?: project.name
            )
        )
        allowedLicensesFile = rootProject.file("build-config/allowed-licenses.json")
        filters = arrayOf<com.github.jk1.license.filter.DependencyFilter>(
            com.github.jk1.license.filter.LicenseBundleNormalizer(
                rootProject.file("build-config/license-normalizer-bundle.json").path,
                true
            )
        )
    }
}

// ---- Publishing ----

publishing {
    repositories {
        maven {
            name = "PreRelease"
            setUrl(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set(project.description)

                    if (version.toString().endsWith("-SNAPSHOT")) {
                        withXml {
                            asNode().appendNode("distributionManagement").appendNode("repository").apply {
                                appendNode("id", "qalipsis-oss-snapshots")
                                appendNode("name", "QALIPSIS OSS Snapshots")
                                appendNode("url", "https://maven.qalipsis.com/repository/oss-snapshots")
                            }
                        }
                    }
                    url.set("https://qalipsis.io")
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0 (Apache-2.0)")
                            url.set("https://opensource.org/license/apache-2-0")
                        }
                    }
                    developers {
                        developer {
                            id.set("ericjesse")
                            name.set("Eric Jessé")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/qalipsis/qalipsis-plugins.git")
                        url.set("https://github.com/qalipsis/qalipsis-plugins.git/")
                    }
                }
            }
        }
    }
}

// ---- JReleaser ----

val versionDetails: Closure<com.palantir.gradle.gitversion.VersionDetails> by extra
val currentGitBranch = versionDetails().branchName ?: ""
if (currentGitBranch != "main") {
    project.logger.lifecycle("Maven Central deploy disabled for branch '${currentGitBranch}' in ${project.name}")
}

jreleaser {
    gitRootSearch.set(true)

    release {
        github {
            skipRelease.set(true)
            skipTag.set(true)
            uploadAssets.set(Active.NEVER)
            token.set("dummy")
        }
    }

    val enableSign = !extra.has("qalipsis.sign") || extra.get("qalipsis.sign") != "false"
    if (enableSign) {
        signing {
            active.set(Active.ALWAYS)
            mode.set(Signing.Mode.MEMORY)
            armored = true
        }
    }

    deploy {
        maven {
            mavenCentral {
                register("qalipsis-releases") {
                    active.set(if (currentGitBranch == "main" && !pluginPlatformVersion.endsWith("-SNAPSHOT")) Active.RELEASE_PRERELEASE else Active.NEVER)
                    namespace.set("io.qalipsis")
                    applyMavenCentralRules.set(true)
                    stage.set(MavenCentralMavenDeployer.Stage.UPLOAD)
                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().asFile.path)
                }
            }
            nexus2 {
                register("qalipsis-snapshots") {
                    active.set(Active.SNAPSHOT)
                    url.set("https://maven.qalipsis.com/repository/oss-snapshots/")
                    snapshotUrl.set("https://maven.qalipsis.com/repository/oss-snapshots/")
                    applyMavenCentralRules.set(true)
                    verifyPom.set(false)
                    snapshotSupported.set(true)
                    closeRepository.set(true)
                    releaseRepository.set(true)
                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().asFile.path)
                }
            }
        }
    }
}
