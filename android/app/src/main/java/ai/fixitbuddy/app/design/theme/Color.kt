package ai.fixitbuddy.app.design.theme

import androidx.compose.ui.graphics.Color

// ── FixIt Genie — Dark App Palette (matches fixit-genie.web.app) ──────────

// Backgrounds — deep dark, layered
val AppBackground  = Color(0xFF080810)   // base canvas
val AppSurface     = Color(0xFF0F0F1A)   // cards, surfaces
val AppCard        = Color(0xFF13131F)   // elevated card
val AppBorder      = Color(0x1AFFFFFF)   // 10% white border
val AppBorderBright = Color(0x33FFFFFF)  // 20% white border (hover/active)

// Primary accent — Genie Orange
val GenieOrange     = Color(0xFFFF6A1E)
val GenieOrangeDeep = Color(0xFFCC4A0A)
val GenieOrangeDim  = Color(0x33FF6A1E)  // 20% for backgrounds
val GenieOrangeGlow = Color(0x1AFF6A1E)  // 10% for subtle glow

// Secondary accent — Genie Purple (smoke)
val GeniePurple     = Color(0xFF9060DD)
val GeniePurpleDeep = Color(0xFF6030AA)
val GeniePurpleDim  = Color(0x1A9060DD)  // 10% for backgrounds

// Text hierarchy
val TextPrimary   = Color(0xFFF0F0F5)   // near-white, main text
val TextSecondary = Color(0xFF8888A8)   // muted
val TextMuted     = Color(0xFF4A4A68)   // very muted / disabled

// Status — vivid on dark background
val StatusListening = Color(0xFF22C55E)  // green
val StatusThinking  = Color(0xFFF59E0B)  // amber
val StatusSpeaking  = Color(0xFF3B82F6)  // blue
val StatusError     = Color(0xFFEF4444)  // red
val StatusIdle      = Color(0xFF6B7280)  // gray

// Legacy aliases — kept for components that reference them directly
val Orange40 = GenieOrange
val Orange80 = Color(0xFFFFB07A)
val Orange90 = Color(0xFFFFDCC2)
val Blue40   = Color(0xFF3B82F6)
val Blue80   = Color(0xFF93C5FD)
val Blue90   = Color(0xFFDBEAFE)
val Green40  = Color(0xFF22C55E)
val Green80  = Color(0xFF86EFAC)
val Green90  = Color(0xFFDCFCE7)
val Red40    = Color(0xFFEF4444)
val Red80    = Color(0xFFFCA5A5)
val Red90    = Color(0xFFFFE4E4)
val Gray10   = AppBackground
val Gray20   = Color(0xFF13131F)
val Gray30   = Color(0xFF1E1E2E)
val Gray40   = Color(0xFF2A2A3E)
val Gray80   = TextSecondary
val Gray90   = Color(0xFFCCCCDD)
val Gray95   = Color(0xFFE8E8F0)
val Gray99   = TextPrimary
