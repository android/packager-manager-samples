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

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.codetransparencyverification.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collect

/** Entry point for on device code transparency verification sample app.  */
@SuppressLint("InlinedApi", "SetTextI18n")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater).apply {
            packageName.setText(applicationContext.packageName)
            verifyButton.setOnClickListener {
                val packageInfo: PackageInfo? = getPackageInfo()

                if (packageInfo == null) {
                    binding.verificationResult.text =
                      getString(R.string.package_not_found, packageName)
                } else {
                    viewModel.fetchCodeTransparency(packageInfo)
                }
            }
            setContentView(root)
        }

        lifecycleScope.launchWhenStarted {
            viewModel.state.collect { result ->
                val logs = StringBuilder()
                logs.appendLine(getString(R.string.public_key_certificates))
                result
                    .apkSigningKeyCertificateFingerprints
                    .forEach { certFingerprint -> logs.appendLine(certFingerprint) }

                if (result.isVerified) {
                    logs
                        .appendLine(getString(R.string.code_transparency_verified))
                        .appendLine(result.transparencyKeyCertificateFingerprint)
                } else {
                    logs.appendLine(result.errorMessage)
                }
                binding.verificationResult.text = logs.toString()
            }
        }
    }

    private fun getPackageInfo(): PackageInfo? =
        try {
            applicationContext.packageManager.getPackageInfo(
                packageName,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                }
            )
        } catch (e: PackageManager.NameNotFoundException) {
            // Note, that starting from API level 30 getPackageInfo will only work for apps that are
            // specified in the queries element in AndroidManifest.xml.
            // Read more: https://developer.android.com/training/package-visibility
            null
        }
}
