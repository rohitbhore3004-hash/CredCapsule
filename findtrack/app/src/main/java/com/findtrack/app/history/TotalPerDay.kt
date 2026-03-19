package com.findtrack.app.history

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.findtrack.app.ui.FindTrackTheme
import com.findtrack.app.ui.colorOnEditor
import com.findtrack.app.data.ExtendCurrency
import com.findtrack.app.util.numberFormat
import java.math.BigDecimal
import com.findtrack.app.R

@Composable
fun TotalPerDay(
    spentPerDay: BigDecimal,
    currency: ExtendCurrency,
) {
    val context = LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 32.dp,
                end = 32.dp,
                top = 16.dp,
                bottom = 16.dp,
            ),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = stringResource(R.string.total_per_day),
            style = MaterialTheme.typography.titleMedium,
            color = colorOnEditor.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = numberFormat(context, spentPerDay, currency = currency),
            style = MaterialTheme.typography.titleMedium,
            color = colorOnEditor,
        )
    }
}

@Preview(name = "Default", widthDp = 280)
@Composable
private fun PreviewDefault() {
    FindTrackTheme {
        TotalPerDay(
            BigDecimal(12340),
            ExtendCurrency.none()
        )
    }
}