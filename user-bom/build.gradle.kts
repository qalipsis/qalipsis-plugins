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
    api(platform("io.qalipsis:qalipsis-dev-platform:$apiVersion"))
    api("io.qalipsis:qalipsis-api-dsl:$apiVersion")

    constraints {
        // API modules.
        api("io.qalipsis:qalipsis-api-processors:$apiVersion")
        api("io.qalipsis:qalipsis-api-common:$apiVersion")
        api("io.qalipsis:qalipsis-api-dev:$apiVersion")
        api("io.qalipsis:qalipsis-api-dsl:$apiVersion")
        api("io.qalipsis:qalipsis-test:$apiVersion")

        // Core modules.
        api("io.qalipsis:qalipsis-runtime:$pluginPlatformVersion")
        api("io.qalipsis:qalipsis-head:$pluginPlatformVersion")
        api("io.qalipsis:qalipsis-factory:$pluginPlatformVersion")

        // Plugins.
        api("io.qalipsis.plugin:qalipsis-plugin-cassandra:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-elasticsearch:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-graphite:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-influxdb:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-jackson:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-jakarta-ee-messaging:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-jms:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-kafka:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-mail:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-mongodb:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-netty:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-r2dbc-jasync:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-rabbitmq:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-redis-lettuce:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-slack:0.6.0-SNAPSHOT")
        api("io.qalipsis.plugin:qalipsis-plugin-timescaledb:0.6.0-SNAPSHOT")
    }
}

