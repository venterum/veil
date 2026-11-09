@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.Morph
import com.v2ray.ang.R
import kotlinx.coroutines.launch

private const val BADGE_PRESS_SCALE = 0.88f
private const val BADGE_ROTATION_STEP_DEG = 45f

/**
 * The Veil logo on an animated Material 3 Expressive shape, shared by the
 * About hero and the main drawer. Tapping the shape morphs it into the next
 * one and rotates it a notch, matching the About screen's behaviour.
 */
@Composable
fun VeilMorphBadge(
    size: Dp,
    logoSize: Dp,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme

    val shapes = remember {
        listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.SoftBurst,
            MaterialShapes.Gem,
            MaterialShapes.Flower,
        )
    }
    var shapeIndex by remember { mutableIntStateOf(0) }
    var startShape by remember { mutableStateOf(shapes.first()) }
    var endShape by remember { mutableStateOf(shapes.first()) }
    val morph = remember(startShape, endShape) { Morph(startShape, endShape) }
    val morphProgress = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue = if (isPressed) 1f else 0f,
            animationSpec = if (isPressed) {
                motionScheme.fastSpatialSpec()
            } else {
                motionScheme.defaultSpatialSpec()
            }
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val pressScale = lerp(1f, BADGE_PRESS_SCALE, pressProgress.value)
                scaleX = pressScale
                scaleY = pressScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation.value }
                .background(MaterialTheme.colorScheme.primaryContainer, MorphShape(morph, morphProgress.value))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.about_logo_description)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    shapeIndex = (shapeIndex + 1) % shapes.size
                    startShape = endShape
                    endShape = shapes[shapeIndex]
                    scope.launch {
                        morphProgress.snapTo(0f)
                        morphProgress.animateTo(1f, motionScheme.slowSpatialSpec())
                    }
                    scope.launch {
                        rotation.animateTo(
                            rotation.value + BADGE_ROTATION_STEP_DEG,
                            motionScheme.slowSpatialSpec()
                        )
                    }
                }
        )
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
            modifier = Modifier.size(logoSize)
        )
    }
}

private class MorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f))
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}
