package com.worklog.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

val Purple = Color(0xFF7B3FE4)
val PurpleDeep = Color(0xFF3A1580)
val Lavender = Color(0xFFEFE7FC)
val Ink = Color(0xFF16161A)
val Sub = Color(0xFF8A8A95)
val FieldBg = Color(0xFFF3F3F6)
val Line = Color(0xFFEDEDF1)
val Coral = Color(0xFFEE5E45)
val HeroA = Color(0xFFC9AEF8)
val HeroB = Color(0xFFA57AF2)

/** 직장 대표 컬러: 그라데이션 두 색 + 캐릭터 모양 */
data class Swatch(val name: String, val a: Color, val b: Color)

val SWATCHES = listOf(
    Swatch("핑크", Color(0xFFF59BBE), Color(0xFFE5679A)),
    Swatch("오렌지", Color(0xFFFFB547), Color(0xFFFF7A2F)),
    Swatch("민트", Color(0xFF43D1C3), Color(0xFF1FA89C)),
    Swatch("블루", Color(0xFF95AFEE), Color(0xFF5E7FD6)),
    Swatch("코랄", Color(0xFFFF967A), Color(0xFFEE5E45)),
    Swatch("퍼플", Color(0xFFB896F6), Color(0xFF7B3FE4)),
    Swatch("옐로", Color(0xFFFFD66B), Color(0xFFF2A516)),
    Swatch("그린", Color(0xFF8EDB8E), Color(0xFF3DAA5C)),
)

fun swatch(i: Int) = SWATCHES[((i % SWATCHES.size) + SWATCHES.size) % SWATCHES.size]

@Composable
fun WorkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Purple,
            onPrimary = Color.White,
            secondary = HeroB,
            background = Color.White,
            surface = Color.White,
            onSurface = Ink,
            onBackground = Ink,
        ),
        content = content,
    )
}

/** 얼굴이 있는 동글동글 캐릭터. shape 0~5: 원, 둥근 네모, 오각형, 구름, 꽃, 다이아 */
@Composable
fun Buddy(color: Color, shape: Int, modifier: Modifier = Modifier.size(56.dp), happy: Boolean = true) {
    Canvas(modifier) {
        val w = size.minDimension
        val cx = size.width / 2
        val cy = size.height / 2
        when (((shape % 6) + 6) % 6) {
            0 -> drawCircle(color, w * 0.46f, Offset(cx, cy))
            1 -> drawRoundRect(
                color, Offset(cx - w * 0.44f, cy - w * 0.44f), Size(w * 0.88f, w * 0.88f), CornerRadius(w * 0.24f),
            )
            2 -> {
                val p = Path()
                for (i in 0 until 5) {
                    val a = Math.toRadians(-90.0 + 72 * i)
                    val x = cx + w * 0.40f * cos(a).toFloat()
                    val y = cy + w * 0.04f + w * 0.40f * sin(a).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                drawPath(p, color)
                drawPath(p, color, style = Stroke(width = w * 0.12f, join = StrokeJoin.Round))
            }
            3 -> {
                drawCircle(color, w * 0.22f, Offset(cx - w * 0.22f, cy + w * 0.04f))
                drawCircle(color, w * 0.28f, Offset(cx, cy - w * 0.08f))
                drawCircle(color, w * 0.2f, Offset(cx + w * 0.24f, cy + w * 0.06f))
                drawRoundRect(color, Offset(cx - w * 0.45f, cy), Size(w * 0.9f, w * 0.3f), CornerRadius(w * 0.15f))
            }
            4 -> {
                val o = w * 0.19f
                listOf(Offset(cx - o, cy - o), Offset(cx + o, cy - o), Offset(cx - o, cy + o), Offset(cx + o, cy + o))
                    .forEach { drawCircle(color, w * 0.23f, it) }
                drawCircle(color, w * 0.3f, Offset(cx, cy))
            }
            else -> rotate(45f, Offset(cx, cy)) {
                drawRoundRect(
                    color, Offset(cx - w * 0.33f, cy - w * 0.33f), Size(w * 0.66f, w * 0.66f), CornerRadius(w * 0.16f),
                )
            }
        }
        val ink = Color(0xFF1B1B22)
        val ey = cy + w * 0.02f
        val ex = w * 0.13f
        val er = w * 0.07f
        for (s in listOf(-1f, 1f)) {
            if (happy) {
                drawCircle(ink, er, Offset(cx + s * ex, ey))
                drawCircle(Color.White, er * 0.38f, Offset(cx + s * ex + er * 0.3f, ey - er * 0.35f))
            } else {
                drawArc(
                    ink, 0f, 180f, false, Offset(cx + s * ex - er, ey - er * 0.6f), Size(er * 2, er * 1.4f),
                    style = Stroke(width = w * 0.03f, cap = StrokeCap.Round),
                )
            }
        }
        drawArc(
            ink, 20f, 140f, false, Offset(cx - w * 0.06f, ey + er * 0.4f), Size(w * 0.12f, w * 0.09f),
            style = Stroke(width = w * 0.028f, cap = StrokeCap.Round),
        )
    }
}
