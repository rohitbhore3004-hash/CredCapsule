package com.danilkinkin.findtrack.wallet

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.findtrack.data.SpendsViewModel

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
