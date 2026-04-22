plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("qalipsis-build") {
            id = "io.qalipsis.build"
            implementationClass = "io.qalipsis.gradle.build.QalipsisBuildPlugin"
        }
    }
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

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
