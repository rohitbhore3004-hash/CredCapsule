package com.danilkinkin.findtrack.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.findtrack.R
import com.danilkinkin.findtrack.analytics.RestAndSpentBudgetCard
import com.danilkinkin.findtrack.analytics.WholeBudgetCard
import com.danilkinkin.findtrack.data.ExtendCurrency
import com.danilkinkin.findtrack.data.SpendsViewModel
import com.danilkinkin.findtrack.util.numberFormat
import java.math.BigDecimal

@Composable
fun WalletSummary(
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    onEdit: () -> Unit = {},
) {
    val context = LocalContext.current
    val currency by spendsViewModel.currency.observeAsState(ExtendCurrency.none())
    val budget = spendsViewModel.budget.value ?: BigDecimal.ZERO
    val spent by spendsViewModel.spent.observeAsState(BigDecimal.ZERO)
    val spentFromDailyBudget by spendsViewModel.spentFromDailyBudget.observeAsState(BigDecimal.ZERO)

    val remainingBalance = (budget - spent - spentFromDailyBudget).coerceAtLeast(BigDecimal.ZERO)
    val totalSpent = spent + spentFromDailyBudget
    val isOverSpent = (budget - spent - spentFromDailyBudget) < BigDecimal.ZERO

    Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)) {
        // Remaining balance card (hero)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isOverSpent)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.primaryContainer
            ),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.remaining_balance),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isOverSpent)
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = numberFormat(context, remainingBalance, currency),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isOverSpent)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
                if (isOverSpent) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.balance_depleted),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Initial balance and spent row
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Initial balance card
            Card(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.initial_balance),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = numberFormat(context, budget, currency),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Spent card
            Card(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.spent_budget),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = numberFormat(context, totalSpent, currency),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        EditButton(onClick = { onEdit() })
    }
}
