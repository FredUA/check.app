package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.SurfaceCardLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CollaboratorRowItem(
    email: String,
    onRevoke: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isConfirming by remember { mutableStateOf(false) }
    var isDismissed by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = !isDismissed,
        exit = fadeOut(animationSpec = tween(220)) + shrinkVertically(animationSpec = tween(220)),
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceCardLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: dot + email / question with ticker animation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isConfirming) NeonPink else NeonBlue)
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    AnimatedContent(
                        targetState = isConfirming,
                        transitionSpec = {
                            if (targetState) {
                                (slideInVertically { it } + fadeIn()).togetherWith(
                                    slideOutVertically { -it } + fadeOut()
                                )
                            } else {
                                (slideInVertically { -it } + fadeIn()).togetherWith(
                                    slideOutVertically { it } + fadeOut()
                                )
                            }
                        },
                        label = "collaborator_text_flip"
                    ) { confirming ->
                        if (confirming) {
                            Text(
                                text = "Unshare access?",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = email,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right side: action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Cancel button (red cross) when confirming
                    AnimatedVisibility(
                        visible = isConfirming,
                        enter = slideInHorizontally { -it } + fadeIn(),
                        exit = slideOutHorizontally { -it } + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(NeonPink.copy(alpha = 0.2f))
                                .clickable { isConfirming = false }
                                .testTag("cancel_unshare_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = NeonPink,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (!isConfirming) {
                        // Inactive subtle gray cross in circular container
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.22f))
                                .clickable { isConfirming = true }
                                .testTag("initiate_unshare_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove access",
                                tint = TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    } else {
                        // Confirm green checkmark
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(NeonGreen.copy(alpha = 0.22f))
                                .clickable {
                                    isDismissed = true
                                    onRevoke(email)
                                }
                                .testTag("confirm_unshare_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Confirm unshare",
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
