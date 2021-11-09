/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.codetransparencyverification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a code related file (dex file or a native library) parsed from a Code Transparency
 * file.
 */
@Serializable
data class CodeRelatedFiles(
    @SerialName("codeRelatedFile")
    val files: List<CodeRelatedFile>,
)

@Serializable
data class CodeRelatedFile(
    /** Type of the code related file. */
    val type: Type,
    val sha256: String,
    /**
     * Path to the file in the APK. Only set if [type] is [Type.NATIVE_LIBRARY].
     */
    val apkPath: String? = null
) {
    enum class Type {
        DEX, NATIVE_LIBRARY
    }
}
