package com.mindsync.mindmapview.desktop.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mindsync.library.data.TaskDate
import com.mindsync.mindmapview.desktop.shortDate
import com.mindsync.mindmapview.desktop.taskDateOfUtcMillis
import com.mindsync.mindmapview.desktop.toUtcMillis

/**
 * A [label] beside a button that shows the [date], or "None", and opens a date picker for it. The picker's Clear
 * takes the date away. The date is written with its year unless it is in the year of [today].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDateField(
    label: String
    , date: TaskDate?
    , today: TaskDate?
    , onDateChange: (TaskDate?) -> Unit
    , modifier: Modifier = Modifier
) {
    var isPickerOpen by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
        , verticalAlignment = Alignment.CenterVertically
        , modifier = modifier
    ) {
        Text(label, maxLines = 1, softWrap = false, modifier = Modifier.width(PICKER_LABEL_WIDTH))
        TextButton(
            onClick = { isPickerOpen = true }
            , modifier = Modifier.testTag("$label date")
        ) {
            Text("${date?.let { shortDate(it, today) } ?: "None"} ▾")
        }
    }

    if (!isPickerOpen) return
    // It opens on the date, or else on today, so the month in view is the one most likely wanted.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date?.toUtcMillis()
        , initialDisplayedMonthMillis = (date ?: today)?.toUtcMillis()
    )
    DatePickerDialog(
        onDismissRequest = { isPickerOpen = false }
        , confirmButton = {
            TextButton(
                onClick = {
                    isPickerOpen = false
                    onDateChange(pickerState.selectedDateMillis?.let { taskDateOfUtcMillis(it) })
                }
            ) {
                Text("OK")
            }
        }
        , dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        isPickerOpen = false
                        onDateChange(null)
                    }
                ) {
                    Text("Clear")
                }
                TextButton(onClick = { isPickerOpen = false }) { Text("Cancel") }
            }
        }
    ) {
        DatePicker(state = pickerState)
    }
}
