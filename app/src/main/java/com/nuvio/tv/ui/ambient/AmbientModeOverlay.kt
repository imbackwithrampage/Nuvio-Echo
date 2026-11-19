package com.nuvio.tv.ui.ambient

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.core.ambient.AmbientSlideItem
import com.nuvio.tv.domain.model.AmbientModeSettings
import com.nuvio.tv.ui.components.GlassMultiRatingsPills
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AmbientModeOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AmbientModeViewModel = hiltViewModel()
) {
    BackHandler { onDismiss() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val slides by viewModel.slides.collectAsStateWithLifecycle()

    var currentSlideIndex by remember { mutableIntStateOf(0) }
    val currentSlide = slides.getOrNull(currentSlideIndex % slides.size.coerceAtLeast(1))

    val intervalSec = settings.slideshowIntervalSeconds.coerceAtLeast(5)

    // Ken Burns Animation progress (0f -> 1f) over the duration of each slide
    var animTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(currentSlideIndex) {
        animTrigger = false
        delay(50L)
        animTrigger = true
    }

    val kenBurnsScale by animateFloatAsState(
        targetValue = if (animTrigger) 1.07f else 1.0f,
        animationSpec = tween(
            durationMillis = intervalSec * 1000,
            easing = LinearEasing
        ),
        label = "kenBurnsScale"
    )

    // Periodic slide cycler
    LaunchedEffect(slides, intervalSec) {
        if (slides.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(intervalSec * 1000L)
            currentSlideIndex++
        }
    }

    // OLED Pixel Shift Calculation (±6px to prevent burn-in)
    val shiftX = remember(currentSlideIndex) { ((currentSlideIndex * 3) % 13 - 6) }
    val shiftY = remember(currentSlideIndex) { ((currentSlideIndex * 5) % 13 - 6) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss
            )
    ) {
        // Slideshow Backdrops with Ken Burns pan/zoom
        AnimatedContent(
            targetState = currentSlide,
            transitionSpec = {
                fadeIn(tween(1400)) togetherWith fadeOut(tween(1400))
            },
            label = "ambientCrossfade",
            modifier = Modifier.fillMaxSize()
        ) { slide ->
            if (slide != null && slide.backdropUrl.isNotBlank()) {
                val context = LocalContext.current
                val req = remember(slide.backdropUrl) {
                    ImageRequest.Builder(context)
                        .data(slide.backdropUrl)
                        .size(1280, 720)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = req,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = kenBurnsScale
                            scaleY = kenBurnsScale
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1A1A24), Color(0xFF0A0A0F))
                            )
                        )
                )
            }
        }

        // OLED Edge Vignette & Shading Gradients (darkens all 4 edges to protect border OLED pixels)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Left and bottom dark gradient for title legibility
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.Transparent,
                            0.8f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Black.copy(alpha = 0.90f)
                        )
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.85f),
                            0.3f to Color.Black.copy(alpha = 0.40f),
                            0.7f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.60f)
                        )
                    )
                }
        )

        // Clock & Date (Top-Right, with OLED Pixel Shift)
        if (settings.showClockAndMetadata) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(shiftX.dp.roundToPx(), shiftY.dp.roundToPx()) }
                    .padding(top = 36.dp, end = 44.dp)
            ) {
                AmbientClockWidget()
            }
        }

        // Title Info & Ratings (Bottom-Left, with OLED Pixel Shift)
        if (settings.showClockAndMetadata && currentSlide != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(shiftX.dp.roundToPx(), shiftY.dp.roundToPx()) }
                    .padding(start = 48.dp, bottom = 44.dp)
                    .width(480.dp)
            ) {
                Column {
                    if (!currentSlide.logoUrl.isNullOrBlank()) {
                        val context = LocalContext.current
                        val logoReq = remember(currentSlide.logoUrl) {
                            ImageRequest.Builder(context)
                                .data(currentSlide.logoUrl)
                                .crossfade(true)
                                .build()
                        }
                        AsyncImage(
                            model = logoReq,
                            contentDescription = currentSlide.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier
                                .height(56.dp)
                                .fillMaxWidth(0.75f)
                        )
                    } else {
                        Text(
                            text = currentSlide.title,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val metaLine = listOfNotNull(
                        currentSlide.year,
                        currentSlide.genre
                    ).joinToString(" • ")

                    if (metaLine.isNotBlank()) {
                        Text(
                            text = metaLine,
                            color = Color(0xFFDDDDDD),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (currentSlide.multiRatings != null && !currentSlide.multiRatings.isEmpty()) {
                        GlassMultiRatingsPills(
                            ratings = currentSlide.multiRatings,
                            maxPills = 4
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (!currentSlide.overview.isNullOrBlank()) {
                        Text(
                            text = currentSlide.overview,
                            color = Color(0xFFBFBFBF),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Return hint (Bottom-Right, subtle glass pill)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 32.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141414).copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Press any button to return",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun AmbientClockWidget() {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(10_000L) // update every 10 seconds
        }
    }

    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val timeFormat = remember(is24Hour) {
        if (is24Hour) SimpleDateFormat("HH:mm", Locale.getDefault())
        else SimpleDateFormat("h:mm a", Locale.getDefault())
    }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF222222).copy(alpha = 0.65f),
                        Color(0xFF111111).copy(alpha = 0.75f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeFormat.format(currentTime),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = dateFormat.format(currentTime),
                color = Color(0xFFD6D6D6),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
