package com.example.atmosfera.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.atmosfera.R

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_light, FontWeight.Light),
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
        letterSpacing = 10.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 35.sp,
    ),
)