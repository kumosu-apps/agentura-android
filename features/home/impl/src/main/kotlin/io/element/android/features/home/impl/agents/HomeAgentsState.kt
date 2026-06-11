/*
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.agents

import io.element.android.libraries.architecture.AsyncData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

data class HomeAgentsState(
    /** Whether a BYOC cloud session exists (agents are managed via the realm). */
    val isSignedIn: Boolean,
    val agents: AsyncData<ImmutableList<AgentInfo>>,
    /** Installation name whose grant is currently being saved, if any. */
    val savingAgent: String?,
    val errorMessage: String?,
    val eventSink: (HomeAgentsEvents) -> Unit,
)

/** One installed agent and its realm-access grant state. */
data class AgentInfo(
    /** Installation name (app-<uuid>) — the API handle. */
    val name: String,
    /** Catalog name, e.g. "hermes" — also the agent's Matrix localpart. */
    val appName: String,
    val phase: String?,
    /** Whether this agent can receive a realm-access grant at all. */
    val grantSupported: Boolean,
    /** Scopes the user has currently delegated (empty = no grant). */
    val grantedScopes: ImmutableSet<String>,
) {
    val ready: Boolean get() = phase == "Ready"
}
