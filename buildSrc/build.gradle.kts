plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
    implementation("org.jetbrains.kotlin:kotlin-allopen:1.9.25")
    implementation("org.jreleaser:jreleaser-gradle-plugin:1.18.0")
    implementation("com.github.jk1:gradle-license-report:2.9")
    implementation("com.palantir.gradle.gitversion:gradle-git-version:3.0.0")
    implementation("io.qalipsis:qalipsis-build-plugin:0.1.1")
}
