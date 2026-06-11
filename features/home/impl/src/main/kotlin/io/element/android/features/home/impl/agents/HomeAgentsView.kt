/*
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.home.impl.R
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Checkbox
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text
import su.kumo.byoc.RealmApiGrant

@Composable
fun HomeAgentsView(
    state: HomeAgentsState,
    lazyListState: LazyListState,
    contentPadding: PaddingValues,
    onAgentChatClick: (AgentInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !state.isSignedIn -> EmptyAgentsView(
            message = stringResource(R.string.screen_home_agents_signed_out),
            modifier = modifier,
        )
        state.agents is AsyncData.Loading && state.agents.dataOrNull() == null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.agents.dataOrNull().isNullOrEmpty() -> EmptyAgentsView(
            message = stringResource(R.string.screen_home_agents_empty),
            modifier = modifier,
        )
        else -> {
            val agents = state.agents.dataOrNull().orEmpty()
            LazyColumn(
                modifier = modifier,
                state = lazyListState,
                contentPadding = contentPadding,
            ) {
                items(agents, key = { it.name }) { agent ->
                    AgentCard(
                        agent = agent,
                        saving = state.savingAgent == agent.name,
                        onChatClick = { onAgentChatClick(agent) },
                        onScopesChange = { scopes ->
                            state.eventSink(HomeAgentsEvents.SetScopes(agent.name, scopes))
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    state.errorMessage?.let { message ->
        ErrorDialog(
            content = message,
            onSubmit = { state.eventSink(HomeAgentsEvents.ClearError) },
        )
    }
}

@Composable
private fun AgentCard(
    agent: AgentInfo,
    saving: Boolean,
    onChatClick: () -> Unit,
    onScopesChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.appName,
                    style = ElementTheme.typography.fontHeadingSmMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                Text(
                    text = agent.phase ?: stringResource(R.string.screen_home_agents_phase_unknown),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(
                    text = stringResource(R.string.screen_home_agents_open_chat),
                    enabled = agent.ready,
                    onClick = onChatClick,
                )
            }
        }
        if (agent.grantSupported) {
            Text(
                text = stringResource(R.string.screen_home_agents_realm_access_title),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.screen_home_agents_realm_access_subtitle),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
            ScopeRow(
                label = stringResource(R.string.screen_home_agents_scope_apps_read),
                scope = RealmApiGrant.SCOPE_APPS_READ,
                agent = agent,
                enabled = !saving,
                onScopesChange = onScopesChange,
            )
            ScopeRow(
                label = stringResource(R.string.screen_home_agents_scope_apps_install),
                scope = RealmApiGrant.SCOPE_APPS_INSTALL,
                agent = agent,
                enabled = !saving,
                onScopesChange = onScopesChange,
            )
            ScopeRow(
                label = stringResource(R.string.screen_home_agents_scope_drive_read),
                scope = RealmApiGrant.SCOPE_DRIVE_READ,
                agent = agent,
                enabled = !saving,
                onScopesChange = onScopesChange,
            )
        }
    }
}

@Composable
private fun ScopeRow(
    label: String,
    scope: String,
    agent: AgentInfo,
    enabled: Boolean,
    onScopesChange: (Set<String>) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = scope in agent.grantedScopes,
            enabled = enabled,
            onCheckedChange = { checked ->
                val scopes = if (checked) agent.grantedScopes + scope else agent.grantedScopes - scope
                onScopesChange(scopes)
            },
        )
        Text(
            text = label,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun EmptyAgentsView(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BigIcon(style = BigIcon.Style.Default(ImageVector.vectorResource(R.drawable.ic_magic_wand)))
            Text(
                text = message,
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun HomeAgentsViewPreview(
    @PreviewParameter(HomeAgentsStateProvider::class) state: HomeAgentsState,
) = ElementPreview {
    HomeAgentsView(
        state = state,
        lazyListState = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = 112.dp),
        onAgentChatClick = {},
    )
}
