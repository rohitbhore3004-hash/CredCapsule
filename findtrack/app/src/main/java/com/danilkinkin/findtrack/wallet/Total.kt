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
fun Total(
    budget: BigDecimal,
    restBudget: BigDecimal,
    days: Int = 0,  // kept for signature compat, unused
    currency: ExtendCurrency,
) {
    WalletTotal(
        initialBalance = budget,
        remainingBalance = restBudget,
        currency = currency,
    )
}
