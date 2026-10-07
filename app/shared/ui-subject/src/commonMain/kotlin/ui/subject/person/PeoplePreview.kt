package com.wynime.app.ui.subject.person

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.wynime.app.navigation.LocalNavigator
import com.wynime.app.ui.foundation.ImageViewer
import com.wynime.app.ui.foundation.ImageViewerBackHandler
import com.wynime.app.ui.foundation.ImageViewerHandler
import com.wynime.app.ui.foundation.rememberImageViewerHandler
import com.wynime.app.ui.foundation.widgets.ModalSideSheet
import com.wynime.app.ui.foundation.widgets.rememberModalSideSheetState
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.person_details_open_full_page
import org.jetbrains.compose.resources.stringResource

@Immutable
sealed class PeoplePreviewTarget {
    data class Person(val personId: Int) : PeoplePreviewTarget()
    data class Character(val characterId: Int) : PeoplePreviewTarget()
}

val LocalPeoplePreviewHandler = staticCompositionLocalOf<((PeoplePreviewTarget) -> Unit)?> { null }

@Composable
fun rememberPeopleClickHandler(): (PeoplePreviewTarget) -> Unit {
    val preview = LocalPeoplePreviewHandler.current
    val navigator = LocalNavigator.current
    return remember(preview, navigator) {
        { target ->
            when {
                preview != null -> preview(target)
                target is PeoplePreviewTarget.Person -> navigator.navigatePersonDetails(target.personId)
                target is PeoplePreviewTarget.Character -> navigator.navigateCharacterDetails(target.characterId)
            }
        }
    }
}

@Composable
fun PeoplePreviewHost(content: @Composable () -> Unit) {
    var target by remember { mutableStateOf<PeoplePreviewTarget?>(null) }
    CompositionLocalProvider(LocalPeoplePreviewHandler provides { target = it }) {
        content()
    }
    target?.let { current ->
        PeoplePreviewSideSheet(current, onDismissRequest = { target = null })
    }
}

@Composable
private fun PeoplePreviewSideSheet(
    target: PeoplePreviewTarget,
    onDismissRequest: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val state = rememberModalSideSheetState()
    val imageViewer = rememberImageViewerHandler()
    ModalSideSheet(
        onDismiss = onDismissRequest,
        modifier = Modifier.width(412.dp),
        state = state,
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,

        overlay = {
            ImageViewer(imageViewer) { imageViewer.clear() }
            ImageViewerBackHandler(imageViewer)
        },
    ) {
        when (target) {
            is PeoplePreviewTarget.Person -> PersonPreviewContent(
                target.personId,
                imageViewer,
                onOpenFullPage = {
                    onDismissRequest()
                    navigator.navigatePersonDetails(target.personId)
                },
                onDismissRequest = { state.close() },
            )

            is PeoplePreviewTarget.Character -> CharacterPreviewContent(
                target.characterId,
                imageViewer,
                onOpenFullPage = {
                    onDismissRequest()
                    navigator.navigateCharacterDetails(target.characterId)
                },
                onDismissRequest = { state.close() },
            )
        }
    }
}

@Composable
private fun PersonPreviewContent(
    personId: Int,
    imageViewer: ImageViewerHandler,
    onOpenFullPage: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val vm = viewModel<PersonDetailsViewModel>(key = "person-preview-$personId") { PersonDetailsViewModel(personId) }
    val details by vm.details.collectAsState()
    Column {
        PreviewSheetHeader(details?.person?.displayName ?: "", onOpenFullPage, onDismissRequest)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            PersonDetailsContentColumn(
                details = details,
                casts = vm.castsPager.collectAsLazyPagingItems(),
                works = vm.worksPager.collectAsLazyPagingItems(),
                comments = vm.comments,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),

                navigation = rememberPeopleDetailsNavigation(onBeforeNavigate = onDismissRequest),
                imageViewer = imageViewer,
            )
        }
    }
}

@Composable
private fun CharacterPreviewContent(
    characterId: Int,
    imageViewer: ImageViewerHandler,
    onOpenFullPage: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val vm = viewModel<CharacterDetailsViewModel>(key = "character-preview-$characterId") {
        CharacterDetailsViewModel(characterId)
    }
    val details by vm.details.collectAsState()
    Column {
        PreviewSheetHeader(details?.character?.displayName ?: "", onOpenFullPage, onDismissRequest)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            CharacterDetailsContentColumn(
                details = details,
                subjects = vm.subjectsPager.collectAsLazyPagingItems(),
                comments = vm.comments,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),

                navigation = rememberPeopleDetailsNavigation(onBeforeNavigate = onDismissRequest),
                imageViewer = imageViewer,
            )
        }
    }
}

@Composable
private fun PreviewSheetHeader(
    title: String,
    onOpenFullPage: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onOpenFullPage) {
            Icon(
                Icons.Rounded.OpenInFull,
                contentDescription = stringResource(Lang.person_details_open_full_page),
            )
        }
        IconButton(onDismissRequest) {
            Icon(Icons.Rounded.Close, contentDescription = null)
        }
    }
}
