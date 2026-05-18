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
import org.junit.jupiter.api.Assertions.assertEquals
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

    private val resolver = VersionResolver()

    @Test
    fun correctVersionsFromStringsAreObtained() {
        unsanitizedStrings.forEachIndexed(fun(index: Int, s: String) {
            assertEquals(correctVersionConstraints[index].split(", "), resolver.versionsFromString(s))
        })
    }

    @Test
    fun officialSolcIndexIsConvertedToStableReleases() {
        val releases = resolver.solcReleasesFromOfficialIndex(
            """
            {
              "builds": [
                {
                  "path": "solc-linux-amd64-v0.8.35-pre.1+commit.a99b6d8c",
                  "version": "0.8.35",
                  "prerelease": "pre.1"
                },
                {
                  "path": "solc-linux-amd64-v0.8.34+commit.80d5c536",
                  "version": "0.8.34"
                },
                {
                  "path": "solc-linux-amd64-v0.8.35+commit.47b9dedd",
                  "version": "0.8.35"
                }
              ],
              "releases": {
                "0.8.35": "solc-linux-amd64-v0.8.35+commit.47b9dedd",
                "0.8.34": "solc-linux-amd64-v0.8.34+commit.80d5c536"
              },
              "latestRelease": "0.8.35"
            }
            """.trimIndent(),
            SolcPlatform.LINUX_AMD64,
        )

        assertEquals(listOf("0.8.34", "0.8.35"), releases.map { it.version })
        assertEquals(
            "https://binaries.soliditylang.org/linux-amd64/solc-linux-amd64-v0.8.35+commit.47b9dedd",
            releases.last().linuxUrl,
        )
    }

    @Test
    fun compatibleVersionsAreResolvedFromStableReleaseSet() {
        val releases =
            listOf("0.4.0", "0.4.2", "0.4.21", "0.4.23", "0.4.25", "0.4.26", "0.5.0", "0.5.13", "0.5.14")
                .map(::releaseForCurrentOs)

        verifyVersion(">=0.5.10<0.5.14;", "0.5.13", releases)
        verifyVersion("^0.4.2;", "0.4.26", releases)
        verifyVersion(">0.4.23 <0.5.0;", "0.4.26", releases)
        verifyVersion("0.4.2;", "0.4.2", releases)
        verifyVersion("0.4.0;", "0.4.0", releases)
        verifyVersion(">=0.4.0 <0.4.8;", "0.4.2", releases)
        verifyVersion("~0.4.24;", "0.4.26", releases)
        verifyVersion("~0.4.24 >=0.5;", null, releases)
    }

    private fun verifyVersion(pragma: String, expectedVersion: String?, releases: List<SolcRelease>) {
        assertEquals(expectedVersion, resolver.getCompatibleVersions(pragma, releases).lastOrNull()?.version)
    }

    private fun releaseForCurrentOs(version: String): SolcRelease {
        val url = "https://example.invalid/$version/solc"
        return when {
            SystemUtils.IS_OS_WINDOWS -> SolcRelease(version = version, windowsUrl = "$url.exe")
            SystemUtils.IS_OS_LINUX -> SolcRelease(version = version, linuxUrl = url)
            SystemUtils.IS_OS_MAC -> SolcRelease(version = version, macUrl = url)
            else -> SolcRelease(version = version)
        }
    }
}
