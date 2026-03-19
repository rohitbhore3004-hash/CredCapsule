package com.danilkinkin.findtrack.base.datePicker.model

import java.time.YearMonth

data class Month(
    val yearMonth: YearMonth,
    val weeks: List<Week>
)
