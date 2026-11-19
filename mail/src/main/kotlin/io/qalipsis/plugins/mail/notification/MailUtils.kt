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

package io.qalipsis.plugins.mail.notification

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Contains utility methods to handle file compression to zip.
 *
 * @author Francisca Eze
 */
internal object MailUtils {

    fun compressSFile(sourceFile: File, attachmentFile: File) {
        FileOutputStream(attachmentFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                writeFileToZip(sourceFile, sourceFile.name, zos)
            }
        }
    }

    fun compressDirectory(reportDirectory: File, attachmentFile: File) {
        val collectedFiles = mutableListOf<File>()
        collectFiles(reportDirectory, collectedFiles)
        FileOutputStream(attachmentFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                collectedFiles.forEach { fileToCompress ->
                    val name = fileToCompress.relativeTo(reportDirectory).path.replace(File.separatorChar, '/')
                    writeFileToZip(fileToCompress, name, zos)
                }
            }
        }
    }

    private fun writeFileToZip(file: File, entryName: String, zos: ZipOutputStream) {
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(1024)
            var length: Int
            while (fis.read(buffer).also { length = it } > 0) {
                zos.write(buffer, 0, length)
            }
            zos.closeEntry()
        }
    }

    private fun collectFiles(directory: File, fileList: MutableCollection<File>) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile) {
                fileList.add(file)
            } else if (file.isDirectory) {
                collectFiles(file, fileList)
            }
        }
    }
}