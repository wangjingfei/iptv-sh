package com.wangjingfei.hometv.update

import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Checking(val manual: Boolean) : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val manifest: UpdateManifest, val showPrompt: Boolean = true) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val progress: Float) : UpdateState
    data class Verifying(val manifest: UpdateManifest) : UpdateState
    data class Ready(val manifest: UpdateManifest, val apk: File, val showPrompt: Boolean = true) : UpdateState
    data class PermissionRequired(val manifest: UpdateManifest, val apk: File, val showPrompt: Boolean = true) : UpdateState
    data class Error(
        val message: String,
        val manifest: UpdateManifest? = null,
        val apk: File? = null,
        val showPrompt: Boolean = false,
    ) : UpdateState
}

internal fun shouldResumePendingInstall(state: UpdateState, requiresPermission: Boolean): Boolean =
    state is UpdateState.PermissionRequired && !requiresPermission
