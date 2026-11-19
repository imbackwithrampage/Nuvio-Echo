package com.nuvio.tv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val DEFAULT_INITIAL_READING_DELAY_MS = 3000L // 3.0-second initial reading delay
private const val DEFAULT_BOTTOM_PAUSE_MS = 1500L
private const val DEFAULT_SCROLL_SPEED_PX_PER_SEC = 28f // Comfortable reading speed
private const val RESET_ANIMATION_MS = 500

/**
 * Auto-scrolling synopsis text for Glass Hero Banners (Home & Details).
 * - Detects if description text exceeds [maxLines].
 * - After a 3.0-second reading delay once the title settles in focus, smoothly scrolls vertically,
 *   pauses at the bottom for 1.5 seconds, then resets to the top with a smooth reset animation.
 * - Applies top/bottom alpha edge fading only while actively scrolling.
 * - Cancels immediately and cleanly via LaunchedEffect(itemId) on focus changes to ensure locked 60fps
 *   with zero animation leaks or UI stutter.
 */
@Composable
fun AutoScrollingHeroDescription(
    text: String,
    itemId: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 17.sp,
    color: Color = Color(0xFFEEEEEE),
    style: TextStyle = TextStyle.Default,
    readingDelayMs: Long = DEFAULT_INITIAL_READING_DELAY_MS,
    pauseAtBottomMs: Long = DEFAULT_BOTTOM_PAUSE_MS,
    edgeFadeHeight: Dp = 10.dp,
    onClick: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    var isOverflowing by remember(itemId, text, maxLines) { mutableStateOf(false) }
    val density = LocalDensity.current
    val edgeFadePx = with(density) { edgeFadeHeight.toPx() }

    // Coroutine lifecycle tied strictly to LaunchedEffect(itemId, text, isOverflowing).
    // Rapid focus movement across cards/rows cancels pending delay and animation immediately!
    LaunchedEffect(itemId, text, isOverflowing) {
        if (!isOverflowing) {
            scrollState.scrollTo(0)
            return@LaunchedEffect
        }

        // Reset scroll position to top initially
        scrollState.scrollTo(0)

        // 1. Initial 3.0s reading delay after title settles in focus
        delay(readingDelayMs)

        while (isActive) {
            val maxScroll = scrollState.maxValue
            if (maxScroll <= 0) break

            // 2. Smooth auto-scroll at comfortable reading speed
            val scrollDurationMs = ((maxScroll / DEFAULT_SCROLL_SPEED_PX_PER_SEC) * 1000)
                .toInt()
                .coerceIn(1200, 10000)

            scrollState.animateScrollTo(
                value = maxScroll,
                animationSpec = tween(durationMillis = scrollDurationMs, easing = LinearEasing)
            )

            // 3. Pause briefly at the bottom
            delay(pauseAtBottomMs)

            // 4. Smoothly reset to the top
            scrollState.animateScrollTo(
                value = 0,
                animationSpec = tween(durationMillis = RESET_ANIMATION_MS, easing = FastOutSlowInEasing)
            )

            // 5. Delay at top before looping
            delay(readingDelayMs)
        }
    }

    val isScrolling = scrollState.maxValue > 0 && (scrollState.value > 0 || scrollState.isScrollInProgress)

    val calculatedMaxHeight = with(density) {
        (lineHeight.toPx() * maxLines + 4.dp.toPx()).toDp()
    }

    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                compositingStrategy = if (isScrolling) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                if (isScrolling && scrollState.maxValue > 0) {
                    val currentVal = scrollState.value
                    val maxVal = scrollState.maxValue
                    val h = size.height

                    // Top alpha fade if scrolled down from top
                    if (currentVal > 0) {
                        val topFadeFactor = (currentVal / edgeFadePx).coerceIn(0f, 1f)
                        val fadeHeight = edgeFadePx * topFadeFactor
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = fadeHeight
                            ),
                            size = Size(size.width, fadeHeight),
                            blendMode = BlendMode.DstIn
                        )
                    }

                    // Bottom alpha fade if not at the bottom
                    if (currentVal < maxVal) {
                        val remaining = maxVal - currentVal
                        val bottomFadeFactor = (remaining / edgeFadePx).coerceIn(0f, 1f)
                        val fadeHeight = edgeFadePx * bottomFadeFactor
                        val fadeTop = h - fadeHeight
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = fadeTop,
                                endY = h
                            ),
                            topLeft = Offset(0f, fadeTop),
                            size = Size(size.width, fadeHeight),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .heightIn(max = calculatedMaxHeight)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                lineHeight = lineHeight,
                style = style,
                onTextLayout = { layoutResult ->
                    val overflows = layoutResult.lineCount > maxLines || layoutResult.hasVisualOverflow
                    if (overflows != isOverflowing) {
                        isOverflowing = overflows
                    }
                }
            )
        }
    }
}
