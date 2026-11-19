package com.nuvio.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.core.player.badges.PlayerStreamBadges
import com.nuvio.tv.core.player.badges.StreamBadgeItem

private val BadgeChipShape = RoundedCornerShape(4.dp)

@Composable
fun PlayerCodecBadgesRow(
    badges: PlayerStreamBadges,
    modifier: Modifier = Modifier
) {
    val items = badges.items
    if (items.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { badge ->
            PlayerCodecBadgeChip(badge = badge)
        }
    }
}

@Composable
fun PlayerCodecBadgeChip(
    badge: StreamBadgeItem,
    modifier: Modifier = Modifier
) {
    val customFile = badge.customAssetFile
    if (customFile != null && customFile.exists() && customFile.isFile) {
        val context = LocalContext.current
        val request: ImageRequest = remember(customFile.absolutePath) {
            ImageRequest.Builder(context)
                .data(customFile)
                .crossfade(false)
                .build()
        }
        Box(
            modifier = modifier
                .height(20.dp)
                .wrapContentWidth()
                .clip(BadgeChipShape)
                .background(Color(0x2BFFFFFF), BadgeChipShape)
                .border(0.75.dp, Color(0x3AFFFFFF), BadgeChipShape)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = request,
                contentDescription = badge.label,
                modifier = Modifier
                    .height(16.dp)
                    .wrapContentWidth(),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        // Built-in glassmorphic badge chip
        val (bgColor, borderColor, textColor) = if (badge.isPremium) {
            Triple(
                Color(0x33FFB300), // Warm gold glass
                Color(0x66FFB300),
                Color(0xFFFFD54F)
            )
        } else {
            Triple(
                Color(0x2BFFFFFF), // Crisp frosted glass
                Color(0x3AFFFFFF),
                Color(0xFFEEEEEE)
            )
        }

        Box(
            modifier = modifier
                .height(20.dp)
                .clip(BadgeChipShape)
                .background(bgColor, BadgeChipShape)
                .border(0.75.dp, borderColor, BadgeChipShape)
                .padding(horizontal = 6.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge.shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.5.sp
                ),
                color = textColor,
                maxLines = 1
            )
        }
    }
}
