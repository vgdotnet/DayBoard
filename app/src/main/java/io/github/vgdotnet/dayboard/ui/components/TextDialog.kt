package io.github.vgdotnet.dayboard.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.sp
import io.github.vgdotnet.dayboard.ui.theme.DayBoard

/** One-line text input with confirm / cancel. */
@Composable
fun TextDialog(
    title: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Добавить",
    initial: String = "",
    numeric: Boolean = false,
) {
    val c = DayBoard.colors
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        titleContentColor = c.text,
        textContentColor = c.text,
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder, color = c.textTertiary) },
                singleLine = true,
                keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.cardInset,
                    focusedTextColor = c.text,
                    unfocusedTextColor = c.text,
                    cursorColor = c.accent,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirmLabel, color = c.accentText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = c.accentText) } },
    )
}
