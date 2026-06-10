/*
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.features.login.impl.R
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import kotlinx.coroutines.launch
import su.kumo.byoc.CloudInput
import su.kumo.byoc.Kumo
import su.kumo.byoc.KumoException
import timber.log.Timber

/**
 * BYOC cloud onboarding: sign in to the user's realm (hosted kumo.su or a
 * self-hosted domain), make sure their matrix homeserver backend exists
 * (installing it from the realm catalog when missing), and hand the resolved
 * homeserver to the regular login flow via [onHomeserverResolved].
 */
@Composable
internal fun CloudSignInSection(
    enabled: Boolean,
    onHomeserverResolved: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDomainDialog by rememberSaveable { mutableStateOf(false) }

    val provisioningMessage = stringResource(R.string.screen_onboarding_cloud_provisioning)

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data == null) {
            busyMessage = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                busyMessage = provisioningMessage
                Kumo.completeSignIn(data)
                val backend = Kumo.ensureBackend("matrix")
                // The "_" ingress is the matrix server_name host (serves the
                // /.well-known/matrix documents the login flow discovers).
                val homeserver = backend.outputs["_"]
                    ?.removePrefix("https://")
                    ?.removePrefix("http://")
                    ?.trimEnd('/')
                if (homeserver.isNullOrEmpty()) {
                    errorMessage = "Your matrix backend has no public address yet — try again in a minute"
                } else {
                    onHomeserverResolved(homeserver)
                }
            } catch (e: KumoException) {
                Timber.w(e, "Cloud sign-in failed")
                errorMessage = e.message
            } finally {
                busyMessage = null
            }
        }
    }

    fun startSignIn(input: CloudInput) {
        scope.launch {
            try {
                busyMessage = provisioningMessage
                authLauncher.launch(Kumo.prepareSignIn(input))
            } catch (e: KumoException) {
                Timber.w(e, "Cloud sign-in failed")
                errorMessage = e.message
                busyMessage = null
            }
        }
    }

    Button(
        text = stringResource(R.string.screen_onboarding_cloud_sign_in_kumosu),
        showProgress = busyMessage != null,
        enabled = enabled && busyMessage == null,
        onClick = { startSignIn(CloudInput.KumoSu) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        text = stringResource(R.string.screen_onboarding_cloud_sign_in_private),
        enabled = enabled && busyMessage == null,
        onClick = { showDomainDialog = true },
        modifier = Modifier.fillMaxWidth(),
    )

    if (showDomainDialog) {
        var domain by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDomainDialog = false },
            title = { Text(stringResource(R.string.screen_onboarding_cloud_domain_title)) },
            text = {
                TextField(
                    value = domain,
                    onValueChange = { domain = it },
                    placeholder = stringResource(R.string.screen_onboarding_cloud_domain_hint),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.screen_onboarding_cloud_connect),
                    enabled = domain.isNotBlank(),
                    onClick = {
                        showDomainDialog = false
                        startSignIn(CloudInput.Domain(domain.trim()))
                    },
                )
            },
            dismissButton = {
                TextButton(
                    text = stringResource(io.element.android.libraries.ui.strings.CommonStrings.action_cancel),
                    onClick = { showDomainDialog = false },
                )
            },
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(io.element.android.libraries.ui.strings.CommonStrings.common_error)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    text = stringResource(io.element.android.libraries.ui.strings.CommonStrings.action_ok),
                    onClick = { errorMessage = null },
                )
            },
        )
    }
}
