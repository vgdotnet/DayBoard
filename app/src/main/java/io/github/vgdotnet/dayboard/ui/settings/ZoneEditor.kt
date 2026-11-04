package io.github.vgdotnet.dayboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.vgdotnet.dayboard.R
import io.github.vgdotnet.dayboard.data.Cities
import io.github.vgdotnet.dayboard.data.City
import io.github.vgdotnet.dayboard.model.ZoneClock
import io.github.vgdotnet.dayboard.ui.hhmm
import io.github.vgdotnet.dayboard.ui.theme.DayBoard
import java.time.ZonedDateTime

private val rowHeight = 48.dp

/**
 * The zone list of the settings screen: rows of a fixed height with a drag handle on the left
 * and a remove button on the right. Dragging a handle moves the row; when it passes half of the
 * next row the two swap and the drag offset is reduced by one row, so the row follows the finger.
 */
@Composable
fun ZoneList(
    zones: List<ZoneClock>,
    now: ZonedDateTime,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (id: String) -> Unit,
    onAdd: () -> Unit,
) {
    val c = DayBoard.colors
    val density = LocalDensity.current
    val rowPx = with(density) { rowHeight.toPx() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column {
        zones.forEachIndexed { index, zone -> key(zone.id) {
            val isDragged = index == dragging
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                    .then(if (isDragged) Modifier.shadow(6.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .background(if (isDragged) c.cardInset else c.card)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_drag),
                    contentDescription = "Перетащить",
                    tint = c.textTertiary,
                    modifier = Modifier
                        .size(20.dp)
                        .pointerInput(zone.id) {
                            detectDragGestures(
                                onDragStart = { dragging = index; dragOffset = 0f },
                                onDragEnd = { dragging = -1; dragOffset = 0f },
                                onDragCancel = { dragging = -1; dragOffset = 0f },
                            ) { change, drag ->
                                change.consume()
                                dragOffset += drag.y
                                val current = dragging
                                if (dragOffset > rowPx / 2 && current < zones.lastIndex) {
                                    onMove(current, current + 1); dragging = current + 1; dragOffset -= rowPx
                                } else if (dragOffset < -rowPx / 2 && current > 0) {
                                    onMove(current, current - 1); dragging = current - 1; dragOffset += rowPx
                                }
                            }
                        },
                )
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(zone.name, fontSize = 15.sp, color = c.text, maxLines = 1)
                    if (zone.isHome) Text("· дом", fontSize = 12.sp, color = c.textSecondary)
                }
                Text(utcLabel(zone, now), fontSize = 12.sp, color = c.textSecondary)
                if (!zone.isHome) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = "Удалить ${zone.name}",
                        tint = c.textTertiary,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onRemove(zone.id) }
                            .padding(6.dp),
                    )
                } else {
                    Box(Modifier.size(28.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.cardInset))
        } }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = c.accentText, modifier = Modifier.size(16.dp))
            Text("Добавить город", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.accentText)
        }
    }
}

/** Search over the city catalogue; a tap adds the city. Cities already in the list are hidden. */
@Composable
fun AddCityDialog(
    existing: List<ZoneClock>,
    now: ZonedDateTime,
    onAdd: (City) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = DayBoard.colors
    var query by rememberSaveable { mutableStateOf("") }
    val taken = existing.map { it.name }.toSet()
    val results = Cities.search(query).filter { it.name !in taken }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        titleContentColor = c.text,
        textContentColor = c.text,
        title = { Text("Добавить город", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Город или часовой пояс", color = c.textTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.cardInset,
                        focusedTextColor = c.text,
                        unfocusedTextColor = c.text,
                        cursorColor = c.accent,
                    ),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results, key = { it.name + it.zoneId }) { city ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(city) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(city.name, fontSize = 15.sp, color = c.text)
                                Text(city.zoneId, fontSize = 12.sp, color = c.textSecondary)
                            }
                            Text(now.withZoneSameInstant(city.zone).hhmm(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.cardInset))
                    }
                    if (results.isEmpty()) {
                        item { Text("Ничего не найдено", fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(vertical = 12.dp)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть", color = c.accentText) } },
    )
}
