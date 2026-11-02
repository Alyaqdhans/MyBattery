package com.alyaqdhan.mybattery

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary                = Color(0xFF006C4E),
    onPrimary              = Color(0xFFFFFFFF),
    primaryContainer       = Color(0xFF89F8C7),
    onPrimaryContainer     = Color(0xFF002116),
    inversePrimary         = Color(0xFF6DDBAC),
    secondary              = Color(0xFF4B635A),
    onSecondary            = Color(0xFFFFFFFF),
    secondaryContainer     = Color(0xFFCDE8DC),
    onSecondaryContainer   = Color(0xFF082019),
    tertiary               = Color(0xFF3D6373),
    onTertiary             = Color(0xFFFFFFFF),
    tertiaryContainer      = Color(0xFFC0E8FB),
    onTertiaryContainer    = Color(0xFF001F29),
    error                  = Color(0xFFBA1A1A),
    onError                = Color(0xFFFFFFFF),
    errorContainer         = Color(0xFFFFDAD6),
    onErrorContainer       = Color(0xFF410002),
    background             = Color(0xFFF5FBF6),
    onBackground           = Color(0xFF171D1A),
    surface                = Color(0xFFF5FBF6),
    onSurface              = Color(0xFF171D1A),
    surfaceVariant         = Color(0xFFDBE5DE),
    onSurfaceVariant       = Color(0xFF3F4944),
    outline                = Color(0xFF6F7973),
    outlineVariant         = Color(0xFFBFC9C2),
    scrim                  = Color(0xFF000000),
    inverseSurface         = Color(0xFF2C322E),
    inverseOnSurface       = Color(0xFFEDF2ED),
    surfaceTint            = Color(0xFF006C4E),
    surfaceBright          = Color(0xFFF5FBF6),
    surfaceDim             = Color(0xFFD5DBD6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow    = Color(0xFFEFF5F0),
    surfaceContainer       = Color(0xFFE9EFEA),
    surfaceContainerHigh   = Color(0xFFE4EAE5),
    surfaceContainerHighest= Color(0xFFDEE4DF),
)

private val DarkScheme = darkColorScheme(
    primary                = Color(0xFF6DDBAC),
    onPrimary              = Color(0xFF003826),
    primaryContainer       = Color(0xFF005139),
    onPrimaryContainer     = Color(0xFF89F8C7),
    inversePrimary         = Color(0xFF006C4E),
    secondary              = Color(0xFFB1CCC0),
    onSecondary            = Color(0xFF1D352C),
    secondaryContainer     = Color(0xFF334B42),
    onSecondaryContainer   = Color(0xFFCDE8DC),
    tertiary               = Color(0xFFA5CCDF),
    onTertiary             = Color(0xFF073543),
    tertiaryContainer      = Color(0xFF244C5B),
    onTertiaryContainer    = Color(0xFFC0E8FB),
    error                  = Color(0xFFFFB4AB),
    onError                = Color(0xFF690005),
    errorContainer         = Color(0xFF93000A),
    onErrorContainer       = Color(0xFFFFDAD6),
    background             = Color(0xFF0F1512),
    onBackground           = Color(0xFFDEE4DF),
    surface                = Color(0xFF0F1512),
    onSurface              = Color(0xFFDEE4DF),
    surfaceVariant         = Color(0xFF3F4944),
    onSurfaceVariant       = Color(0xFFBFC9C2),
    outline                = Color(0xFF89938D),
    outlineVariant         = Color(0xFF3F4944),
    scrim                  = Color(0xFF000000),
    inverseSurface         = Color(0xFFDEE4DF),
    inverseOnSurface       = Color(0xFF2C322E),
    surfaceTint            = Color(0xFF6DDBAC),
    surfaceBright          = Color(0xFF353B38),
    surfaceDim             = Color(0xFF0F1512),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow    = Color(0xFF171D1A),
    surfaceContainer       = Color(0xFF1B211E),
    surfaceContainerHigh   = Color(0xFF252B28),
    surfaceContainerHighest= Color(0xFF303633),
)

@Composable
fun MyBatteryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else           dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else       -> LightScheme
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable fun cardBorderColor()      = MaterialTheme.colorScheme.outline
@Composable fun textPrimary()          = MaterialTheme.colorScheme.onBackground
@Composable fun textSecondary()        = MaterialTheme.colorScheme.onSurfaceVariant
@Composable fun textMuted()            = MaterialTheme.colorScheme.outline
@Composable fun accentGreenEffective() = MaterialTheme.colorScheme.primary

@Composable fun gaugeGreen()  = MaterialTheme.colorScheme.primary
@Composable fun gaugeBlue()   = MaterialTheme.colorScheme.secondary
@Composable fun gaugeRed()    = MaterialTheme.colorScheme.error
@Composable fun gaugeOrange() = if (isSystemInDarkTheme()) Color(0xFFFFB95C) else Color(0xFF8B5000)
@Composable fun gaugeGray()   = MaterialTheme.colorScheme.outlineVariant