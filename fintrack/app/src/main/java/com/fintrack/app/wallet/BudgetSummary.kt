package com.fintrack.app.wallet

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrack.app.data.SpendsViewModel

// Delegates to WalletSummary — budget is now called "wallet balance" in FinTrack
@Composable
fun BudgetSummary(
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    onEdit: () -> Unit = {},
) {
    WalletSummary(
        spendsViewModel = spendsViewModel,
        onEdit = onEdit,
    )
}
