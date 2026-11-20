package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberNumericStyle
import com.example.ui.theme.LocalAppResources
import com.example.ui.theme.LocalCyberColors

@Composable
fun str(@StringRes id: Int, vararg formatArgs: Any): String {
    val res = LocalAppResources.current
    return remember(res, id, formatArgs.contentHashCode()) {
        if (formatArgs.isEmpty()) res.getString(id) else res.getString(id, *formatArgs)
    }
}

@Composable
fun CyberBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val cyber = LocalCyberColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(cyber.obsidian)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subtle crimson ambient core glow at top-center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyber.crimson.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.22f),
                    radius = size.width * 0.78f
                )
            )
            // Secondary cyan glow at bottom corner
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyber.cyan.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.85f),
                    radius = size.width * 0.6f
                )
            )
            // Subtle diagonal technical grid lines
            val step = 42.dp.toPx()
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.022f),
                    start = Offset(x, 0f),
                    end = Offset(x + size.height * 0.45f, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
        }
        content()
    }
}

@Composable
fun CyberChamferedCard(
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    accentColor: Color = LocalCyberColors.current.crimson,
    cutSize: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val cyber = LocalCyberColors.current
    val shape = remember(cutSize) {
        CutCornerShape(topStart = cutSize, bottomEnd = cutSize, topEnd = 4.dp, bottomStart = 4.dp)
    }
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) accentColor else cyber.stroke,
        animationSpec = tween(240),
        label = "cardBorder"
    )
    val bgBrush = remember(isHighlighted, accentColor) {
        Brush.linearGradient(
            colors = if (isHighlighted) {
                listOf(accentColor.copy(alpha = 0.14f), Color(0xFF14141F))
            } else {
                listOf(Color(0xFF161622), Color(0xFF101018))
            }
        )
    }

    val clickableMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = accentColor),
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgBrush, shape)
            .border(width = if (isHighlighted) 1.5.dp else 1.dp, color = borderColor, shape = shape)
            .then(clickableMod)
    ) {
        // Corner tech bracket accents
        Canvas(modifier = Modifier.matchParentSize()) {
            val bracketLen = 10.dp.toPx()
            val stroke = 2.dp.toPx()
            val bracketColor = if (isHighlighted) accentColor else accentColor.copy(alpha = 0.45f)
            // Top-right corner accent
            drawLine(
                color = bracketColor,
                start = Offset(size.width - bracketLen, 2.dp.toPx()),
                end = Offset(size.width - 2.dp.toPx(), 2.dp.toPx()),
                strokeWidth = stroke
            )
            drawLine(
                color = bracketColor,
                start = Offset(size.width - 2.dp.toPx(), 2.dp.toPx()),
                end = Offset(size.width - 2.dp.toPx(), bracketLen),
                strokeWidth = stroke
            )
            // Bottom-left corner accent
            drawLine(
                color = bracketColor,
                start = Offset(2.dp.toPx(), size.height - bracketLen),
                end = Offset(2.dp.toPx(), size.height - 2.dp.toPx()),
                strokeWidth = stroke
            )
            drawLine(
                color = bracketColor,
                start = Offset(2.dp.toPx(), size.height - 2.dp.toPx()),
                end = Offset(bracketLen, size.height - 2.dp.toPx()),
                strokeWidth = stroke
            )
        }
        content()
    }
}

