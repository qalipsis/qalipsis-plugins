# QALIPSIS BOM

Platform dependencies to develop scenarios for QALIPSIS.

## How to use it?

In your build.gradle file, add the following dependency to your scen:

```
dependencies {
    implementation(enforcedPlatform("io.qalipsis:qalipsis-platform:<version>"))
    kapt(enforcedPlatform("io.qalipsis:qalipsis-platform:<version>"))
    
    // Add your plugins:
    implementation("io.qalipsis:plugin-cassandra")
    implementation("io.qalipsis:plugin-jackson")
    
    // Add the additional libraries you need:
    implementation("io.kotest:kotest-assertions-core:5.4.2")
}
```

That's all folk!

## Current version

To know the current version, look at [this file](./project.version).
