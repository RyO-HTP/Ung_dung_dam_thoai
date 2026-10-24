package com.example.damthoai.ui.onboarding
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.AmberChipBgIntro
import com.example.damthoai.ui.theme.AmberGlowIntro
import com.example.damthoai.ui.theme.AmberNodeFillIntro
import com.example.damthoai.ui.theme.AmberRingIntro
import com.example.damthoai.ui.theme.AvatarInk
import com.example.damthoai.ui.theme.BlueAvatarA
import com.example.damthoai.ui.theme.BlueAvatarB
import com.example.damthoai.ui.theme.BlueAvatarC
import com.example.damthoai.ui.theme.BlueGlowIntro
import com.example.damthoai.ui.theme.BlueLineIntro
import com.example.damthoai.ui.theme.CrownStart
import com.example.damthoai.ui.theme.CyanGlowIntro
import com.example.damthoai.ui.theme.CyanNodeBgIntro
import com.example.damthoai.ui.theme.IntroPill
import com.example.damthoai.ui.theme.IntroPillBorder
import com.example.damthoai.ui.theme.OrangeAvatarB
import com.example.damthoai.ui.theme.PinkAvatarA
import com.example.damthoai.ui.theme.PrimaryEnd
import com.example.damthoai.ui.theme.PrimaryStart
import com.example.damthoai.ui.theme.PurpleAvatarA
import com.example.damthoai.ui.theme.PurpleAvatarB
import com.example.damthoai.ui.theme.SpeakerTint
import com.example.damthoai.ui.theme.TealAvatarA


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.R
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.TextPrimary

private const val SVG_WIDTH = 350f
private const val SVG_HEIGHT = 400f

