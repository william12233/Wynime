package com.wynime.app.ui.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.icons.Passkey_24dp_E8EAED_FILL0_wght400_GRAD0_opsz24
import com.wynime.app.ui.foundation.setClipEntryText
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.foundation_load_error_copied_feedback
import com.wynime.app.ui.lang.foundation_load_error_network
import com.wynime.app.ui.lang.foundation_load_error_no_details
import com.wynime.app.ui.lang.foundation_load_error_no_results
import com.wynime.app.ui.lang.foundation_load_error_rate_limited
import com.wynime.app.ui.lang.foundation_load_error_request_error
import com.wynime.app.ui.lang.foundation_load_error_requires_login
import com.wynime.app.ui.lang.foundation_load_error_service_unavailable
import com.wynime.app.ui.lang.foundation_load_error_unknown_with_message
import com.wynime.app.ui.lang.login_sign_in
import com.wynime.app.ui.lang.settings_mediasource_copy
import com.wynime.app.ui.lang.settings_mediasource_retry
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T : Any> LazyPagingItems<T>.rememberLoadErrorState(): State<LoadError?> {
    return remember(this) {
        derivedStateOf {
            LoadError.fromCombinedLoadStates(loadState)
        }
    }
}

@Composable
fun LoadErrorCard(
    error: LoadError?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = run {
        val navigator = LocalNavigator.current
        { navigator.navigateBangumiAuthorize() }
    },
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = LoadErrorDefaults.containerColor,
    elevation: CardElevation? = null,
) {
    if (error == null) return
    val role = LoadErrorCardRole.from(error)
    val retryText = stringResource(Lang.settings_mediasource_retry)
    val signInText = stringResource(Lang.login_sign_in)
    val copyText = stringResource(Lang.settings_mediasource_copy)
    val copiedFeedbackText = stringResource(Lang.foundation_load_error_copied_feedback)

    val retryButton = @Composable {
        SearchDefaults.IconTextButton(
            onRetry,
            leadingIcon = { iconModifier ->
                Icon(
                    Icons.Rounded.Refresh, null,
                    iconModifier,
                )
            },
            text = { Text(retryText) },
        )
    }

    val content = @Composable { cardColors: CardColors ->
        val listItemColors = ListItemDefaults.colors(
            containerColor = cardColors.containerColor,
            leadingIconColor = cardColors.contentColor,
            trailingIconColor = cardColors.contentColor,
            headlineColor = cardColors.contentColor,
        )

        when (error) {
            LoadError.NetworkError -> {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.WifiOff, null) },
                    headlineContent = { Text(renderLoadErrorMessage(error)) },
                    trailingContent = retryButton,
                    colors = listItemColors,
                )
            }

            LoadError.RateLimited -> {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.ErrorOutline, null) },
                    headlineContent = { Text(renderLoadErrorMessage(error)) },
                    trailingContent = retryButton,
                    colors = listItemColors,
                )
            }

            LoadError.ServiceUnavailable -> {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.CloudOff, null) },
                    headlineContent = { Text(renderLoadErrorMessage(error)) },
                    trailingContent = retryButton,
                    colors = listItemColors,
                )
            }

            LoadError.NoResults -> {
                ListItem(
                    leadingContent = { Spacer(Modifier.size(24.dp)) },
                    headlineContent = { Text(renderLoadErrorMessage(error)) },
                    colors = listItemColors,
                )
            }

            LoadError.RequiresLogin -> {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Passkey_24dp_E8EAED_FILL0_wght400_GRAD0_opsz24, null) },
                    headlineContent = { Text(renderLoadErrorMessage(error)) },
                    trailingContent = {
                        SearchDefaults.IconTextButton(
                            onLogin,
                            leadingIcon = { iconModifier ->
                                Icon(
                                    Icons.AutoMirrored.Rounded.Login, null,
                                    iconModifier,
                                )
                            },
                            text = { Text(signInText) },
                        )
                    },
                    colors = listItemColors,
                )
            }

            is LoadError.UnknownError,
            is LoadError.RequestError -> {
                val e = when (error) {
                    is LoadError.UnknownError -> error.throwable
                    is LoadError.RequestError -> error.throwable
                    else -> null
                }
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.ErrorOutline, null) },
                    headlineContent = { Text(renderLoadErrorMessage(error)) },
                    trailingContent = {
                        Row {
                            if (currentWynimeBuildConfig.isDebug) {
                                TextButton({ e?.printStackTrace() }) {
                                    Text("Dump", fontStyle = FontStyle.Italic)
                                }
                            } else {
                                val clipboard = LocalClipboard.current
                                val scope = rememberCoroutineScope()
                                val toaster = LocalToaster.current
                                TextButton(
                                    {
                                        scope.launch {
                                            clipboard.setClipEntryText(
                                                e?.stackTraceToString() ?: "null",
                                            )
                                        }
                                        @OptIn(DelicateCoroutinesApi::class)
                                        GlobalScope.launch {
                                            logger<LoadError>().error(e) {
                                                "<User clicked copy, I'm just printing the stack trace>"
                                            }
                                        }
                                        toaster.toast(copiedFeedbackText)
                                    },
                                ) {
                                    Text(copyText)
                                }
                            }

                            retryButton()
                        }
                    },
                    colors = listItemColors,
                )
            }
        }
    }

    LoadErrorCardLayout(
        role,
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
        elevation = elevation,
        content = {
            content(cardColors)
        },
    )
}

@Composable
fun LoadErrorCardLayout(
    role: LoadErrorCardRole,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = LoadErrorDefaults.containerColor,
    elevation: CardElevation? = null,
    content: @Composable (LoadErrorCardScope.() -> Unit),
) {
    role.Container(modifier, containerColor, shape, elevation, content)
}

@Stable
interface LoadErrorCardScope {
    val cardColors: CardColors
        @Composable get

    val listItemColors: ListItemColors
        @Composable get() = cardColors.run {
            ListItemDefaults.colors(
                containerColor = containerColor,
                leadingIconColor = contentColor,
                trailingIconColor = contentColor,
                headlineColor = contentColor,
            )
        }
}

@Stable
object LoadErrorDefaults {
    val containerColor
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
fun renderLoadErrorMessage(error: LoadError): String {
    return when (error) {
        LoadError.NetworkError -> stringResource(Lang.foundation_load_error_network)
        LoadError.RateLimited -> stringResource(Lang.foundation_load_error_rate_limited)
        LoadError.ServiceUnavailable -> stringResource(Lang.foundation_load_error_service_unavailable)
        LoadError.NoResults -> stringResource(Lang.foundation_load_error_no_results)
        LoadError.RequiresLogin -> stringResource(Lang.foundation_load_error_requires_login)
        is LoadError.UnknownError -> {
            error.throwable?.printStackTrace()
            stringResource(
                Lang.foundation_load_error_unknown_with_message,
                error.throwable?.message ?: stringResource(Lang.foundation_load_error_no_details),
            )
        }

        is LoadError.RequestError -> stringResource(Lang.foundation_load_error_request_error, error.localized)
    }
}

@Composable
@PreviewLightDark
private fun PreviewLoadErrorCard() {
    PreviewLoadErrorCardImpl(LoadError.UnknownError(IllegalStateException("test")))
}

@Composable
private fun PreviewLoadErrorCardImpl(error: LoadError?) {
    ProvideCompositionLocalsForPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            LoadErrorCard(error, {}, Modifier.padding(all = 16.dp), {})
        }
    }
}
