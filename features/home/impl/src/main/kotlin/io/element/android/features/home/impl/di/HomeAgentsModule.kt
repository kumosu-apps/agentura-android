/*
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import io.element.android.features.home.impl.agents.HomeAgentsPresenter
import io.element.android.features.home.impl.agents.HomeAgentsState
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.SessionScope

@BindingContainer
@ContributesTo(SessionScope::class)
interface HomeAgentsModule {
    @Binds
    fun bindHomeAgentsPresenter(presenter: HomeAgentsPresenter): Presenter<HomeAgentsState>
}
