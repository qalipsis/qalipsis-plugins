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

package io.qalipsis.gradle.build

import io.qalipsis.gradle.build.metrics.MetricsReportTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

/**
 * Main entry point for the `io.qalipsis.build` Gradle plugin.
 *
 * ## Purpose
 *
 * This plugin provides shared build logic for QALIPSIS plugin projects. It is designed to be
 * applied to the **root project** of each plugin (e.g. `qalipsis-plugins-kafka`) and automatically
 * configures all subprojects.
 *
 * Currently it provides a single feature — **metrics report generation** — but the extension
 * model is designed to accommodate additional build duties in the future.
 *
 * ## Usage
 *
 * In the plugin project's `build.gradle.kts`:
 * ```kotlin
 * plugins {
 *     id("io.qalipsis.build") version "0.1.0"
 * }
 *
 * qalipsisBuild {
 *     metricsReport {
 *         enabled.set(true)
 *         // Optional: supply values for variables the analyzer cannot resolve from source.
 *         prefixOverrides.put("MyStep.redisMethod", "scan")
 *     }
 * }
 * ```
 *
 * ## What it does when `metricsReport` is enabled
 *
 * 1. Registers a `generateMetricsReport` task ([MetricsReportTask]) on the root project.
 * 2. The task scans `src/main/kotlin` across the root project and all subprojects for
 *    `CampaignMeterRegistry` and `EventsLogger` API calls.
 * 3. Produces a CSV file at `build/generated/resources/metrics-report/META-INF/services/qalipsis/metrics-and-events.csv`.
 * 4. Injects the generated resources directory into every subproject's `main` resource source set,
 *    so the CSV is included in the plugin JAR.
 * 5. Wires `processResources` to depend on `generateMetricsReport`, ensuring the CSV is always
 *    up-to-date before resource processing.
 *
 * When `metricsReport.enabled` is `false` (the default), no tasks run and no source sets are modified.
 *
 * ## Plugin name derivation
 *
 * The [pluginName][MetricsReportTask.pluginName] task input is derived from the root project name
 * by stripping the `qalipsis-plugins-` or `qalipsis-plugin-` prefix (e.g.
 * `qalipsis-plugins-kafka` → `kafka`). This name appears in log output and can be overridden
 * in the task configuration if needed.
 */
class QalipsisBuildPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("qalipsisBuild", QalipsisBuildExtension::class.java)

        val generatedResourcesDir = project.layout.buildDirectory.dir(
            "docs/analysis"
        )

        val metricsReportTask = project.tasks.register("generateMetricsReport", MetricsReportTask::class.java) {
            group = "build"
            description = "Generate a CSV report of all meters and events declared in the plugin sources"

            pluginName.convention(derivePluginName(project))
            prefixOverrides.convention(extension.metricsReport.prefixOverrides)
            outputFile.convention(
                generatedResourcesDir.map { dir -> dir.file("metrics-and-events.csv") }
            )

            // Collect src/main/kotlin from all projects (root + subprojects).
            sourceDirs.setFrom(
                project.provider {
                    (setOf(project) + project.subprojects)
                        .map { p -> p.file("src/main/kotlin") }
                        .filter { f -> f.isDirectory }
                }
            )

            onlyIf { extension.metricsReport.enabled.getOrElse(false) }
        }

        // Wire generated resources into the project and its subprojects.
        val wireProject = { target: Project ->
            target.afterEvaluate {
                if (!extension.metricsReport.enabled.getOrElse(false)) return@afterEvaluate
                if (!target.file("src/main/kotlin").isDirectory) return@afterEvaluate

                target.extensions.findByType(SourceSetContainer::class.java)?.named("main") {
                    resources.srcDir(generatedResourcesDir)
                }
                target.tasks.named("processResources") { dependsOn(metricsReportTask) }
                target.tasks.findByName("sourcesJar")?.dependsOn(metricsReportTask)
            }
        }

        project.subprojects.forEach(wireProject)
        wireProject(project)
    }

    /**
     * Derives a human-readable plugin name from the Gradle project name by stripping
     * the standard QALIPSIS prefix (e.g. `qalipsis-plugins-kafka` → `kafka`).
     */
    private fun derivePluginName(project: Project): String {
        return project.name
            .removePrefix("qalipsis-plugins-")
            .removePrefix("qalipsis-plugin-")
    }
}
