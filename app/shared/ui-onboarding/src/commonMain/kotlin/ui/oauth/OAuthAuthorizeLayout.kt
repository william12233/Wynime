package com.wynime.app.ui.oauth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.wynime.app.domain.foundation.LoadError
import com.wynime.app.domain.session.auth.OAuthPlatform
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.animation.WynimeMotionScheme
import com.wynime.app.ui.foundation.animation.AnimatedVisibilityMotionScheme
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.icons.OAuthPlatformIcon
import com.wynime.app.ui.foundation.widgets.HeroIcon
import com.wynime.app.ui.lang.*
import com.wynime.app.ui.search.renderLoadErrorMessage
import com.wynime.app.ui.settings.SettingsTab
import com.wynime.app.ui.settings.framework.components.SettingsScope
import com.wynime.app.ui.settings.framework.components.TextItem
import org.jetbrains.compose.resources.*

sealed interface AuthState {
    data class LoggedInBangumi(val bound: Boolean) : Idle

    data object NoBangumiAccount : Idle

    sealed interface Idle : AuthState

    data object AwaitingResult : AuthState

    data object Success : AuthState
    class Failed(val error: LoadError, val loggedIn: Boolean) : AuthState
}

@Composable
fun OAuthAuthorizeLayout(
    platform: OAuthPlatform,
    authorizeState: AuthState,
    contactActions: @Composable () -> Unit,
    onClickAuthorize: () -> Unit,
    onCancelAuthorize: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    SettingsTab(modifier) {
        val motionScheme = LocalWynimeMotionScheme.current
        Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(SettingsScope.itemVerticalSpacing),
        ) {
            HeroIcon {
                OAuthPlatformIcon(platform, Modifier.fillMaxSize())
            }
            Column {
                if (platform == OAuthPlatform.BANGUMI) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(Lang.oauth_bangumi_description),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth(),
                ) {
                    AuthorizeButton(
                        platform,
                        authorizeState,
                        onClick = onClickAuthorize,
                        onClickCancel = onCancelAuthorize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                    )
                    AuthorizeStateText(
                        authorizeState,
                        modifier = Modifier.padding(vertical = 8.dp),
                        animatedVisibilityMotionScheme = motionScheme.animatedVisibility,
                    )
                }
            }
            if (platform == OAuthPlatform.BANGUMI) {
                AuthorizeHelpQA(
                    contactActions = contactActions,
                    scrollState = scrollState,
                    Modifier.padding(top = 36.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AuthorizeButton(
    platform: OAuthPlatform,
    authorizeState: AuthState,
    onClick: () -> Unit,
    onClickCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable RowScope.() -> Unit = remember(authorizeState) {
        {
            AnimatedContent(
                targetState = authorizeState,
                transitionSpec = LocalWynimeMotionScheme.current.animatedContent.standard,
            ) {
                when (it) {
                    is AuthState.LoggedInBangumi -> {
                        Text(stringResource(Lang.oauth_bind_account, platform.displayName))
                    }

                    is AuthState.Idle, is AuthState.Failed -> {
                        Text(stringResource(Lang.oauth_sign_in_or_sign_up))
                    }

                    is AuthState.AwaitingResult -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 3.dp,
                            )
                            Text(stringResource(Lang.oauth_waiting_result))
                        }
                    }

                    is AuthState.Success -> {
                        Text(stringResource(Lang.oauth_authorized))
                    }
                }
            }
        }
    }

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val awaitingResult = authorizeState is AuthState.AwaitingResult
        if (authorizeState is AuthState.Success) {
            OutlinedButton(
                onClick = onClick,
                enabled = false,
                modifier = Modifier.weight(1f),
                content = content,
            )
        } else {
            Button(
                onClick = onClick,
                enabled = !awaitingResult,
                modifier = Modifier.weight(1f),
                content = content,
                shape = if (awaitingResult) SplitButtonDefaults.leadingButtonShapesFor(48.dp).shape else ButtonDefaults.shape,
            )
        }
        WynimeAnimatedVisibility(
            visible = awaitingResult,
            enter = LocalWynimeMotionScheme.current.animatedVisibility.rowEnter,
            exit = LocalWynimeMotionScheme.current.animatedVisibility.rowExit,
        ) {
            FilledTonalButton(
                onClick = onClickCancel,
                content = { Text(stringResource(Lang.oauth_cancel)) },
                shape = SplitButtonDefaults.trailingButtonShapesFor(48.dp).shape,
            )
        }
    }
}

