package com.wynime.app.desktop

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset

object DesktopContextMenuRepresentation : ContextMenuRepresentation {

    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        if (status is ContextMenuState.Status.Open) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = {
                    state.status = ContextMenuState.Status.Closed
                },
                offset = with(LocalDensity.current) {
                    status.rect.center.let {
                        DpOffset(
                            it.x.toDp(),
                            (-it.y).toDp()
                        )
                    }
                },
            ) {
                items().forEach {
                    DropdownMenuItem(
                        onClick = {
                            it.onClick()
                            state.status = ContextMenuState.Status.Closed
                        },
                        text = {
                            Text(it.label)
                        },
                    )
                }
            }
        }
    }
}