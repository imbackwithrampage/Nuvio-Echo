package com.nuvio.tv.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.player.badges.BadgePackInfo
import com.nuvio.tv.core.player.badges.PlayerBadgePackManager
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun PlayerBadgePackDialog(
    currentPackPath: String?,
    onPackSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installedPacks by remember { mutableStateOf<List<BadgePackInfo>>(emptyList()) }
    var showPathInputDialog by remember { mutableStateOf(false) }

    fun refreshPacks() {
        installedPacks = PlayerBadgePackManager.getInstalledPacks(context)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            refreshPacks()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = PlayerBadgePackManager.importFromUri(context, uri)
                result.onSuccess { pack ->
                    refreshPacks()
                    onPackSelected(pack.directoryPath)
                    Toast.makeText(
                        context,
                        context.getString(R.string.playback_badge_pack_imported_success, pack.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { err ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.playback_badge_pack_import_error, err.localizedMessage ?: "Unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val firstItemFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocusAfterFrames()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.playback_custom_badge_pack),
        subtitle = stringResource(R.string.playback_custom_badge_pack_sub),
        width = 460.dp,
        suppressFirstKeyUp = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = NuvioTheme.spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                    contentPadding = PaddingValues(vertical = NuvioTheme.spacing.xs)
                ) {
                    // Option 0: Built-in default
                    item(key = "default_pack") {
                        val isSelected = currentPackPath.isNullOrBlank()
                        var focused by remember { mutableStateOf(false) }
                        Card(
                            onClick = {
                                onPackSelected(null)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(firstItemFocusRequester)
                                .onFocusChanged { focused = it.isFocused }
                                .settingsFocusGlass(focused, RoundedCornerShape(10.dp)),
                            colors = CardDefaults.colors(
                                containerColor = if (isSelected) settingsFocusFillColor() else NuvioTheme.colors.BackgroundCard,
                                focusedContainerColor = Color.Transparent
                            ),
                            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = CardDefaults.scale(focusedScale = 1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(NuvioTheme.spacing.lg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.playback_badge_pack_default),
                                        color = if (isSelected) NuvioTheme.colors.Primary else NuvioTheme.colors.TextPrimary,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = stringResource(R.string.playback_custom_badge_pack_sub),
                                        color = NuvioTheme.colors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = NuvioTheme.colors.Primary
                                    )
                                }
                            }
                        }
                    }

                    // Custom installed packs
                    itemsIndexed(
                        items = installedPacks,
                        key = { _, pack -> pack.id }
                    ) { _, pack ->
                        val isSelected = currentPackPath == pack.directoryPath
                        var focused by remember { mutableStateOf(false) }
                        Card(
                            onClick = {
                                onPackSelected(pack.directoryPath)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focused = it.isFocused }
                                .settingsFocusGlass(focused, RoundedCornerShape(10.dp)),
                            colors = CardDefaults.colors(
                                containerColor = if (isSelected) settingsFocusFillColor() else NuvioTheme.colors.BackgroundCard,
                                focusedContainerColor = Color.Transparent
                            ),
                            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = CardDefaults.scale(focusedScale = 1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(NuvioTheme.spacing.lg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pack.name,
                                        color = if (isSelected) NuvioTheme.colors.Primary else NuvioTheme.colors.TextPrimary,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "${pack.assetCount} badge assets",
                                        color = NuvioTheme.colors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = NuvioTheme.colors.Primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
            ) {
                var importFocused by remember { mutableStateOf(false) }
                Card(
                    onClick = {
                        runCatching {
                            filePickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                        }.onFailure {
                            showPathInputDialog = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { importFocused = it.isFocused }
                        .settingsFocusGlass(importFocused, RoundedCornerShape(10.dp)),
                    colors = CardDefaults.colors(
                        containerColor = NuvioTheme.colors.BackgroundCard,
                        focusedContainerColor = Color.Transparent
                    ),
                    shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = CardDefaults.scale(focusedScale = 1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NuvioTheme.spacing.md, vertical = NuvioTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = NuvioTheme.colors.TextPrimary
                        )
                        Spacer(modifier = Modifier.padding(start = NuvioTheme.spacing.xs))
                        Text(
                            text = stringResource(R.string.playback_badge_pack_import),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioTheme.colors.TextPrimary
                        )
                    }
                }

                if (!currentPackPath.isNullOrBlank()) {
                    var deleteFocused by remember { mutableStateOf(false) }
                    Card(
                        onClick = {
                            val activeId = File(currentPackPath).name
                            PlayerBadgePackManager.deletePack(context, activeId)
                            onPackSelected(null)
                            refreshPacks()
                        },
                        modifier = Modifier
                            .onFocusChanged { deleteFocused = it.isFocused }
                            .settingsFocusGlass(deleteFocused, RoundedCornerShape(10.dp)),
                        colors = CardDefaults.colors(
                            containerColor = NuvioTheme.colors.BackgroundCard,
                            focusedContainerColor = Color.Transparent
                        ),
                        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                        scale = CardDefaults.scale(focusedScale = 1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = NuvioTheme.spacing.md, vertical = NuvioTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF5252)
                            )
                        }
                    }
                }
            }
        }
    }
}
