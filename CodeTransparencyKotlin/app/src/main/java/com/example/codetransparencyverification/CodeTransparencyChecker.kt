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

import android.content.pm.PackageInfo
import android.os.Build
import com.google.common.hash.Hashing
import com.google.common.io.ByteSource
import com.google.common.io.ByteStreams
import com.google.common.io.CharStreams
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jws.JsonWebSignature
import org.jose4j.lang.JoseException
import java.io.IOException
import java.io.InputStreamReader
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Class for verifying code transparency for a given package.  */
object CodeTransparencyChecker {
    private const val CODE_TRANSPARENCY_FILE_ENTRY_PATH = "META-INF/code_transparency_signed.jwt"

    /**
     * Checks code transparency for the given [PackageInfo] and returns [TransparencyCheckResult].
     */
    fun checkCodeTransparency(packageInfo: PackageInfo): TransparencyCheckResult {
        var result = TransparencyCheckResult()

        try {
            val keyCertificates: List<String> = getApkSigningKeyCertificates(packageInfo)
            result = result.copy(apkSigningKeyCertificateFingerprints = keyCertificates)

            val baseApkPath: String = packageInfo.applicationInfo.sourceDir
            val codeTransparencyJws: JsonWebSignature = getCodeTransparencyJws(baseApkPath)
            val transparencyKeyCertFingerPrint: String =
                checkCodeTransparencySignature(codeTransparencyJws)

            result = result.copy(
                isTransparencySignatureVerified = true,
                transparencyKeyCertificateFingerprint = transparencyKeyCertFingerPrint
            )

            val codeRelatedFilesFromTransparencyFile: Map<String, CodeRelatedFile> =
                Json.decodeFromString<CodeRelatedFiles>(codeTransparencyJws.unverifiedPayload)
                    .files.associateBy { codeRelatedFile ->
                        codeRelatedFile.sha256
                    }

            val splitApkPaths = packageInfo.splitApkPaths.map { path -> baseApkPath + path }
            val modifiedFiles: List<String> =
                findModifiedFiles(splitApkPaths, codeRelatedFilesFromTransparencyFile)

            result = result.copy(isFileContentsVerified = modifiedFiles.isEmpty())
        } catch (e: Exception) {
            result = result.copy(errorMessage = e.message)
        }

        return result
    }

