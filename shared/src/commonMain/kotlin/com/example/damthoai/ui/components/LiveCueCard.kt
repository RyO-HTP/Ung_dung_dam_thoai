package com.example.damthoai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.resources.*
import com.example.damthoai.data.LiveCue
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.CueBorder
import com.example.damthoai.ui.theme.CueGradientEnd
import com.example.damthoai.ui.theme.CueGradientStart
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import com.example.damthoai.ui.theme.cssGradient

@Composable
fun LiveCueCard(
    cue: LiveCue,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(cssGradient(135f, CueGradientStart, CueGradientEnd), shape)
            .border(1.dp, CueBorder, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_zap),
            contentDescription = null,
            tint = AccentAmber,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "Live Cue · đang diễn",
                color = AccentAmber,
                style = DamType.Tag
            )
            Text(
                text = cue.title,
                color = TextPrimary,
                style = DamType.Heading13_5
            )
            Text(
                text = cue.message,
                color = TextSecondary,
                style = DamType.Small
            )
        }
    }
}
