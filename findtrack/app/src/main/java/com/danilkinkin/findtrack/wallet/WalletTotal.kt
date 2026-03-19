package com.danilkinkin.findtrack.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danilkinkin.findtrack.R
import com.danilkinkin.findtrack.base.TextRow
import com.danilkinkin.findtrack.data.ExtendCurrency
import com.danilkinkin.findtrack.util.numberFormat
import java.math.BigDecimal

@Composable
fun WalletTotal(
    initialBalance: BigDecimal,
    remainingBalance: BigDecimal,
    currency: ExtendCurrency,
) {
    val context = LocalContext.current
    val textColor = LocalContentColor.current

    Column {
        if (initialBalance > BigDecimal.ZERO) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.total_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 64.dp, end = 16.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.rest_budget,
                ) + ": " + numberFormat(
                    context,
                    remainingBalance.coerceAtLeast(BigDecimal.ZERO),
                    currency,
                ),
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 64.dp, end = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            TextRow(
                icon = painterResource(R.drawable.ic_info),
                text = stringResource(id = R.string.budget_must_greater_zero),
                description = ""
            )
        }
    }
}
