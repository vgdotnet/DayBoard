package io.github.vgdotnet.dayboard.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.github.vgdotnet.dayboard.R
import io.github.vgdotnet.dayboard.model.InboxSource
import io.github.vgdotnet.dayboard.ui.theme.DayBoard

/** Card from the mockup: #1A1D24 in the dark theme, white with a 1 px border in the light one. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    border: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = DayBoard.colors
    val shape = RoundedCornerShape(radius)
    val borderColor = border ?: c.cardBorder
    Column(
        modifier
            .clip(shape)
            .background(c.card, shape)
            .then(if (borderColor != Color.Transparent) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun SourceIcon(source: InboxSource, boxSize: Dp = 40.dp, iconSize: Dp = 20.dp, radius: Dp = 12.dp) {
    val c = DayBoard.colors
    Box(
        Modifier
            .size(boxSize)
            .background(c.cardInset, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (source == InboxSource.GMAIL) R.drawable.ic_mail else R.drawable.ic_chat),
            contentDescription = source.label,
            tint = c.text,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun SectionHeader(title: String, titleSize: Int = 15, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = titleSize.sp, fontWeight = FontWeight.SemiBold, color = DayBoard.colors.text)
        trailing()
    }
}

/** Segmented filter: a dark track with the selected segment lifted. */
@Composable
fun Segmented(options: List<String>, selected: Int, height: Dp = 36.dp, onSelect: (Int) -> Unit) {
    val c = DayBoard.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(12.dp))
            .border(1.dp, c.cardBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) c.segmentSelected else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) c.text else c.textSecondary,
                )
            }
        }
    }
}

@Composable
fun rememberOpenUrl(): (String) -> Unit {
    val context = LocalContext.current
    return { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}
