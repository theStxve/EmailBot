package com.example.emailbot.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand
val Indigo = Color(0xFF6366F1)
val Violet = Color(0xFF8B5CF6)
val BrandGradient = Brush.linearGradient(listOf(Indigo, Violet))

// Light scheme
val IndigoDark       = Color(0xFF4F46E5)
val IndigoContainer  = Color(0xFFE0E7FF)
val OnIndigoContainer = Color(0xFF1E1B4B)
val LightBackground  = Color(0xFFF7F7FB)
val LightSurfaceVariant = Color(0xFFE9E9F2)

// Dark scheme
val IndigoLight      = Color(0xFFA5B4FC)
val OnIndigoLight    = Color(0xFF1E1B4B)
val IndigoContainerDark = Color(0xFF3730A3)
val DarkBackground   = Color(0xFF111118)
val DarkSurface      = Color(0xFF17171F)
val DarkSurfaceVariant = Color(0xFF2A2A36)

// Status colors (same in light and dark, used with tinted backgrounds)
val StatusSuccess = Color(0xFF16A34A)
val StatusPending = Color(0xFFD97706)
val StatusFailed  = Color(0xFFDC2626)
val StatusPaused  = Color(0xFF64748B)
