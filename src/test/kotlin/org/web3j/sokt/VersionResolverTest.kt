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
import org.apache.commons.lang3.SystemUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class VersionResolverTest {
    private val unsanitizedStrings =
        """
            >=0.5.10<0.5.14;
            >0.4.21 <=0.6.0;
            ^0.4.2;
            >=0.4.21   <0.6.0;
            >=0.4.21 <=0.6.0    ;
               >0.4.21 <0.6.0;
            >=0.4.21<0.6.0;
              ^ 0.4.21  ;
             ~ 0.4.21  ;
            0.4.2;
            >0.4.23 <0.5.0;
            0.4.0;
            v0.4.0; // like npm
            ^0.4.0;
            >= 0.4.0;
            <= 0.4.0;
            < 0.4.0;
            > 0.4.0;
            != 0.4.0;
            >=0.4.0 <0.4.8;
            0.4;
            v0.4;
            ^0.4;
            >= 0.4;
            <= 0.4;
            < 0.5;
            > 0.4;
            != 0.4;
            >=0.4 <=0.4;
            0;
            v0;
            ^0;
            >= 0;
            <= 0;
            < 1;
            > 0;
            != 0;
            >=0 <=1;
            ~0.4.24;
            ~0.4.24 >=0.5;
        """.trimIndent().split("\n")

    private val correctVersionConstraints =
        """
            >=0.5.10, <0.5.14
            >0.4.21, <=0.6.0
            ^0.4.2
            >=0.4.21, <0.6.0
            >=0.4.21, <=0.6.0
            >0.4.21, <0.6.0
            >=0.4.21, <0.6.0
            ^0.4.21
            ~0.4.21
            0.4.2
            >0.4.23, <0.5.0
            0.4.0
            0.4.0
            ^0.4.0
            >=0.4.0
            <=0.4.0
            <0.4.0
            >0.4.0
            !=0.4.0
            >=0.4.0, <0.4.8
            0.4
            0.4
            ^0.4
            >=0.4
            <=0.4
            <0.5
            >0.4
            !=0.4
            >=0.4, <=0.4
            0
            0
            ^0
            >=0
            <=0
            <1
            >0
            !=0
            >=0, <=1
            ~0.4.24
            ~0.4.24, >=0.5
        """.trimIndent().split("\n")

    private val correctWindowsVersions =
        """
            0.5.13
            0.5.14
            0.4.25
            0.5.14
            0.5.14
            0.5.14
            0.5.14
            0.4.25
            0.4.25
            null
            0.4.25
            null
            null
            0.4.25
            0.5.14
            null
            null
            0.5.14
            0.5.14
            null
            0.4.25
            0.4.25
            0.4.25
            0.5.14
            null
            0.4.25
            0.5.14
            0.5.14
            null
            0.5.14
            0.5.14
            0.5.14
            0.5.14
            null
            0.5.14
            0.5.14
            0.5.14
            0.5.14
            0.4.25
            null
        """.trimIndent().split("\n")

    private val correctLinuxVersions =
        """
            0.5.13
            0.5.14
            0.4.26
            0.5.14
            0.5.14
            0.5.14
            0.5.14
            0.4.26
            0.4.26
            null
            0.4.26
            null
            null
            0.4.26
            0.5.14
            null
            null
            0.5.14
            0.5.14
            null
            0.4.26
            0.4.26
            0.4.26
            0.5.14
            null
            0.4.26
            0.5.14
            0.5.14
            null
            0.5.14
            0.5.14
            0.5.14
            0.5.14
            null
            0.5.14
            0.5.14
            0.5.14
            0.5.14
            0.4.26
            null
        """.trimIndent().split("\n")

    private val resolver = VersionResolver()

    @Test
    fun correctVersionsFromStringsAreObtained() {
        unsanitizedStrings.forEachIndexed(fun(index: Int, s: String) {
            assertEquals(correctVersionConstraints[index].split(", "), resolver.versionsFromString(s))
        })
    }

    @Test
    fun correctSolidityVersionFromConstraintsIsResolved() {
        // Filter is needed as test was written at 0.5.14 and new releases since this version will cause resolved version to be different from expected
        val releases = resolver.getSolcReleases().filter { Version.valueOf(it.version).lessThan(Version.valueOf("0.5.15")) }
        // Mac will not be able to do this as we don't have solc builds for every solc version tested here
        if (SystemUtils.IS_OS_WINDOWS) {
            verifyVersions(correctWindowsVersions, releases)
        } else if (SystemUtils.IS_OS_LINUX) {
            verifyVersions(correctLinuxVersions, releases)
        }
    }

    @Test
    fun linuxArm64SolidityVersionFromConstraintsIsResolved() {
        assumeTrue(SystemUtils.IS_OS_LINUX)
        assumeTrue(System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64"))

        val releases = listOf(
            SolcRelease(
                version = "0.8.30",
                linuxUrl = "https://example.com/solc-linux-amd64-v0.8.30",
            ),
            SolcRelease(
                version = "0.8.31",
                linuxUrl = "https://example.com/solc-linux-amd64-v0.8.31",
                linuxArm64Url = "https://example.com/solc-linux-arm64-v0.8.31",
            ),
        )

        assertEquals("0.8.31", resolver.getCompatibleVersions("^0.8.0", releases).lastOrNull()?.version)
        assertEquals(null, resolver.getCompatibleVersions("0.8.30", releases).lastOrNull())
    }

    private fun verifyVersions(versions: List<String>, releases: List<SolcRelease>) {
        unsanitizedStrings.forEachIndexed(fun(index: Int, s: String) {
            val correctVersion = versions[index]
            val resolvedVersion = resolver.getCompatibleVersions(s, releases).lastOrNull()
            if (resolvedVersion == null) {
                assertEquals(correctVersion, "null")
            } else {
                assertEquals(correctVersion, resolvedVersion.version)
            }
        })
    }
}
