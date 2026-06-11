/*
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.agents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.launch
import su.kumo.byoc.Backend
import su.kumo.byoc.Kumo
import su.kumo.byoc.KumoException
import timber.log.Timber

@Inject
class HomeAgentsPresenter : Presenter<HomeAgentsState> {
    @Composable
    override fun present(): HomeAgentsState {
        val scope = rememberCoroutineScope()
        val isSignedIn = remember { Kumo.isSignedIn }
        var refreshKey by remember { mutableIntStateOf(0) }
        var agents by remember { mutableStateOf<AsyncData<ImmutableList<AgentInfo>>>(AsyncData.Uninitialized) }
        var savingAgent by remember { mutableStateOf<String?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(refreshKey) {
            if (!isSignedIn) return@LaunchedEffect
            agents = AsyncData.Loading(agents.dataOrNull())
            agents = try {
                AsyncData.Success(Kumo.agents().map { it.toAgentInfo() }.toImmutableList())
            } catch (e: KumoException) {
                Timber.w(e, "Loading agents failed")
                errorMessage = e.message
                AsyncData.Failure(e, agents.dataOrNull())
            }
        }

        fun handleEvent(event: HomeAgentsEvents) {
            when (event) {
                HomeAgentsEvents.Refresh -> refreshKey++
                HomeAgentsEvents.ClearError -> errorMessage = null
                is HomeAgentsEvents.SetScopes -> {
                    if (savingAgent != null) return
                    savingAgent = event.name
                    scope.launch {
                        try {
                            if (event.scopes.isEmpty()) {
                                Kumo.revokeRealmGrant(event.name)
                            } else {
                                Kumo.setRealmGrant(event.name, event.scopes)
                            }
                            refreshKey++
                        } catch (e: KumoException) {
                            Timber.w(e, "Updating agent grant failed")
                            errorMessage = e.message
                        } finally {
                            savingAgent = null
                        }
                    }
                }
            }
        }

        return HomeAgentsState(
            isSignedIn = isSignedIn,
            agents = agents,
            savingAgent = savingAgent,
            errorMessage = errorMessage,
            eventSink = ::handleEvent,
        )
    }
}

private fun Backend.toAgentInfo() = AgentInfo(
    name = name,
    appName = appName,
    phase = phase,
    grantSupported = realmApi?.supported == true,
    grantedScopes = (realmApi?.takeIf { it.granted }?.scopes ?: emptySet()).toImmutableSet(),
)
