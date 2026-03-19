package com.findtrack.app.wallet

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.findtrack.app.data.SpendsViewModel

// Delegates to WalletSummary — budget is now called "wallet balance" in FindTrack
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