@Composable
fun Slide1Visual(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(350.dp, 400.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / SVG_WIDTH
            val sy = size.height / SVG_HEIGHT
            drawContext.transform.scale(sx, sy, androidx.compose.ui.geometry.Offset.Zero)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BlueGlowIntro, Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(175f, 200f),
                    radius = 170f
                ),
                radius = 170f,
                center = androidx.compose.ui.geometry.Offset(175f, 200f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = androidx.compose.ui.geometry.Offset(65f, 50f),
                size = androidx.compose.ui.geometry.Size(220f, 290f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(36f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = androidx.compose.ui.geometry.Offset(65f, 50f),
                size = androidx.compose.ui.geometry.Size(220f, 290f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(36f),
                style = Stroke(1.5f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = androidx.compose.ui.geometry.Offset(150f, 62f),
                size = androidx.compose.ui.geometry.Size(50f, 6f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
            )
            listOf(
                androidx.compose.ui.geometry.Offset(89f, 188f),
                androidx.compose.ui.geometry.Offset(165f, 112f),
                androidx.compose.ui.geometry.Offset(165f, 188f),
                androidx.compose.ui.geometry.Offset(165f, 264f),
                androidx.compose.ui.geometry.Offset(203f, 226f),
                androidx.compose.ui.geometry.Offset(241f, 188f),
                androidx.compose.ui.geometry.Offset(241f, 264f)
            ).forEach {
                drawRoundRect(
                    Color.White,
                    topLeft = it,
                    size = androidx.compose.ui.geometry.Size(16f, 16f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                )
            }
            listOf(
                androidx.compose.ui.geometry.Offset(90f, 113f),
                androidx.compose.ui.geometry.Offset(204f, 113f),
                androidx.compose.ui.geometry.Offset(90f, 227f)
            ).forEach {
                drawRoundRect(
                    color = AccentCyan,
                    topLeft = it,
                    size = androidx.compose.ui.geometry.Size(52f, 52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f),
                    style = Stroke(3f)
                )
            }
            listOf(
                androidx.compose.ui.geometry.Offset(110f, 133f),
                androidx.compose.ui.geometry.Offset(224f, 133f),
                androidx.compose.ui.geometry.Offset(110f, 247f)
            ).forEach {
                drawRoundRect(
                    AccentCyan,
                    topLeft = it,
                    size = androidx.compose.ui.geometry.Size(12f, 12f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                )
            }
            val frameColor = AccentCyan
            val frame = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 146f)))
            drawRoundRect(
                color = frameColor,
                topLeft = androidx.compose.ui.geometry.Offset(82f, 105f),
                size = androidx.compose.ui.geometry.Size(186f, 186f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f),
                style = frame
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, AccentCyan, Color.Transparent),
                    startX = 82f,
                    endX = 268f
                ),
                topLeft = androidx.compose.ui.geometry.Offset(82f, 190f),
                size = androidx.compose.ui.geometry.Size(186f, 4f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.14f),
                topLeft = androidx.compose.ui.geometry.Offset(95f, 306f),
                size = androidx.compose.ui.geometry.Size(160f, 14f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f)
            )
            drawRoundRect(
                color = IntroPill,
                topLeft = androidx.compose.ui.geometry.Offset(205f, 20f),
                size = androidx.compose.ui.geometry.Size(130f, 38f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(19f)
            )
            drawRoundRect(
                color = IntroPillBorder,
                topLeft = androidx.compose.ui.geometry.Offset(205f, 20f),
                size = androidx.compose.ui.geometry.Size(130f, 38f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(19f),
                style = Stroke(1f)
            )
            drawCircle(AccentMint, 11f, androidx.compose.ui.geometry.Offset(226f, 39f))
        }
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = AvatarInk,
            modifier = Modifier.offset(215.dp, 28.dp).size(22.dp)
        )
        Box(Modifier.offset(243.dp, 29.dp).size(84.dp, 20.dp), contentAlignment = Alignment.CenterStart) {
            Text("Sân khấu", color = TextPrimary, style = DamType.Tag.copy(fontSize = 12.sp))
        }
        Box(Modifier.offset(12.dp, 338.dp).size(150.dp, 38.dp), contentAlignment = Alignment.Center) {
            Text(
                "PIN 240826",
                color = AccentCyan,
                style = DamType.Mono.copy(fontSize = 16.sp, letterSpacing = 0.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun Slide2Visual(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(350.dp, 400.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / SVG_WIDTH
            val sy = size.height / SVG_HEIGHT
            drawContext.transform.scale(sx, sy, androidx.compose.ui.geometry.Offset.Zero)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(AmberGlowIntro, Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(175f, 100f),
                    radius = 140f
                ),
                radius = 140f,
                center = androidx.compose.ui.geometry.Offset(175f, 100f)
            )
            drawCircle(AmberRingIntro, 52f, androidx.compose.ui.geometry.Offset(175f, 100f), style = Stroke(1.5f))
            drawCircle(AmberNodeFillIntro, 76f, androidx.compose.ui.geometry.Offset(175f, 100f), style = Stroke(1.5f))
            val lineBrush = Brush.verticalGradient(
                listOf(AccentAmber, BlueLineIntro),
                startY = 134f,
                endY = 270f
            )
            listOf(70f, 175f, 280f).forEach { targetX ->
                val path = Path().apply {
                    moveTo(175f, 134f)
                    cubicTo(175f, 220f, targetX, 220f, targetX, 270f)
                }
                drawPath(
                    path,
                    lineBrush,
                    style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 7f)))
                )
            }
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(CrownStart, OrangeAvatarB),
                    start = androidx.compose.ui.geometry.Offset(139f, 64f),
                    end = androidx.compose.ui.geometry.Offset(211f, 136f)
                ),
                radius = 36f,
                center = androidx.compose.ui.geometry.Offset(175f, 100f)
            )
            listOf(70f, 175f, 280f).forEach { x ->
                drawCircle(CyanNodeBgIntro, 30f, androidx.compose.ui.geometry.Offset(x, 300f))
                drawCircle(BlueAvatarA, 30f, androidx.compose.ui.geometry.Offset(x, 300f), style = Stroke(2f))
                drawRoundRect(
                    color = AmberNodeFillIntro,
                    topLeft = androidx.compose.ui.geometry.Offset(x - 23f, 262f),
                    size = androidx.compose.ui.geometry.Size(46f, 20f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f)
                )
                drawRoundRect(
                    color = AmberGlowIntro,
                    topLeft = androidx.compose.ui.geometry.Offset(x - 23f, 262f),
                    size = androidx.compose.ui.geometry.Size(46f, 20f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
                    style = Stroke(1f)
                )
            }
            val crown = Path().apply {
                moveTo(160f, 94f)
                lineTo(166f, 100f)
                lineTo(175f, 89f)
                lineTo(184f, 100f)
                lineTo(190f, 94f)
                lineTo(187f, 111f)
                lineTo(163f, 111f)
                close()
            }
            drawPath(crown, AvatarInk, style = Stroke(2.7f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        listOf(70, 175, 280).forEach { x ->
            Box(
                modifier = Modifier.offset((x - 23).dp, 262.dp).size(46.dp, 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AmberChipBgIntro),
                contentAlignment = Alignment.Center
            ) {
                Text("-80%", color = AccentAmber, style = DamType.Tag.copy(fontSize = 11.sp))
            }
            Icon(
                painter = painterResource(R.drawable.ic_vol),
                contentDescription = null,
                tint = SpeakerTint,
                modifier = Modifier.offset((x - 12).dp, 288.dp).size(24.dp)
            )
            Box(
                Modifier.offset((x - 48).dp, 340.dp).size(96.dp, 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    listOf("Sân khấu", "Kỹ thuật", "Hậu cần")[(x - 70) / 105],
                    color = TextPrimary,
                    style = DamType.Heading,
                    textAlign = TextAlign.Center
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_crown),
            contentDescription = null,
            tint = AvatarInk,
            modifier = Modifier.offset(157.dp, 82.dp).size(36.dp)
        )
    }
}

@Composable
fun Slide3Visual(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(350.dp, 400.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / SVG_WIDTH
            val sy = size.height / SVG_HEIGHT
            drawContext.transform.scale(sx, sy, androidx.compose.ui.geometry.Offset.Zero)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BlueGlowIntro, Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(175f, 200f),
                    radius = 170f
                ),
                radius = 170f,
                center = androidx.compose.ui.geometry.Offset(175f, 200f)
            )
            drawCircle(Color.White.copy(alpha = 0.22f), 62f, androidx.compose.ui.geometry.Offset(175f, 200f), style = Stroke(1.5f))
            drawCircle(Color.White.copy(alpha = 0.22f), 108f, androidx.compose.ui.geometry.Offset(175f, 200f), style = Stroke(1.5f))
            drawCircle(
                Color.White.copy(alpha = 0.22f),
                152f,
                androidx.compose.ui.geometry.Offset(175f, 200f),
                style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 7f)))
            )
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(BlueAvatarC, PrimaryEnd),
                    start = androidx.compose.ui.geometry.Offset(133f, 158f),
                    end = androidx.compose.ui.geometry.Offset(217f, 242f)
                ),
                radius = 42f,
                center = androidx.compose.ui.geometry.Offset(175f, 200f)
            )
            val nodes = listOf(
                Triple(255f, 132f, listOf(BlueAvatarA, BlueAvatarB)),
                Triple(86f, 254f, listOf(PinkAvatarA, AccentRed)),
                Triple(318f, 250f, listOf(AccentCyan, PrimaryStart)),
                Triple(32f, 150f, listOf(AccentAmber, OrangeAvatarB)),
                Triple(160f, 262f, listOf(AccentMint, TealAvatarA)),
                Triple(262f, 334f, listOf(PurpleAvatarA, PurpleAvatarB))
            )
            nodes.forEachIndexed { index, (x, y, colors) ->
                val radius = listOf(22f, 20f, 17f, 16f, 14f, 14f)[index]
                drawCircle(
                    brush = Brush.linearGradient(colors, start = androidx.compose.ui.geometry.Offset(x - radius, y - radius), end = androidx.compose.ui.geometry.Offset(x + radius, y + radius)),
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
                drawCircle(Color.White.copy(alpha = 0.4f), radius, androidx.compose.ui.geometry.Offset(x, y), style = Stroke(1.5f))
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_radio),
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.offset(155.dp, 180.dp).size(40.dp)
        )
        listOf(
            Triple("SK", 255, 132),
            Triple("HC", 86, 254),
            Triple("KT", 318, 250),
            Triple("ĐĐ", 32, 150),
            Triple("MC", 160, 262),
            Triple("AN", 262, 334)
        ).forEach { (label, x, y) ->
            Text(
                text = label,
                color = AvatarInk,
                style = DamType.Body.copy(fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.offset((x - 18).dp, (y - 11).dp).size(36.dp, 22.dp)
            )
        }
        Box(Modifier.offset(216.dp, 160.dp).size(78.dp, 22.dp), contentAlignment = Alignment.Center) {
            Text("Sân khấu", color = TextPrimary, style = DamType.Tag.copy(fontSize = 11.sp))
        }
        Box(Modifier.offset(51.dp, 280.dp).size(70.dp, 22.dp), contentAlignment = Alignment.Center) {
            Text("Hậu cần", color = TextPrimary, style = DamType.Tag.copy(fontSize = 11.sp))
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingVisualsPreview() {
    DamThoaiTheme {
        Column {
            Slide1Visual()
            Slide2Visual()
            Slide3Visual()
        }
    }
}
