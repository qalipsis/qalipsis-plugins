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

package io.qalipsis.plugins.mail

import assertk.assertThat
import assertk.assertions.isTrue
import io.qalipsis.plugins.mail.notification.MailUtils
import io.qalipsis.plugins.mail.utils.TestUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class MailUtilsTest {

    @TempDir
    private lateinit var tempDir: File

    @TempDir
    private lateinit var unzipDirectory: File

    private lateinit var zippedFile: File

    @BeforeEach
    fun setup() {
        zippedFile = File.createTempFile(CAMPAIGN_KEY, ".zip", tempDir)
    }

    @Test
    fun `should compress a single file`() {
        // given
        val sourceFile = File.createTempFile("source", ".txt", tempDir)
        val content = "test content for single file compression"
        sourceFile.writeText(content)

        // when
        MailUtils.compressSFile(sourceFile, zippedFile)

        // then
        assertThat(zippedFile).transform("exists") { it.exists() }.isTrue()

        TestUtil.unzip(zippedFile, unzipDirectory.absolutePath)
        val unzippedFiles = Files.walk(unzipDirectory.toPath())
            .filter(Files::isRegularFile)
            .collect(Collectors.toList())
        assertEquals(1, unzippedFiles.size)
        assertEquals(sourceFile.name, unzippedFiles[0].fileName.toString())
        assertEquals(content, Files.readString(unzippedFiles[0]))
    }

    @Test
    fun `should recursively compress a directory`() {
        // given
        val reportDirectory = copyHtmlRootReportsToTemp()

        // when
        MailUtils.compressDirectory(reportDirectory.toFile(), zippedFile)

        // then
        assertThat(zippedFile).transform("exists") { it.exists() }.isTrue()

        TestUtil.unzip(zippedFile, unzipDirectory.absolutePath)
        val fileBeforeCompression =
            Files.walk(reportDirectory)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList())
        val filesAfterCompression =
                Files.walk(unzipDirectory.toPath())
                .filter((Files::isRegularFile))
                .collect(Collectors.toList())
        assertEquals(filesAfterCompression.size, fileBeforeCompression.size)
        assertTrue(isEqualContent(fileBeforeCompression, filesAfterCompression))
    }

    private fun isEqualContent(fileBeforeCompression: List<Path>, filesAfterCompression: List<Path>): Boolean {
        var isEqualContent = true
        val k = filesAfterCompression.iterator()
        val t = fileBeforeCompression.iterator()
        while (k.hasNext() && t.hasNext()) {
            if (!isEqual(k.next(), t.next())) {
                isEqualContent = false
                break
            }
        }
        return isEqualContent
    }

    private fun isEqual(firstFile: Path, secondFile: Path): Boolean {
        if (Files.size(firstFile) != Files.size(secondFile)) {
            return false
        }
        val first = Files.readString(firstFile)
        val second = Files.readString(secondFile)
        return first.contentEquals(second)
    }

    private fun copyHtmlRootReportsToTemp(): Path {
        val reportDirectory = Files.createTempDirectory(REPORT_FOLDER)
        val fileList = this.javaClass.getResourceAsStream("/$REPORT_FOLDER/index.txt")!!
            .bufferedReader().readLines()
        for (relativePath in fileList) {
            val inputStream = this.javaClass.getResourceAsStream("/$REPORT_FOLDER/$relativePath")
            val targetFile = File(reportDirectory.toFile(), relativePath)
            targetFile.parentFile.mkdirs()

            inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input!!.copyTo(output)
                }
            }
        }

        return reportDirectory
    }

    companion object {
        const val REPORT_FOLDER = "html-reports"
        const val CAMPAIGN_KEY = "campaign-8"
    }
}