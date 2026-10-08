package com.wynime.app.ui.settings.account

import androidx.compose.runtime.Composable
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_account_tracking_sync_login_expired
import com.wynime.app.ui.lang.settings_account_tracking_sync_network_error
import com.wynime.app.ui.lang.settings_account_tracking_sync_rate_limited
import com.wynime.app.ui.lang.settings_account_tracking_sync_unknown_error
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun selfInfoLoadErrorText(error: LoadError): String = when (error) {
    LoadError.RequiresLogin -> stringResource(Lang.settings_account_tracking_sync_login_expired)
    LoadError.NetworkError -> stringResource(Lang.settings_account_tracking_sync_network_error)
    LoadError.RateLimited -> stringResource(Lang.settings_account_tracking_sync_rate_limited)
    is LoadError.RequestError,
    is LoadError.UnknownError,
    LoadError.NoResults,
    LoadError.ServiceUnavailable,
    -> stringResource(Lang.settings_account_tracking_sync_unknown_error)
}
