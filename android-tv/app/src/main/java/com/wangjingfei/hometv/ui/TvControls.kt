package com.wangjingfei.hometv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TvControlShape = RoundedCornerShape(6.dp)

@Composable
internal fun TvActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "按钮焦点")
    val containerColor = when {
        focused || selected -> Mint
        else -> PanelRaised
    }
    val contentColor = if (focused || selected) Ink else TextPrimary
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = PanelRaised.copy(alpha = 0.55f),
            disabledContentColor = TextMuted,
        ),
        border = if (focused) BorderStroke(2.dp, TextPrimary) else null,
        shape = TvControlShape,
        content = content,
    )
}

@Composable
internal fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "图标按钮焦点")
    val background = when {
        focused || selected -> Mint
        else -> Color.Transparent
    }
    val contentColor = if (focused || selected) Ink else TextPrimary
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .scale(scale)
            .background(background, TvControlShape)
            .then(if (focused) Modifier.border(2.dp, TextPrimary, TvControlShape) else Modifier),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = contentColor,
            disabledContentColor = TextMuted,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
