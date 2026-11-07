package com.mindsync.mindmapview.desktop

import com.mindsync.library.data.TaskDate
import java.time.LocalDate

internal actual fun today(): TaskDate = LocalDate.now().let { TaskDate(it.year, it.monthValue, it.dayOfMonth) }
