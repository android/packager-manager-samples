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

/** Represents result of code transparency verification. */
data class TransparencyCheckResult(
    /** Whether code transparency signature was successfully verified. */
    val isTransparencySignatureVerified: Boolean = false,
    /** Whether code transparency file contents were successfully verified. */
    val isFileContentsVerified: Boolean = false,
    /**
     * SHA-256 Fingerprint of the public key certificate that was used for verifying code
     * transparency signature. Only set when {@link #transparencySignatureVerified()} is true.
     *
     * <p>Must be compared to the developer's public key manually.
     */
    val transparencyKeyCertificateFingerprint: String? = null,
    /**
     * SHA-256 fingerprints of the APK signing certificates associated with the package. Starting
     * from API level 28, this includes both the signing certificate associated with the signer of
     * the package and the past signing certificates it included as its proof of signing certificate
     * rotation.
     *
     * <p>When determining if a package is signed by a desired certificate, the returned list should
     * be checked to determine if it corresponds to one of the entries.
     */
    val apkSigningKeyCertificateFingerprints: List<String> = emptyList(),
    /**
     * Error message containing information about the cause of code transparency verification
     * failure. Only set when code transparency verification fails.
     */
    val errorMessage: String? = null
) {
    val isVerified: Boolean
        get() = isTransparencySignatureVerified && isFileContentsVerified
}