    private fun getApkSigningKeyCertificates(packageInfo: PackageInfo): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (packageInfo.signingInfo.hasMultipleSigners()) {
                throw AssertionError("Play App Signing does not support multiple signers.")
            } else {
                packageInfo.signingInfo.signingCertificateHistory.map { signature ->
                    getCertificateFingerprint(signature.toByteArray())
                }
            }
        } else {
            packageInfo.signatures.map { signature ->
                getCertificateFingerprint(signature.toByteArray())
            }
        }

    private fun getCertificateFingerprint(certificate: X509Certificate): String =
        getCertificateFingerprint(certificate.encoded)

    private fun getCertificateFingerprint(encodedCertificate: ByteArray): String =
        getCertificateFingerprintBytes(encodedCertificate).joinToString(":") { byte ->
            byte.toString(16)
        }

    private fun getCertificateFingerprintBytes(encodedCertificate: ByteArray): ByteArray =
        ByteSource.wrap(encodedCertificate).hash(Hashing.sha256()).asBytes()

    private fun getCodeTransparencyJws(baseApkPath: String): JsonWebSignature =
        ZipFile(baseApkPath).use { baseApkFile ->
            val transparencyFileEntry: ZipEntry = baseApkFile.getEntry(CODE_TRANSPARENCY_FILE_ENTRY_PATH)
                ?: throw RuntimeException("Installed base APK does not contain code transparency file.")

            val serializedJwt: String =
                getSerializedCodeTransparencyJws(baseApkFile, transparencyFileEntry)
                    ?: throw RuntimeException("Error parsing the code transparency file.")

            try {
                JsonWebSignature.fromCompactSerialization(serializedJwt) as JsonWebSignature
            } catch (e: JoseException) {
                throw RuntimeException("Error constructing JsonWebSignature from compact serialization.")
            }
        }

    private fun getSerializedCodeTransparencyJws(
        baseApkFile: ZipFile,
        codeTransparencyEntry: ZipEntry
    ): String? = try {
        baseApkFile.getInputStream(codeTransparencyEntry).use { inputStream ->
            CharStreams.toString(InputStreamReader(inputStream))
        }
    } catch (e: IOException) {
        null
    }

    private fun checkCodeTransparencySignature(jws: JsonWebSignature): String = try {
        val publicKeyCert: X509Certificate = jws.leafCertificateHeaderValue
        jws.key = publicKeyCert.publicKey
        jws.setAlgorithmConstraints(
            AlgorithmConstraints(
                ConstraintType.PERMIT,
                AlgorithmIdentifiers.RSA_USING_SHA256
            )
        )
        if (jws.verifySignature()) {
            getCertificateFingerprint(jws.leafCertificateHeaderValue)
        } else {
            throw RuntimeException("Code transparency signature is invalid.")
        }
    } catch (e: Exception) {
        throw RuntimeException("Encountered error while verifying code transparency signature.")
    }

    private fun findModifiedFiles(
        installedApkPaths: List<String>,
        codeRelatedFilesFromTransparencyFile: Map<String, CodeRelatedFile>
    ): List<String> = installedApkPaths.flatMap { installedApkPath ->
        ZipFile(installedApkPath).use { apk ->
            findModifiedDexFiles(apk, codeRelatedFilesFromTransparencyFile) +
                findModifiedNativeLibraries(apk, codeRelatedFilesFromTransparencyFile)
        }
    }

    private fun findModifiedDexFiles(
        apk: ZipFile,
        codeRelatedFilesFromTransparencyFile: Map<String, CodeRelatedFile>
    ): List<String> = apk.entries()
        .toList()
        .filter { entry -> entry.isDexFile }
        .filter { entry ->
            val fileHash: String = getFileHash(apk, entry)
            !codeRelatedFilesFromTransparencyFile.containsKey(fileHash) ||
                codeRelatedFilesFromTransparencyFile[fileHash]?.type != CodeRelatedFile.Type.DEX
        }.map { entry -> entry.name }

    private fun findModifiedNativeLibraries(
        apk: ZipFile,
        codeRelatedFilesFromTransparencyFile: Map<String, CodeRelatedFile>
    ): List<String> = apk.entries()
        .toList()
        .filter { entry -> entry.isNativeLibrary }
        .filter { entry ->
            val fileHash: String = getFileHash(apk, entry)
            !codeRelatedFilesFromTransparencyFile.containsKey(fileHash) ||
                codeRelatedFilesFromTransparencyFile[fileHash]?.type !=
                CodeRelatedFile.Type.NATIVE_LIBRARY ||
                // For native libraries path to the file in the APK is known at code transparency
                // file generation time. This is not true for dex files, so APK path should not be
                // checked for them.
                codeRelatedFilesFromTransparencyFile[fileHash]?.apkPath != entry.name
        }.map { entry -> entry.name }

    private val ZipEntry.isDexFile: Boolean
        get() = name.endsWith(".dex")

    private val ZipEntry.isNativeLibrary: Boolean
        get() = name.endsWith(".so")

    private fun getFileHash(apkFile: ZipFile, zipEntry: ZipEntry): String =
        apkFile.getInputStream(zipEntry).use { inputStream ->
            ByteSource.wrap(ByteStreams.toByteArray(inputStream))
                .hash(Hashing.sha256())
                .toString()
        }

    private val PackageInfo.splitApkPaths: List<String>
        get() = applicationInfo.splitSourceDirs?.toList() ?: emptyList()
}
