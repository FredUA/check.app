package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.ui.theme.DarkNavyDrawer
import com.example.ui.theme.DeepMidnight
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun DrawerContent(
    userProfile: UserProfile?,
    userEmail: String,
    currentScreen: String,
    onNavigate: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    val displayName = userProfile?.displayName?.ifBlank { null }
        ?: listOfNotNull(userProfile?.firstName, userProfile?.lastName).joinToString(" ").ifBlank { null }
        ?: userEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyDrawer)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Close / back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SurfaceCard)
                        .clickable(onClick = onCloseDrawer)
                        .testTag("drawer_close_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close Menu",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // User Avatar with neon glowing ring
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(
                        width = 3.dp,
                        brush = Brush.sweepGradient(
                            listOf(NeonPink, NeonPurple, Color.Transparent, NeonPink)
                        ),
                        shape = CircleShape
                    )
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                if (!userProfile?.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = com.example.ui.AvatarImages.displayable(userProfile?.avatarUrl.orEmpty()),
                        contentDescription = "User Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Avatar",
                        tint = NeonPink,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // User Name
            Text(
                text = displayName,
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )
            Text(
                text = userEmail,
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Navigation Items
            DrawerMenuItem(
                icon = Icons.Default.GridView,
                label = "All Projects & Lists",
                isSelected = currentScreen == "home",
                onClick = { onNavigate("home"); onCloseDrawer() }
            )
            DrawerMenuItem(
                icon = Icons.Default.Group,
                label = "Shared Projects",
                isSelected = currentScreen == "shared",
                onClick = { onNavigate("shared"); onCloseDrawer() }
            )
            DrawerMenuItem(
                icon = Icons.Default.Settings,
                label = "Profile & Settings",
                isSelected = currentScreen == "settings",
                onClick = { onNavigate("settings"); onCloseDrawer() }
            )

            if (com.example.demo.DemoMode.isDemoActive.value) {
                Spacer(modifier = Modifier.height(10.dp))
                DrawerMenuItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    label = "Exit Demo Mode",
                    isSelected = false,
                    onClick = {
                        com.example.demo.DemoMode.exitDemoMode()
                        onCloseDrawer()
                    }
                )
            }
        }

        // Consistency Wave Chart (as seen in screenshot)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            WaveConsistencyChart()
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Plan.Share.Done",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "check.app",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) SurfaceCard else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) NeonPink else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun WaveConsistencyChart() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(50.dp)
    ) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val path = Path().apply {
            moveTo(0f, height * 0.75f)
            cubicTo(
                width * 0.15f, height * 0.7f,
                width * 0.25f, height * 0.45f,
                width * 0.35f, height * 0.65f
            )
            cubicTo(
                width * 0.45f, height * 0.85f,
                width * 0.55f, height * 0.5f,
                width * 0.65f, height * 0.2f
            )
            cubicTo(
                width * 0.75f, height * 0.1f,
                width * 0.85f, height * 0.45f,
                width, height * 0.35f
            )
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(NeonPurple, NeonPink, NeonPink)
            ),
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun ThreeDimensionalDrawerScaffold(
    isDrawerOpen: Boolean,
    onCloseDrawer: () -> Unit,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (isDrawerOpen) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "drawer_animation"
    )

    val safeProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyDrawer)
    ) {
        // Drawer content in background
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.78f)
        ) {
            drawerContent()
        }

        // Main content card in foreground that slides, scales, and curves
        val offsetX = (safeProgress * 260f).dp
        val scale = (1f - (safeProgress * 0.16f)).coerceIn(0.5f, 1f)
        val cornerRadius = (safeProgress * 36f).coerceIn(0f, 60f).dp
        val shadowElevation = (safeProgress * 24f).coerceAtLeast(0f).dp
        val dimAlpha = (safeProgress * 0.35f).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .offset { IntOffset(x = (offsetX.toPx()).roundToInt(), y = 0) }
                .scale(scale)
                .shadow(
                    elevation = shadowElevation,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = Color.Black,
                    spotColor = NeonPink
                )
                .clip(RoundedCornerShape(cornerRadius))
                .fillMaxSize()
                .background(DeepMidnight)
        ) {
            content()

            // Dim overlay when drawer is open
            if (safeProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dimAlpha))
                        .clickable(
                            onClick = onCloseDrawer
                        )
                )
            }
        }
    }
}
