# Creating a New QALIPSIS Plugin

This guide lists all the files to create or update when adding a new plugin to the repository.

Throughout this guide, replace:

- `<plugin-name>` with the short name of the plugin (e.g., `kafka`, `redis-lettuce`)
- `<Plugin Name>` with the display name (e.g., `Kafka`, `Redis Lettuce`)
- `qalipsis-plugin-<plugin-name>` with the full Gradle module name (e.g., `qalipsis-plugin-kafka`)

## Files to create

### 1. Plugin module directory: `<plugin-name>/`

Create the directory for the plugin source code with the standard layout:

```
<plugin-name>/
├── build.gradle.kts
├── project.version
├── build-config/
│   ├── allowed-licenses.json
│   └── license-normalizer-bundle.json
└── src/
    ├── main/kotlin/...
    └── test/kotlin/...
```

#### `<plugin-name>/build.gradle.kts`

Apply the shared convention plugin and declare dependencies:

```kotlin
plugins {
    id("qalipsis-plugin")
    `java-test-fixtures` // only if needed
}

description = "QALIPSIS plugin for <Plugin Name>"

val pluginPlatformVersion: String by project

dependencies {
    implementation(platform("io.qalipsis:qalipsis-plugin-platform:${pluginPlatformVersion}"))
    // ... your dependencies
}
```

#### `<plugin-name>/project.version`

A single line with the version, e.g., `0.17.1-SNAPSHOT`.

#### `<plugin-name>/build-config/allowed-licenses.json`

Copy from an existing plugin (e.g., `kafka/build-config/allowed-licenses.json`) and add any extra licenses required by
your dependencies.

Entries are organized as:

1. License-only entries (sorted alphabetically by `moduleLicense`)
2. Module-specific entries (sorted alphabetically by `moduleName`)

#### `<plugin-name>/build-config/license-normalizer-bundle.json`

Copy from an existing plugin (e.g., `kafka/build-config/license-normalizer-bundle.json`) and extend if needed.

### 2. GitHub Actions workflow: `.github/workflows/<plugin-name>.yml`

Create the per-plugin CI workflow:

```yaml
name: <Plugin Name> Plugin

on:
  push:
    branches: [ 'main', 'develop/*' ]
    paths: [ '<plugin-name>/**', 'buildSrc/**', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradle/**' ]
  pull_request:
    branches: [ 'main', 'develop/*' ]
    paths: [ '<plugin-name>/**', 'buildSrc/**', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradle/**' ]
  workflow_dispatch:

jobs:
  build-and-publish:
    if: github.event_name != 'pull_request'
    uses: ./.github/workflows/_build-and-publish.yml
    with:
      module: qalipsis-plugin-<plugin-name>
      module-dir: <plugin-name>
    secrets: inherit

  verify-pr:
    if: github.event_name == 'pull_request'
    uses: ./.github/workflows/_verify-pr.yml
    with:
      module: qalipsis-plugin-<plugin-name>
      module-dir: <plugin-name>
    secrets: inherit
```

If your plugin requires extra environment variables from secrets for Gradle (e.g., API tokens for integration tests),
add them to the `env:` block of both `_build-and-publish.yml` and `_verify-pr.yml`. Since all callers use
`secrets: inherit`, the secret will resolve to the value for the plugin that defines it, and be empty for others.

## Files to update

### 3. `settings.gradle.kts`

Register the new module:

```kotlin
include("qalipsis-plugin-<plugin-name>")
project(":qalipsis-plugin-<plugin-name>").projectDir = file("<plugin-name>")
```

### 4. `user-bom/build.gradle.kts`

Add the plugin to the `constraints` block inside `dependencies`:

```kotlin
constraints {
    // ... existing plugins
    api(project(":qalipsis-plugin-<plugin-name>"))
}
```

### 5. `.github/workflows/build-and-publish-all.yml`

Add a new job for the plugin:

```yaml
  <plugin-name>:
    uses: ./.github/workflows/_build-and-publish.yml
    with:
      module: qalipsis-plugin-<plugin-name>
      module-dir: <plugin-name>
    secrets: inherit
```

## Summary checklist

| Action | File                                                        |
|--------|-------------------------------------------------------------|
| Create | `<plugin-name>/build.gradle.kts`                            |
| Create | `<plugin-name>/project.version`                             |
| Create | `<plugin-name>/build-config/allowed-licenses.json`          |
| Create | `<plugin-name>/build-config/license-normalizer-bundle.json` |
| Create | `.github/workflows/<plugin-name>.yml`                       |
| Update | `settings.gradle.kts`                                       |
| Update | `user-bom/build.gradle.kts`                                 |
| Update | `.github/workflows/build-and-publish-all.yml`               |
