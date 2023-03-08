plugins {
    `java-platform`

    id("nebula.contacts") version "5.1.0"
    id("nebula.info") version "9.1.1"
    id("nebula.maven-publish") version "17.3.3"
    id("nebula.maven-scm") version "17.3.3"
    id("nebula.maven-manifest") version "17.3.3"
    id("nebula.maven-apache-license") version "17.3.3"
    signing
}

group = "io.qalipsis"
version = File(rootDir, "project.version").readText().trim()

infoBroker {
    excludedManifestProperties = listOf(
        "Manifest-Version", "Module-Owner", "Module-Email", "Module-Source",
        "Built-OS", "Build-Host", "Build-Job", "Build-Host", "Build-Job", "Build-Number", "Build-Id", "Build-Url",
        "Built-Status"
    )
}

contacts {
    addPerson("eric.jesse@aeris-consulting.com", delegateClosureOf<nebula.plugin.contacts.Contact> {
        moniker = "Eric Jessé"
        github = "ericjesse"
        role("Owner")
    })
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "maven-central-snapshots"
        setUrl("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

signing {
    publishing.publications.forEach { sign(it) }
}

val ossrhUsername: String? by project
val ossrhPassword: String? by project
publishing {
    repositories {
        maven {
            val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://oss.sonatype.org/content/repositories/snapshots/"
            name = "sonatype"
            // See https://docs.gradle.org/current/userguide/single_versions.html#version_ordering.
            url = uri(if (project.version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                username = ossrhUsername
                password = ossrhPassword
            }
        }
    }

    publications {
        create<MavenPublication>("qalipsisPlatform") {
            from(components["javaPlatform"])
        }
    }
}

val apiVersion: String? by project
val pluginPlatformVersion: String? by project

javaPlatform {
    allowDependencies()
}

dependencies {
    // Core modules.
    api(platform("io.qalipsis:dev-platform:$apiVersion"))
    api("io.qalipsis:api-dsl:$apiVersion")

    constraints {
        // API modules.
        api("io.qalipsis:api-processors:$apiVersion")
        api("io.qalipsis:api-common:$apiVersion")
        api("io.qalipsis:api-dev:$apiVersion")
        api("io.qalipsis:api-dsl:$apiVersion")
        api("io.qalipsis:test:$apiVersion")

        // Core modules.
        api("io.qalipsis:runtime:$pluginPlatformVersion")
        api("io.qalipsis:head:$pluginPlatformVersion")
        api("io.qalipsis:factory:$pluginPlatformVersion")

        // Plugins.
        api("io.qalipsis.plugin:cassandra:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:elasticsearch:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:graphite:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:influxdb:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:jackson:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:jakarta-ee-messaging:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:jms:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:kafka:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:mail:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:mongodb:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:netty:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:r2dbc-jasync:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:rabbitmq:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:redis-lettuce:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:slack:0.5.5-SNAPSHOT")
        api("io.qalipsis.plugin:timescaledb:0.5.10-SNAPSHOT")
    }
}

