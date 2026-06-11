/*
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.agents

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.architecture.AsyncData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import su.kumo.byoc.RealmApiGrant

open class HomeAgentsStateProvider : PreviewParameterProvider<HomeAgentsState> {
    override val values: Sequence<HomeAgentsState>
        get() = sequenceOf(
            aHomeAgentsState(isSignedIn = false),
            aHomeAgentsState(agents = AsyncData.Loading()),
            aHomeAgentsState(agents = AsyncData.Success(persistentListOf())),
            aHomeAgentsState(
                agents = AsyncData.Success(
                    persistentListOf(
                        anAgentInfo(),
                        anAgentInfo(
                            name = "app-2",
                            appName = "scribe",
                            phase = "Pending",
                            grantedScopes = persistentSetOf(),
                        ),
                    )
                ),
            ),
            aHomeAgentsState(
                agents = AsyncData.Success(persistentListOf(anAgentInfo())),
                savingAgent = "app-1",
            ),
        )
}

fun aHomeAgentsState(
    isSignedIn: Boolean = true,
    agents: AsyncData<kotlinx.collections.immutable.ImmutableList<AgentInfo>> = AsyncData.Uninitialized,
    savingAgent: String? = null,
    errorMessage: String? = null,
) = HomeAgentsState(
    isSignedIn = isSignedIn,
    agents = agents,
    savingAgent = savingAgent,
    errorMessage = errorMessage,
    eventSink = {},
)

fun anAgentInfo(
    name: String = "app-1",
    appName: String = "hermes",
    phase: String? = "Ready",
    grantSupported: Boolean = true,
    grantedScopes: kotlinx.collections.immutable.ImmutableSet<String> = persistentSetOf(RealmApiGrant.SCOPE_APPS_READ),
) = AgentInfo(
    name = name,
    appName = appName,
    phase = phase,
    grantSupported = grantSupported,
    grantedScopes = grantedScopes,
)
