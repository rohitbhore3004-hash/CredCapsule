package com.findtrack.app.wallet

import androidx.compose.animation.*
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.findtrack.app.R
import com.findtrack.app.data.ExtendCurrency
import com.findtrack.app.data.SpendsViewModel
import com.findtrack.app.util.*
import java.math.BigDecimal
import java.math.RoundingMode


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WalletConstructor(
    forceChange: Boolean = false,
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    onChange: (balance: BigDecimal) -> Unit = { _ -> },
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val budget by spendsViewModel.budget.observeAsState(BigDecimal.ZERO)
    val spent by spendsViewModel.spent.observeAsState(BigDecimal.ZERO)
    val spentFromDailyBudget by spendsViewModel.spentFromDailyBudget.observeAsState(BigDecimal.ZERO)

    var rawBalance by remember {
        if (budget.isZero()) {
            return@remember mutableStateOf("")
        }

        val restBalance =
            (budget - spent - spentFromDailyBudget)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()

        val converted = if (restBalance != "0") {
            tryConvertStringToNumber(restBalance)
        } else {
            Triple("", "0", "")
        }

        mutableStateOf(converted.first + converted.second)
    }

    var balanceCache by remember {
        val restBalance =
            (budget - spent - spentFromDailyBudget)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()

        mutableStateOf(BigDecimal(restBalance))
    }

    var showUseSuggestion by remember {
        val useBalance = budget != balanceCache && !budget.isZero()
        mutableStateOf(useBalance)
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    Column {
        UseLastBalanceSuggestionChip(
            visible = showUseSuggestion,
            onClick = {
                rawBalance =
                    if (!budget.isZero()) {
                        tryConvertStringToNumber(budget.toString()).join(third = false)
                    } else {
                        Triple("", "0", "").join(third = false)
                    }

                balanceCache = budget

                onChange(balanceCache)

                showUseSuggestion = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        )

        val focusRequester = remember { FocusRequester() }

        BasicTextField(
            value = rawBalance,
            interactionSource = interactionSource,
            onValueChange = {
                if (it.isEmpty()) {
                    rawBalance = ""
                    balanceCache = BigDecimal.ZERO
                    onChange(BigDecimal.ZERO)
                    return@BasicTextField
                }

                val converted = tryConvertStringToNumber(it)

                rawBalance = converted.join(third = false)
                balanceCache = converted.join().toBigDecimal()

                onChange(balanceCache)
            },
            textStyle = MaterialTheme.typography.headlineLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontSize = MaterialTheme.typography.displayLarge.fontSize,
            ),
            visualTransformation = visualTransformationAsCurrency(
                context,
                currency = ExtendCurrency.none(),
                hintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { keyboardController?.hide() }
            ),
            modifier = Modifier.focusRequester(focusRequester),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { input ->
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp, start = 24.dp, end = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isFocused && rawBalance.isEmpty()) {
                            Text(
                                text = "0",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12F),
                                    textAlign = TextAlign.Center,
                                ),
                            )
                        }
                        input()
                    }
                    Text(
                        text = stringResource(R.string.wallet_balance_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                    )
                }
            },
        )

        LaunchedEffect(Unit) {
            if (forceChange) focusRequester.requestFocus()
        }
    }
}

@Composable
fun UseLastBalanceSuggestionChip(
    visible: Boolean,
    onClick: () -> Unit
) {
    val localDensity = LocalDensity.current

    Row(
        modifier = Modifier
            .padding(start = 16.dp)
            .height(32.dp)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(
                tween(durationMillis = 150)
            ) + slideInHorizontally(
                tween(
                    durationMillis = 150,
                    easing = EaseInOutQuad,
                )
            ) { with(localDensity) { 10.dp.toPx().toInt() } },
            exit = fadeOut(
                tween(durationMillis = 150)
            ) + slideOutHorizontally(
                tween(
                    durationMillis = 150,
                    easing = EaseInOutQuad,
                )
            ) { with(localDensity) { 10.dp.toPx().toInt() } },
        ) {
            SuggestionChip(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_autorenew),
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(text = stringResource(R.string.use_last))
                },
                onClick = { onClick() }
            )
        }
    }
}