@Composable
fun ArcTelemetryGauge(
    label: String,
    valueText: String,
    subText: String,
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "gaugeProgress"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 8.dp.toPx()
                val arcPadding = strokeWidth / 2f + 4.dp.toPx()
                val arcSize = Size(
                    this.size.width - arcPadding * 2f,
                    this.size.height - arcPadding * 2f
                )
                val topLeft = Offset(arcPadding, arcPadding)

                // Track arc
                drawArc(
                    color = Color(0xFF232334),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                // Soft outer glow behind active arc
                if (animatedProgress > 0.01f) {
                    drawArc(
                        color = accentColor.copy(alpha = 0.25f),
                        startAngle = 135f,
                        sweepAngle = 270f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth * 1.9f, cap = StrokeCap.Butt)
                    )
                }

                // Active sweep arc
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to accentColor.copy(alpha = 0.65f),
                        0.5f to accentColor,
                        1.0f to accentColor.copy(alpha = 0.95f)
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = valueText,
                    style = CyberNumericStyle.copy(
                        fontSize = if (size >= 120.dp) 22.sp else 17.sp,
                        color = Color.White
                    ),
                    maxLines = 1
                )
                if (subText.isNotBlank()) {
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accentColor,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RgbBoostCoreButton(
    title: String,
    subtitle: String,
    enabled: Boolean,
    isBoosting: Boolean,
    isSessionActive: Boolean,
    chargeProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "boostCore")

    val rgbAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isBoosting) 1100 else 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rgbSpin"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    val animatedCharge by animateFloatAsState(
        targetValue = if (isBoosting) chargeProgress else 0f,
        animationSpec = tween(220),
        label = "chargeProgress"
    )

    val coreShape = remember {
        CutCornerShape(topStart = 28.dp, bottomEnd = 28.dp, topEnd = 8.dp, bottomStart = 8.dp)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(216.dp)
                .clickable(
                    enabled = enabled && !isBoosting,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 110.dp, color = cyber.crimson),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Animated outer RGB / Crimson-Cyan-Violet energy ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radiusOuter = (size.minDimension / 2f) - 10.dp.toPx()
                val ringColorAlpha = if (enabled) 0.35f else 0.12f

                // Pulsing aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            (if (isSessionActive) cyber.cyan else cyber.crimson).copy(alpha = ringColorAlpha),
                            Color.Transparent
                        )
                    ),
                    radius = (size.minDimension / 2f) * pulseScale
                )

                // Rotating RGB gradient ring
                rotate(degrees = rgbAngle) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0.0f to cyber.crimson,
                            0.35f to cyber.cyan,
                            0.68f to cyber.violet,
                            1.0f to cyber.crimson
                        ),
                        radius = radiusOuter,
                        style = Stroke(width = if (isBoosting) 5.dp.toPx() else 3.dp.toPx())
                    )
                }

                // Segmented tactical reticle ring
                val innerRadius = radiusOuter - 12.dp.toPx()
                drawArc(
                    color = cyber.crimson.copy(alpha = 0.55f),
                    startAngle = -rgbAngle * 0.5f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2f, innerRadius * 2f),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawArc(
                    color = cyber.cyan.copy(alpha = 0.55f),
                    startAngle = 180f - rgbAngle * 0.5f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                    size = Size(innerRadius * 2f, innerRadius * 2f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Inner octagonal / chamfered core button
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .clip(coreShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = when {
                                !enabled -> listOf(Color(0xFF1C1C28), Color(0xFF12121A))
                                isSessionActive -> listOf(Color(0xFF003B47), Color(0xFF12121C))
                                else -> listOf(Color(0xFF8C0D20), Color(0xFF280812))
                            }
                        ),
                        shape = coreShape
                    )
                    .border(
                        width = 1.5.dp,
                        color = when {
                            !enabled -> cyber.stroke
                            isSessionActive -> cyber.cyan
                            else -> cyber.crimsonGlow
                        },
                        shape = coreShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = if (enabled) Color.White else cyber.textMuted,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSessionActive) cyber.cyan else cyber.crimsonGlow
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Energy charging bar visible during boost execution
        if (isBoosting) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(10.dp)
                    .clip(CutCornerShape(4.dp))
                    .background(Color(0xFF1A1A26))
                    .border(1.dp, cyber.stroke, CutCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedCharge.coerceIn(0.05f, 1f))
                        .height(10.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(cyber.crimson, cyber.cyan)
                            )
                        )
                )
            }
        }
    }
}

data class CyberNavItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun CyberBottomNavBar(
    items: List<CyberNavItem>,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberColors.current
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xE60D0D14), Color(0xFF09090E))
                )
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Top glowing border hairline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            cyber.crimson.copy(alpha = 0.7f),
                            cyber.cyan.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val activeColor by animateColorAsState(
                    targetValue = if (item.selected) cyber.crimson else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "navColor"
                )
                val pillShape = remember {
                    CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp, topEnd = 2.dp, bottomStart = 2.dp)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(pillShape)
                        .background(
                            if (item.selected) cyber.crimson.copy(alpha = 0.14f) else Color.Transparent,
                            pillShape
                        )
                        .then(
                            if (item.selected) {
                                Modifier.border(1.dp, cyber.crimson.copy(alpha = 0.55f), pillShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            item.onClick()
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = activeColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = activeColor,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AppIconImage(
    drawable: Drawable?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val imageBitmap: ImageBitmap? = remember(drawable) {
        drawable?.let { d ->
            val width = d.intrinsicWidth.takeIf { it > 0 } ?: 96
            val height = d.intrinsicHeight.takeIf { it > 0 } ?: 96
            val bmp = Bitmap.createBitmap(
                width.coerceAtMost(192),
                height.coerceAtMost(192),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, canvas.width, canvas.height)
            d.draw(canvas)
            bmp.asImageBitmap()
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF232334)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contentDescription.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
