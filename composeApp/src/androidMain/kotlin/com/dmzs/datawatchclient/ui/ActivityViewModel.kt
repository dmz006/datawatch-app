package com.dmzs.datawatchclient.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * A view-model shared by the whole activity rather than one per navigation
 * destination.
 *
 * Plain `viewModel()` inside a NavHost destination is scoped to that back-stack
 * entry, so a default parameter like `alertsVm: AlertsViewModel = viewModel()`
 * created a separate instance, with its own 5 s poll, on every tab the user
 * visited. The 2026-10-14 profile measured 7 alert pollers at once. Use this
 * for view-models that hold app-wide state (alerts) so every screen gets the
 * single instance AppRoot already created.
 */
@Composable
public inline fun <reified VM : ViewModel> activityViewModel(): VM {
    val owner = LocalContext.current.findComponentActivity() ?: LocalViewModelStoreOwner.current
    return if (owner != null) viewModel(viewModelStoreOwner = owner) else viewModel()
}

public tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
