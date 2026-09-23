package com.example.data.model

import android.graphics.Color as AndroidColor

enum class PaperTheme(val displayName: String, val bgHex: Int, val textColorHex: Int) {
    WHITE("Classic Crisp White", AndroidColor.WHITE, AndroidColor.rgb(33, 33, 33)),
    WARM_SEPIA("Warm Paper Sepia", AndroidColor.rgb(250, 246, 238), AndroidColor.rgb(44, 34, 20)),
    DARK_SLATE("Midnight Blueprint", AndroidColor.rgb(24, 28, 36), AndroidColor.rgb(230, 235, 245))
}

data class RenderOptions(
    val resolutionScale: Float = 1.5f, // 1.0f = Standard, 1.5f = High DPI, 2.0f = Ultra HD
    val paperTheme: PaperTheme = PaperTheme.WHITE,
    val showPageNumbers: Boolean = true,
    val showHeaderStamp: Boolean = true
)
