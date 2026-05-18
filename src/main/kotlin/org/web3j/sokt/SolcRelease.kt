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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.commons.lang3.SystemUtils

@Serializable
data class SolcRelease(
    val version: String,
    @SerialName("windows_url") val windowsUrl: String = "",
    @SerialName("linux_url") val linuxUrl: String = "",
    @SerialName("mac_url") val macUrl: String = "",
) {
    fun downloadUrl(): String {
        return when {
            SystemUtils.IS_OS_WINDOWS -> windowsUrl
            SystemUtils.IS_OS_LINUX -> linuxUrl
            SystemUtils.IS_OS_MAC -> macUrl
            else -> ""
        }
    }

    fun isWindowsArchive(): Boolean {
        return SystemUtils.IS_OS_WINDOWS && downloadUrl().lowercase().endsWith(".zip")
    }

    fun isCompatibleWithOs(): Boolean {
        return downloadUrl().isNotBlank()
    }
}
