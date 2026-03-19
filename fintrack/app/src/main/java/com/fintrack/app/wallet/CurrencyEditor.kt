package com.fintrack.app.wallet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrack.app.LocalWindowInsets
import com.fintrack.app.R
import com.fintrack.app.base.CheckedRow
import com.fintrack.app.base.LocalBottomSheetScrollState
import com.fintrack.app.data.AppViewModel
import com.fintrack.app.data.ExtendCurrency
import com.fintrack.app.data.SpendsViewModel
import com.fintrack.app.util.titleCase
import java.util.Currency

const val CURRENCY_EDITOR = "currencyEditor"

@Composable
fun CurrencyEditor(
    appViewModel: AppViewModel = hiltViewModel(),
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    onClose: () -> Unit = {},
) {
    val localBottomSheetScrollState = LocalBottomSheetScrollState.current

    var currency by remember { mutableStateOf(spendsViewModel.currency.value!!) }
    val openCurrencyChooserDialog = remember { mutableStateOf(false) }
    val openCustomCurrencyEditorDialog = remember { mutableStateOf(false) }

    val navigationBarHeight = LocalWindowInsets.current.calculateBottomPadding()
        .coerceAtLeast(16.dp)

    Surface(Modifier.padding(top = localBottomSheetScrollState.topPadding)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.select_currency_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = navigationBarHeight)
            ) {
                Text(
                    text = stringResource(R.string.select_currency_description),
                    style = MaterialTheme.typography.bodySmall
                        .copy(color = LocalContentColor.current.copy(alpha = 0.6f)),
                    softWrap = true,
                    modifier = Modifier
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            bottom = 16.dp,
                        )
                )
                CheckedRow(
                    checked = currency.type === ExtendCurrency.Type.FROM_LIST,
                    onValueChange = { openCurrencyChooserDialog.value = true },
                    text = stringResource(R.string.currency_from_list),
                    endCaption = if (currency.type === ExtendCurrency.Type.FROM_LIST) {
                        "${
                            Currency.getInstance(
                                currency.value
                            ).displayName.titleCase()
                        } (${
                            Currency.getInstance(
                                currency.value
                            ).symbol
                        })"
                    } else {
                        ""
                    },
                )
                CheckedRow(
                    checked = currency.type === ExtendCurrency.Type.CUSTOM,
                    onValueChange = { openCustomCurrencyEditorDialog.value = true },
                    text = stringResource(R.string.currency_custom),
                    endCaption = if (currency.type === ExtendCurrency.Type.CUSTOM) {
                        currency.value!!
                    } else {
                        ""
                    },
                )
                CheckedRow(
                    checked = currency.type === ExtendCurrency.Type.NONE,
                    onValueChange = {
                        currency = ExtendCurrency.none()
                        spendsViewModel.changeDisplayCurrency(currency)

                        onClose()
                    },
                    text = stringResource(R.string.currency_none),
                )
            }
        }
    }

    if (openCurrencyChooserDialog.value) {
        WorldCurrencyChooser(
            defaultCurrency = if (currency.type === ExtendCurrency.Type.FROM_LIST) {
                Currency.getInstance(currency.value)
            } else {
                null
            },
            onSelect = {
                currency = ExtendCurrency(type = ExtendCurrency.Type.FROM_LIST, value = it.currencyCode)
                spendsViewModel.changeDisplayCurrency(currency)

                onClose()
            },
            onClose = { openCurrencyChooserDialog.value = false },
        )
    }

    if (openCustomCurrencyEditorDialog.value) {
        CustomCurrencyEditor(
            defaultCurrency = if (currency.type === ExtendCurrency.Type.CUSTOM) {
                currency.value
            } else {
                null
            },
            onChange = {
                currency = ExtendCurrency(type = ExtendCurrency.Type.CUSTOM, value = it)
                spendsViewModel.changeDisplayCurrency(currency)

                onClose()
            },
            onClose = { openCustomCurrencyEditorDialog.value = false },
        )
    }
}