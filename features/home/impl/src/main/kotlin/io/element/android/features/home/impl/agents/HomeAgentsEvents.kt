/*
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.agents

sealed interface HomeAgentsEvents {
    data object Refresh : HomeAgentsEvents

    /** Replace the agent's delegated scope set; empty set revokes the grant. */
    data class SetScopes(val name: String, val scopes: Set<String>) : HomeAgentsEvents

    data object ClearError : HomeAgentsEvents
}
