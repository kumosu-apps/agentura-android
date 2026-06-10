/*
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
plugins {
    id("io.element.android-library")
}

android {
    namespace = "su.kumo.byoc"
}

dependencies {
    // api: hosts use AppAuth types (auth Intent flow) and OkHttp for their own
    // bearer-injecting interceptors.
    api("net.openid:appauth:0.11.1")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.coroutines.core)
}
