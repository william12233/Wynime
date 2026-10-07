package com.wynime.app.ui.oauth

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.wynime.app.domain.session.auth.OAuthPlatform
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.navigation.rememberAsyncBrowserNavigator
import com.wynime.app.ui.lang.*

import org.jetbrains.compose.resources.*

@Composable
fun OAuthAuthorizeScreen(
    vm: OAuthAuthorizeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateSettings: () -> Unit,
    onAuthorizeSuccess: () -> Unit,
    contactActions: @Composable () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle(AuthState.NoBangumiAccount)
    val scope = rememberCoroutineScope()
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.collectNewLoginEvent {
            onAuthorizeSuccess()
        }
    }

    suspend fun startOAuth(currentState: AuthState) {
        if (currentState is AuthState.AwaitingResult) return
        vm.doOAuth(
            currentState is AuthState.NoBangumiAccount || (currentState is AuthState.Failed && !currentState.loggedIn),
        ) {
            browserNavigator.openBrowser(context, it)
        }
    }

    if (vm.platform.startsAuthorizationImmediately) {
        LaunchedEffect(vm) {

            startOAuth(vm.state.first { it is AuthState.Idle })
        }
    }

    OAuthAuthorizeScreen(
        platform = vm.platform,
        state = state,
        onClickAuthorize = {
            scope.launch { startOAuth(state) }
        },
        onCancelAuthorize = { vm.cancelCurrentOAuth() },
        onNavigateSettings = onNavigateSettings,
        onNavigateBack = onNavigateBack,
        contactActions = contactActions,
    )
}

@Composable
internal fun OAuthAuthorizeScreen(
    platform: OAuthPlatform,
    state: AuthState,
    onClickAuthorize: () -> Unit,
    onCancelAuthorize: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    contactActions: @Composable () -> Unit,
) {
    AuthorizationScreenLayout(
        onNavigateSettings = onNavigateSettings,
        onNavigateBack = onNavigateBack,
        title = { Text(stringResource(Lang.oauth_authorize_title, platform.displayName)) },
    ) { scrollState ->
        OAuthAuthorizeLayout(
            platform = platform,
            authorizeState = state,
            contactActions = contactActions,
            onClickAuthorize = onClickAuthorize,
            onCancelAuthorize = onCancelAuthorize,
            scrollState = scrollState,
        )
    }
}

private val OAuthPlatform.startsAuthorizationImmediately: Boolean
    get() = this != OAuthPlatform.BANGUMI
