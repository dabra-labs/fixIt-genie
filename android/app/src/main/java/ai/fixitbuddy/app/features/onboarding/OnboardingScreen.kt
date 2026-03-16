package ai.fixitbuddy.app.features.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ai.fixitbuddy.app.R
import ai.fixitbuddy.app.design.theme.*

private val PageCount = 3

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PageCount })
    val scope = rememberCoroutineScope()
    val ctaLabels = listOf(
        stringResource(R.string.onboarding_cta_1),
        stringResource(R.string.onboarding_cta_2),
        stringResource(R.string.onboarding_cta_3)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // Subtle radial glow from top-center — atmospheric depth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GenieOrange.copy(alpha = 0.07f),
                            AppBackground.copy(alpha = 0f)
                        ),
                        radius = 600f
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onComplete) {
                    Text(
                        stringResource(R.string.onboarding_skip),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> Page1Welcome()
                    1 -> Page2HowItWorks()
                    2 -> Page3WhatItKnows()
                }
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(PageCount) { index ->
                    val isActive = index == pagerState.currentPage
                    val width by animateDpAsState(
                        targetValue = if (isActive) 28.dp else 6.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "dot_width"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = width, height = 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) GenieOrange
                                else TextMuted
                            )
                    )
                }
            }

            // CTA Button
            Button(
                onClick = {
                    if (pagerState.currentPage < PageCount - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onComplete()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GenieOrange,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp)
                    .height(58.dp)
            ) {
                Text(
                    text = ctaLabels[pagerState.currentPage],
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                if (pagerState.currentPage == PageCount - 1) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─── Page 1: Welcome ──────────────────────────────────────────────────────────

@Composable
private fun Page1Welcome() {
    // Breathing glow animation behind the logo
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // Logo mark — genie lamp in glowing orange aura
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(GenieOrange.copy(alpha = glowAlpha))
            )
            // Inner container
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1A1000),
                                Color(0xFF2A1800)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = GenieOrange.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(26.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_genie_lamp),
                    contentDescription = "FixIt Genie lamp",
                    modifier = Modifier.size(70.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // App name
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold,
            color = GenieOrange,
            letterSpacing = (-1).sp
        )

        Spacer(Modifier.height(10.dp))

        // Tagline
        Text(
            stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.onboarding_description),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(32.dp))

        // Feature pills
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DarkFeaturePill(Icons.Default.Mic, stringResource(R.string.onboarding_pill_voice))
            DarkFeaturePill(Icons.Default.Videocam, stringResource(R.string.onboarding_pill_camera))
            DarkFeaturePill(Icons.Default.Psychology, stringResource(R.string.onboarding_pill_ai))
        }

        Spacer(Modifier.height(20.dp))

        // Challenge badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F0F1A))
                .border(1.dp, AppBorderBright, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🏆", fontSize = 18.sp)
            Text(
                stringResource(R.string.onboarding_challenge),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun DarkFeaturePill(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(AppCard)
            .border(1.dp, AppBorder, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GenieOrange,
            modifier = Modifier.size(12.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            letterSpacing = 0.15.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

// ─── Page 2: How It Works ─────────────────────────────────────────────────────

@Composable
private fun Page2HowItWorks() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Large bold headline split across lines
        Text(
            "See It.",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        Text(
            "Say It.",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = GenieOrange
        )
        Text(
            "Fix It.",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )

        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.onboarding_how_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        DarkStep(
            number = "01",
            icon = Icons.Default.PhotoCamera,
            title = stringResource(R.string.onboarding_step1_title),
            description = stringResource(R.string.onboarding_step1_desc),
            accentColor = GenieOrange
        )
        Spacer(Modifier.height(12.dp))
        DarkStep(
            number = "02",
            icon = Icons.Default.RecordVoiceOver,
            title = stringResource(R.string.onboarding_step2_title),
            description = stringResource(R.string.onboarding_step2_desc),
            accentColor = GeniePurple
        )
        Spacer(Modifier.height(12.dp))
        DarkStep(
            number = "03",
            icon = Icons.Default.CheckCircle,
            title = stringResource(R.string.onboarding_step3_title),
            description = stringResource(R.string.onboarding_step3_desc),
            accentColor = StatusListening
        )
    }
}

@Composable
private fun DarkStep(
    number: String,
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppCard)
            .border(1.dp, AppBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.12f))
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    number,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    letterSpacing = 1.sp
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 19.sp
            )
        }
    }
}

// ─── Page 3: What It Knows ───────────────────────────────────────────────────

@Composable
private fun Page3WhatItKnows() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✦", fontSize = 28.sp, color = GenieOrange)

        Spacer(Modifier.height(12.dp))

        Text(
            stringResource(R.string.onboarding_knows_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.onboarding_knows_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(28.dp))

        DarkKnowledgeCard(
            icon = Icons.Default.DirectionsCar,
            accentColor = GenieOrange,
            category = stringResource(R.string.onboarding_cat_automotive),
            items = stringResource(R.string.onboarding_cat_automotive_items)
        )
        Spacer(Modifier.height(10.dp))
        DarkKnowledgeCard(
            icon = Icons.Default.ElectricalServices,
            accentColor = Color(0xFFFACC15),
            category = stringResource(R.string.onboarding_cat_electrical),
            items = stringResource(R.string.onboarding_cat_electrical_items)
        )
        Spacer(Modifier.height(10.dp))
        DarkKnowledgeCard(
            icon = Icons.Default.HomeRepairService,
            accentColor = GeniePurple,
            category = stringResource(R.string.onboarding_cat_appliances),
            items = stringResource(R.string.onboarding_cat_appliances_items)
        )

        Spacer(Modifier.height(28.dp))

        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DarkStatBadge("7", "Equipment\nTypes")
            // Divider
            Box(modifier = Modifier.size(width = 1.dp, height = 40.dp).background(AppBorder))
            DarkStatBadge("33+", "Error\nCodes")
            Box(modifier = Modifier.size(width = 1.dp, height = 40.dp).background(AppBorder))
            DarkStatBadge("28", "Diagnostic\nSteps")
        }

        Spacer(Modifier.height(20.dp))

        // Powered by
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Verified,
                contentDescription = null,
                tint = Blue40,
                modifier = Modifier.size(15.dp)
            )
            Text(
                stringResource(R.string.onboarding_powered_by),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun DarkKnowledgeCard(
    icon: ImageVector,
    accentColor: Color,
    category: String,
    items: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppCard)
            .border(1.dp, AppBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accentColor.copy(alpha = 0.12f))
                .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(
                category,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                items,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun DarkStatBadge(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = GenieOrange
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
            letterSpacing = 0.2.sp
        )
    }
}
