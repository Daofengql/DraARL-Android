package cn.silverdragon.draarl.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

data class DraarlIconButtonOptions(
    val modifier: Modifier = Modifier,
    val iconModifier: Modifier = Modifier,
    val enabled: Boolean = true,
    val tint: Color? = null
)