@Composable
private fun AuthorizeStateText(
    authorizeState: AuthState,
    modifier: Modifier = Modifier,
    animatedVisibilityMotionScheme: AnimatedVisibilityMotionScheme = LocalWynimeMotionScheme.current.animatedVisibility,
) {

    AnimatedVisibility(
        visible = authorizeState is AuthState.Success || authorizeState is AuthState.Failed,
        enter = animatedVisibilityMotionScheme.columnEnter,
        exit = animatedVisibilityMotionScheme.columnExit,
        modifier = modifier,
    ) {
        Text(
            when (authorizeState) {
                is AuthState.Failed -> renderLoadErrorMessage(authorizeState.error)
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when (authorizeState) {
                is AuthState.Success -> MaterialTheme.colorScheme.primary
                is AuthState.Failed -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Stable
private enum class HelpOption {
    BANGUMI_DESC,
    WEBSITE_BLOCKED,
    BANGUMI_REGISTER_CHOOSE,
    REGISTER_TYPE_WRONG_CAPTCHA,
    CANT_RECEIVE_REGISTER_EMAIL,
    REGISTER_ACTIVATION_FAILED,
    OTHERS,
}

@Composable
private fun renderHelpOptionTitle(option: HelpOption): String {
    return when (option) {
        HelpOption.BANGUMI_DESC -> stringResource(Lang.oauth_bangumi_help_bangumi_desc)
        HelpOption.WEBSITE_BLOCKED -> stringResource(Lang.oauth_bangumi_help_website_blocked)
        HelpOption.BANGUMI_REGISTER_CHOOSE -> stringResource(Lang.oauth_bangumi_help_register_choose)
        HelpOption.REGISTER_TYPE_WRONG_CAPTCHA -> stringResource(Lang.oauth_bangumi_help_wrong_captcha)
        HelpOption.CANT_RECEIVE_REGISTER_EMAIL -> stringResource(Lang.oauth_bangumi_help_cant_receive_email)
        HelpOption.REGISTER_ACTIVATION_FAILED -> stringResource(Lang.oauth_bangumi_help_activation_failed)
        HelpOption.OTHERS -> stringResource(Lang.oauth_bangumi_help_others)
    }
}

@Composable
private fun RenderHelpOptionContent(
    option: HelpOption,
    contactActions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (option) {
            HelpOption.BANGUMI_DESC -> {
                Text(stringResource(Lang.oauth_bangumi_help_bangumi_desc_content))
            }

            HelpOption.WEBSITE_BLOCKED -> {
                Text(stringResource(Lang.oauth_bangumi_help_website_blocked_content))
            }

            HelpOption.BANGUMI_REGISTER_CHOOSE -> {
                Text(stringResource(Lang.oauth_bangumi_help_register_choose_content))
            }

            HelpOption.REGISTER_TYPE_WRONG_CAPTCHA -> {
                Text(stringResource(Lang.oauth_bangumi_help_wrong_captcha_content))
            }

            HelpOption.CANT_RECEIVE_REGISTER_EMAIL -> {
                Text(stringResource(Lang.oauth_bangumi_help_cant_receive_email_content))
            }

            HelpOption.REGISTER_ACTIVATION_FAILED -> {
                Text(stringResource(Lang.oauth_bangumi_help_activation_failed_content))
            }

            HelpOption.OTHERS -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Lang.oauth_bangumi_help_others_content))
                    contactActions()
                }
            }
        }
    }
}

@Composable
private fun SettingsScope.AuthorizeHelpQA(
    contactActions: @Composable () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    var currentSelected by rememberSaveable { mutableStateOf<HelpOption?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        Column(
            modifier = Modifier.padding(top = 8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                stringResource(Lang.oauth_bangumi_help_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            HelpOption.entries.forEachIndexed { index, option ->
                ExpandableHelpItem(
                    title = {
                        Text(
                            renderHelpOptionTitle(option),
                            fontWeight = if (currentSelected == option) FontWeight.SemiBold else null,
                        )
                    },
                    content = { RenderHelpOptionContent(option, contactActions) },
                    expanded = currentSelected == option,
                    showDivider = index != HelpOption.entries.lastIndex,
                    onClick = {
                        val wasExpanded = currentSelected == option
                        currentSelected = if (wasExpanded) null else option
                        if (!wasExpanded) {
                            scope.launch {

                                delay(100)
                                scrollState.animateScrollBy(300f)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun SettingsScope.ExpandableHelpItem(
    title: @Composable () -> Unit,
    content: @Composable () -> Unit,
    expanded: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    motionScheme: WynimeMotionScheme = LocalWynimeMotionScheme.current,
) {
    Column(modifier) {
        TextItem(
            title = {
                ProvideTextStyle(MaterialTheme.typography.titleMedium, title)
            },
            action = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
        )
        AnimatedVisibility(
            expanded,
            modifier = Modifier.fillMaxWidth(),
            enter = motionScheme.animatedVisibility.columnEnter,
            exit = motionScheme.animatedVisibility.columnExit,
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium, content)
        }
        if (showDivider) {
            HorizontalDivider()
        }
    }
}
