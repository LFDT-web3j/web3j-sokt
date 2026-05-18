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

import com.github.zafarkhaja.semver.Version
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.lang3.SystemUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

internal enum class SolcPlatform(val directoryName: String) {
    WINDOWS_AMD64("windows-amd64"),
    LINUX_AMD64("linux-amd64"),
    LINUX_ARM64("linux-arm64"),
    MACOSX_AMD64("macosx-amd64"),
    ;

    val indexUrl: String = "https://binaries.soliditylang.org/$directoryName/list.json"

    fun binaryUrl(path: String): String = "https://binaries.soliditylang.org/$directoryName/$path"

    companion object {
        fun current(): SolcPlatform? {
            val architecture = System.getProperty("os.arch").lowercase()
            return when {
                SystemUtils.IS_OS_WINDOWS -> WINDOWS_AMD64
                SystemUtils.IS_OS_MAC -> MACOSX_AMD64
                SystemUtils.IS_OS_LINUX && architecture in setOf("aarch64", "arm64") -> LINUX_ARM64
                SystemUtils.IS_OS_LINUX -> LINUX_AMD64
                else -> null
            }
        }
    }
}

@Serializable
private data class SolcBuildIndex(
    val builds: List<SolcBuild>,
    val releases: Map<String, String> = emptyMap(),
)

@Serializable
private data class SolcBuild(
    val path: String,
    val version: String,
    val prerelease: String? = null,
)

class VersionResolver(private val directoryPath: String = ".web3j") {
    private val json = Json { ignoreUnknownKeys = true }

    operator fun get(uri: String): String {
        val con = URL(uri).openConnection() as HttpsURLConnection
        con.connectTimeout = TimeUnit.MILLISECONDS.toMillis(200).toInt()
        con.readTimeout = TimeUnit.SECONDS.toMillis(1).toInt()
        con.requestMethod = "GET"
        con.setRequestProperty("Content-Type", "application/json")
        con.setRequestProperty("Accept", "application/json")
        con.doOutput = true
        val reader = BufferedReader(
            InputStreamReader(con.inputStream),
        )
        var inputLine: String?
        val response = StringBuffer()
        while (reader.readLine().also { inputLine = it } != null) {
            response.append(inputLine)
        }
        reader.close()
        return response.toString()
    }

    fun getSolcReleases(): List<SolcRelease> {
        val platform = SolcPlatform.current() ?: return bundledSolcReleases()
        val versionsFile = Paths.get(System.getProperty("user.home"), directoryPath, "solc", "${platform.directoryName}-list.json").toFile()
        return runCatching {
            val result = get(platform.indexUrl)
            versionsFile.parentFile.mkdirs()
            versionsFile.writeText(result)
            solcReleasesFromOfficialIndex(result, platform)
        }.getOrElse {
            if (versionsFile.exists()) {
                runCatching { solcReleasesFromOfficialIndex(versionsFile.readText(), platform) }.getOrElse {
                    bundledSolcReleases()
                }
            } else {
                bundledSolcReleases()
            }
        }
    }

    fun versionsFromString(input: String): List<String> {
        return Regex("\\s*[\\^<>=~!]{0,3}\\s*(\\d*(\\.?)\\s*){1,3}").findAll(input)
            .filter { it.groupValues[0].isNotBlank() }.map { it.groupValues[0].trim().replace("\\s".toRegex(), "") }
            .toList()
    }

    fun getCompatibleVersions(pragmaRequirement: String, releases: List<SolcRelease>): List<SolcRelease> {
        val requiredVersions = versionsFromString(pragmaRequirement)
        return releases.filter {
            requiredVersions.all(fun(nr: String): Boolean {
                return Version.valueOf(it.version).satisfies(nr) && it.isCompatibleWithOs()
            })
        }
    }

    fun getLatestCompatibleVersion(pragmaRequirement: String?): SolcRelease? {
        val solcReleases = getSolcReleases()
        return if (pragmaRequirement != null) {
            getCompatibleVersions(pragmaRequirement, solcReleases).lastOrNull()
        } else {
            solcReleases.lastOrNull { it.isCompatibleWithOs() }
        }
    }

    internal fun solcReleasesFromOfficialIndex(input: String, platform: SolcPlatform): List<SolcRelease> {
        val buildIndex = json.decodeFromString<SolcBuildIndex>(input)
        val stableReleases = if (buildIndex.releases.isNotEmpty()) {
            buildIndex.releases.entries.map { (version, path) -> platform.toSolcRelease(version, path) }
        } else {
            buildIndex.builds.filter { it.prerelease == null }.map { platform.toSolcRelease(it.version, it.path) }
        }

        return stableReleases.sortedBy { Version.valueOf(it.version) }
    }

    private fun bundledSolcReleases(): List<SolcRelease> {
        val defaultReleases = ClassLoader.getSystemResource("releases.json").readText()
        return json.decodeFromString<List<SolcRelease>>(defaultReleases)
    }

    private fun SolcPlatform.toSolcRelease(version: String, path: String): SolcRelease {
        val binaryUrl = binaryUrl(path)
        return when (this) {
            SolcPlatform.WINDOWS_AMD64 -> SolcRelease(version = version, windowsUrl = binaryUrl)
            SolcPlatform.LINUX_AMD64, SolcPlatform.LINUX_ARM64 -> SolcRelease(version = version, linuxUrl = binaryUrl)
            SolcPlatform.MACOSX_AMD64 -> SolcRelease(version = version, macUrl = binaryUrl)
        }
    }
}
