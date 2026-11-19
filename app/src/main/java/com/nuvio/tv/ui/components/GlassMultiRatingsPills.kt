package com.nuvio.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.RottenTomatoesStatus
import java.util.Locale

@Composable
fun GlassMultiRatingsPills(
    ratings: MDBListRatings,
    modifier: Modifier = Modifier,
    maxPills: Int = 5,
    pillHeight: Dp = 24.dp
) {
    val items = remember(ratings, maxPills) {
        val list = mutableListOf<Pair<String, Double>>()
        ratings.imdb?.takeIf { it > 0.0 }?.let { list.add("imdb" to it) }
        ratings.tomatoes?.takeIf { it > 0.0 }?.let { list.add("tomatoes" to it) }
        ratings.audience?.takeIf { it > 0.0 }?.let { list.add("audience" to it) }
        ratings.tmdb?.takeIf { it > 0.0 }?.let { list.add("tmdb" to it) }
        ratings.metacritic?.takeIf { it > 0.0 }?.let { list.add("metacritic" to it) }
        ratings.trakt?.takeIf { it > 0.0 }?.let { list.add("trakt" to it) }
        ratings.letterboxd?.takeIf { it > 0.0 }?.let { list.add("letterboxd" to it) }
        list.take(maxPills)
    }

    if (items.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (provider, score) ->
            GlassRatingPill(
                provider = provider,
                score = score,
                ratings = ratings,
                pillHeight = pillHeight
            )
        }
    }
}

@Composable
private fun GlassRatingPill(
    provider: String,
    score: Double,
    ratings: MDBListRatings,
    pillHeight: Dp = 24.dp
) {
    val context = LocalContext.current
    val pillShape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .height(pillHeight)
            .clip(pillShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF262626).copy(alpha = 0.72f),
                        Color(0xFF141414).copy(alpha = 0.85f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), pillShape)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            when (provider) {
                "audience" -> {
                    Image(
                        painter = painterResource(
                            id = when (ratings.audienceStatus) {
                                RottenTomatoesStatus.VERIFIED_HOT -> R.drawable.mdblist_audience_verified_hot
                                RottenTomatoesStatus.STALE -> R.drawable.mdblist_audience_stale
                                else -> R.drawable.mdblist_audience
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                }
                "metacritic" -> {
                    Image(
                        painter = painterResource(id = R.drawable.mdblist_metacritic),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                }
                else -> {
                    val rawRes = when (provider) {
                        "imdb" -> R.raw.imdb_logo_2016
                        "tmdb" -> R.raw.mdblist_tmdb
                        "trakt" -> R.raw.mdblist_trakt
                        "letterboxd" -> R.raw.mdblist_letterboxd
                        "tomatoes" -> when (ratings.tomatoesStatus) {
                            RottenTomatoesStatus.CERTIFIED_FRESH -> R.raw.mdblist_tomatoes_certified
                            RottenTomatoesStatus.ROTTEN -> R.raw.mdblist_tomatoes_rotten
                            else -> R.raw.mdblist_tomatoes
                        }
                        else -> null
                    }
                    if (rawRes != null) {
                        val isImdb = provider == "imdb"
                        val model = remember(context, rawRes) {
                            ImageRequest.Builder(context).data(rawRes).build()
                        }
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            modifier = Modifier.size(if (isImdb) 20.dp else 13.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            val scoreText = formatScore(provider, score)
            Text(
                text = scoreText,
                color = Color(0xFFF0F0F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatScore(provider: String, score: Double): String {
    return when (provider) {
        "imdb", "letterboxd" -> String.format(Locale.US, "%.1f", score)
        "tmdb" -> {
            if (score <= 10.0) {
                String.format(Locale.US, "%.1f", score)
            } else {
                "${score.toInt()}%"
            }
        }
        "tomatoes", "audience" -> "${score.toInt()}%"
        "metacritic" -> score.toInt().toString()
        else -> {
            if (score <= 10.0) String.format(Locale.US, "%.1f", score) else "${score.toInt()}%"
        }
    }
}
