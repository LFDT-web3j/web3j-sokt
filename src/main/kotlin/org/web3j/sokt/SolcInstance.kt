/*
 * Copyright 2020 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.web3j.sokt

import org.apache.commons.lang3.SystemUtils
import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class SolcInstance(
    val solcRelease: SolcRelease,
    private val directoryPath: String = ".web3j",
    private val redirectOutput: Boolean = false,
    private vararg val sourceFiles: SolidityFile,
) {
    val solcFile: File =
        Paths.get(
            System.getProperty("user.home"),
            directoryPath,
            "solc",
            solcRelease.version,
            if (SystemUtils.IS_OS_WINDOWS) "solc.exe" else "solc",
        ).toFile()

    fun installed(): Boolean {
        return solcFile.exists()
    }

    fun install(): Boolean {
        println("Solidity version ${solcRelease.version} is not installed. Downloading and installing it to ~/$directoryPath/solc/${solcRelease.version}")
        val downloadUrl = solcRelease.downloadUrl()
        if (downloadUrl.isBlank()) {
            return false
        }
        when {
            SystemUtils.IS_OS_WINDOWS -> {
                solcFile.parentFile.mkdirs()

                if (!solcRelease.isWindowsArchive()) {
                    solcFile.writeBytes(URL(downloadUrl).readBytes())
                    if (installed()) {
                        solcFile.setExecutable(true)
                    }
                } else {
                    val winDownloadFile = File("${solcFile.absolutePath.dropLast(4)}.zip")
                    winDownloadFile.writeBytes(URL(downloadUrl).readBytes())
                    ZipFile(winDownloadFile).use { zip ->
                        zip.entries().asSequence().forEach { entry ->
                            zip.getInputStream(entry).use { input ->
                                Paths.get(solcFile.parentFile.absolutePath, entry.name).toFile().outputStream()
                                    .use { output ->
                                        input.copyTo(output)
                                    }
                            }
                        }
                    }
                    winDownloadFile.delete()
                }
                return installed()
            }
            SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_MAC -> {
                solcFile.parentFile.mkdirs()
                solcFile.writeBytes(URL(downloadUrl).readBytes())
                if (installed()) {
                    solcFile.setExecutable(true)
                    return true
                }
            }
        }
        return false
    }

    fun uninstall(): Boolean {
        return solcFile.parentFile.exists() && solcFile.parentFile.deleteRecursively()
    }

    fun execute(vararg args: SolcArguments): SolcOutput {
        if (!installed() && !install()) {
            println("Failed to install solc version")
        }
        return "${solcFile.absolutePath} ${args.joinToString(" ") { it.toString() }} ${sourceFiles.joinToString(" ") {
            it.sourceFile.toAbsolutePath().toString()
        }}".runCommand()
    }

    private fun String.runCommand(): SolcOutput {
        val process = ProcessBuilder(split("\\s".toRegex()))
            .start().apply { waitFor(30, TimeUnit.SECONDS) }

        return SolcOutput(process.exitValue(), process.inputStream.bufferedReader().readText(), process.errorStream.bufferedReader().readText())
    }
}
