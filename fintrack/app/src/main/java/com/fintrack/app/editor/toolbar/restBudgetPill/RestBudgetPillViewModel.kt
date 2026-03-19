package com.fintrack.app.editor.toolbar.restBudgetPill

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fintrack.app.di.SpendsRepository
import com.fintrack.app.util.numberFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

enum class DaileBudgetState {
    NOT_SET,
    OVERDRAFT,
    BUDGET_END,
    NORMAL,
}

@HiltViewModel
class RestBudgetPillViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val spendsRepository: SpendsRepository,
) : ViewModel() {
    var state = MutableLiveData(DaileBudgetState.NOT_SET)
        private set
    var percentWithNewSpent = MutableLiveData(1f)
        private set
    var percentWithoutNewSpent = MutableLiveData(1f)
        private set
    // In FinTrack: shows remaining wallet balance (not daily budget)
    var todayBudget = MutableLiveData("")
        private set
    var newDailyBudget = MutableLiveData("")
        private set

    fun calculateValues(context: Context, currentSpent: BigDecimal) {
        val ths = this

        viewModelScope.launch {
            val budget = spendsRepository.getBudget().first()
            val spent = spendsRepository.getSpent().first()
            val spentFromDailyBudget = spendsRepository.getSpentFromDailyBudget().first()
            val currency = spendsRepository.getCurrency().first()

            // Wallet is not set if budget is zero
            if (budget <= BigDecimal.ZERO) {
                ths.state.value = DaileBudgetState.NOT_SET
                return@launch
            }

            // Remaining balance = initial balance - all spent - current input
            val totalSpent = spent + spentFromDailyBudget
            val remainingBalance = budget - totalSpent - currentSpent
            val remainingWithoutCurrent = budget - totalSpent

            val isOverdraft = remainingBalance < BigDecimal.ZERO
            val isBalanceDepleted = remainingWithoutCurrent <= BigDecimal.ZERO

            val percentWithNewSpent = if (budget > BigDecimal.ZERO) {
                remainingBalance
                    .divide(budget, 4, RoundingMode.HALF_EVEN)
                    .coerceAtLeast(BigDecimal.ZERO)
            } else {
                BigDecimal.ZERO
            }

            val percentWithoutNewSpent = if (budget > BigDecimal.ZERO) {
                remainingWithoutCurrent
                    .divide(budget, 4, RoundingMode.HALF_EVEN)
                    .coerceAtLeast(BigDecimal.ZERO)
            } else {
                BigDecimal.ZERO
            }

            val formattedRemainingBalance = numberFormat(
                context,
                remainingBalance.coerceAtLeast(BigDecimal.ZERO),
                currency = currency,
                trimDecimalPlaces = true,
            )

            ths.state.value = when {
                isBalanceDepleted -> DaileBudgetState.BUDGET_END
                isOverdraft -> DaileBudgetState.OVERDRAFT
                else -> DaileBudgetState.NORMAL
            }
            ths.percentWithNewSpent.value = percentWithNewSpent.toFloat()
            ths.percentWithoutNewSpent.value = percentWithoutNewSpent.toFloat()
            // todayBudget now shows remaining wallet balance
            ths.todayBudget.value = formattedRemainingBalance
            ths.newDailyBudget.value = formattedRemainingBalance
        }
    }
}
