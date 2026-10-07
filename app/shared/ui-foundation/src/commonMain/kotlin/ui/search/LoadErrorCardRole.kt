package com.wynime.app.ui.search

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.wynime.app.domain.foundation.LoadError

@Stable
sealed class LoadErrorCardRole {
    @Composable
    internal abstract fun Container(
        modifier: Modifier = Modifier,
        containerColor: Color = LoadErrorDefaults.containerColor,
        shape: Shape = MaterialTheme.shapes.large,
        elevation: CardElevation?,
        content: @Composable (LoadErrorCardScope.() -> Unit),
    )

    @Stable
    data object Neural : LoadErrorCardRole() {
        @Composable
        override fun Container(
            modifier: Modifier,
            containerColor: Color,
            shape: Shape,
            elevation: CardElevation?,
            content: @Composable (LoadErrorCardScope.() -> Unit),
        ) {
            val colors = CardDefaults.elevatedCardColors(
                containerColor = containerColor,
            )
            val colorsState by rememberUpdatedState(colors)
            val scope = remember {
                object : LoadErrorCardScope {
                    override val cardColors: CardColors @Composable get() = colorsState
                }
            }
            ElevatedCard(
                modifier, shape = shape,
                colors = colors,
                elevation = elevation ?: CardDefaults.elevatedCardElevation(),
            ) {
                scope.content()
            }
        }
    }

    @Stable
    data object Unimportant : LoadErrorCardRole() {
        @Composable
        override fun Container(
            modifier: Modifier,
            containerColor: Color,
            shape: Shape,
            elevation: CardElevation?,
            content: @Composable (LoadErrorCardScope.() -> Unit),
        ) {
            val colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            val colorsState by rememberUpdatedState(colors)
            val scope = remember {
                object : LoadErrorCardScope {
                    override val cardColors: CardColors @Composable get() = colorsState
                }
            }
            ElevatedCard(
                modifier,
                colors = colors,
                elevation = elevation ?: CardDefaults.cardElevation(),
            ) {
                scope.content()
            }
        }
    }

    @Stable
    data object Important : LoadErrorCardRole() {
        @Composable
        override fun Container(
            modifier: Modifier,
            containerColor: Color,
            shape: Shape,
            elevation: CardElevation?,
            content: @Composable (LoadErrorCardScope.() -> Unit),
        ) {
            val colors = CardDefaults.elevatedCardColors(
                containerColor = containerColor,
                contentColor = MaterialTheme.colorScheme.error,
            )
            val colorsState by rememberUpdatedState(colors)
            val scope = remember {
                object : LoadErrorCardScope {
                    override val cardColors: CardColors @Composable get() = colorsState
                }
            }
            ElevatedCard(
                modifier, shape = shape,
                colors = colors,
                elevation = elevation ?: CardDefaults.elevatedCardElevation(),
            ) {
                scope.content()
            }
        }
    }

    @Stable
    data object Suggestive : LoadErrorCardRole() {
        @Composable
        override fun Container(
            modifier: Modifier,
            containerColor: Color,
            shape: Shape,
            elevation: CardElevation?,
            content: @Composable (LoadErrorCardScope.() -> Unit),
        ) {
            val colors = CardDefaults.elevatedCardColors(
                containerColor = containerColor,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            val colorsState by rememberUpdatedState(colors)
            val scope = remember {
                object : LoadErrorCardScope {
                    override val cardColors: CardColors @Composable get() = colorsState
                }
            }
            ElevatedCard(
                modifier, shape = shape,
                colors = colors,
                elevation = elevation ?: CardDefaults.elevatedCardElevation(),
            ) {
                scope.content()
            }
        }
    }

    companion object {
        fun from(problem: LoadError): LoadErrorCardRole {
            return when (problem) {

                is LoadError.UnknownError,
                is LoadError.RequestError,
                LoadError.ServiceUnavailable,
                LoadError.NetworkError,
                    -> Important

                LoadError.RequiresLogin -> Suggestive

                LoadError.RateLimited -> Neural

                LoadError.NoResults -> Unimportant
            }
        }
    }
}