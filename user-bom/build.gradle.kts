import org.jreleaser.model.Active
import org.jreleaser.model.Signing
import org.jreleaser.model.api.deploy.maven.MavenCentralMavenDeployer

plugins {
    `java-platform`
    `maven-publish`
    id("org.jreleaser")
}

group = "io.qalipsis"
version = file("project.version").readText().trim()
description = "Bill of materials for QALIPSIS scenario development"

repositories {
    mavenLocal()
    if (version.toString().endsWith("-SNAPSHOT")) {
        maven {
            name = "QALIPSIS OSS Snapshots"
            url = uri("https://maven.qalipsis.com/repository/oss-snapshots")
            content {
                includeGroup("io.qalipsis")
                includeGroup("io.qalipsis.plugin")
            }
        }
    }
    mavenCentral()
}

jreleaser {
    gitRootSearch.set(true)

    release {
        // One least one enabled release provider is mandatory, so let's use Github and disable
        // all the options.
        github {
            skipRelease.set(true)
            skipTag.set(true)
            uploadAssets.set(Active.NEVER)
            token.set("dummy")
        }
    }

    val enableSign = extra.properties.get("qalipsis.sign") != "false"
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
                    active.set(Active.RELEASE_PRERELEASE)
                    namespace.set("io.qalipsis")
                    applyMavenCentralRules.set(true)
                    stage.set(MavenCentralMavenDeployer.Stage.UPLOAD)
                    stagingRepository(layout.buildDirectory.dir("staging-deploy").get().asFile.path)
                }
            }
            nexus2 {
                register("qalipsis-snapshots") {
                    active.set(Active.SNAPSHOT)
                    // Here we are using our own repository, because the maven central snapshot repo
                    // is too often not available.
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

publishing {
    repositories {
        maven {
            // Local repository to store the artifacts before they are released by JReleaser.
            name = "PreRelease"
            setUrl(layout.buildDirectory.dir("staging-deploy"))
        }
    }

    publications {
        create<MavenPublication>("qalipsisPlatform") {
            pom {
                name.set(project.name)
                description.set(project.description)

                if (rootProject.version.toString().endsWith("-SNAPSHOT")) {
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
                        name.set("GNU AFFERO GENERAL PUBLIC LICENSE, Version 3 (AGPL-3.0)")
                        url.set("http://opensource.org/licenses/AGPL-3.0")
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

            from(components["javaPlatform"])
        }
    }
}

val pluginPlatformVersion: String? by project

javaPlatform {
    allowDependencies()
}

dependencies {
    // Core modules.
    api(platform("io.qalipsis:qalipsis-dev-platform:$pluginPlatformVersion"))
    api("io.qalipsis:qalipsis-api-dsl:$pluginPlatformVersion")

    constraints {
        // API modules.
        api("io.qalipsis:qalipsis-api-processors:$pluginPlatformVersion")
        api("io.qalipsis:qalipsis-api-common:$pluginPlatformVersion")
        api("io.qalipsis:qalipsis-api-dev:$pluginPlatformVersion")
        api("io.qalipsis:qalipsis-api-dsl:$pluginPlatformVersion")
        api("io.qalipsis:qalipsis-test:$pluginPlatformVersion")

        // Core modules.
        api("io.qalipsis:qalipsis-runtime:$pluginPlatformVersion")
        api("io.qalipsis:qalipsis-head:$pluginPlatformVersion")
        api("io.qalipsis:qalipsis-webapp:${pluginPlatformVersion}")
        api("io.qalipsis:qalipsis-factory:$pluginPlatformVersion")

        // Plugins.
        api(project(":qalipsis-plugin-cassandra"))
        api(project(":qalipsis-plugin-elasticsearch"))
        api(project(":qalipsis-plugin-graphite"))
        api(project(":qalipsis-plugin-http"))
        api(project(":qalipsis-plugin-influxdb"))
        api(project(":qalipsis-plugin-jackson"))
        api(project(":qalipsis-plugin-jakarta-ee-messaging"))
        api(project(":qalipsis-plugin-jms"))
        api(project(":qalipsis-plugin-kafka"))
        api(project(":qalipsis-plugin-mail"))
        api(project(":qalipsis-plugin-mongodb"))
        api(project(":qalipsis-plugin-netty"))
        api(project(":qalipsis-plugin-r2dbc-jasync"))
        api(project(":qalipsis-plugin-rabbitmq"))
        api(project(":qalipsis-plugin-redis-lettuce"))
        api(project(":qalipsis-plugin-slack"))
        api(project(":qalipsis-plugin-sql"))
        api(project(":qalipsis-plugin-timescaledb"))
    }
}
