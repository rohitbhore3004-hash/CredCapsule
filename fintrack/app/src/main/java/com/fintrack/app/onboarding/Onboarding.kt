package com.fintrack.app.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fintrack.app.LocalWindowInsets
import com.fintrack.app.R
import com.fintrack.app.base.DescriptionButton
import com.fintrack.app.base.LocalBottomSheetScrollState
import com.fintrack.app.ui.FinTrackTheme

const val ON_BOARDING_SHEET = "onBoarding"

@Composable
fun Onboarding(
    onSetBudget: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val localBottomSheetScrollState = LocalBottomSheetScrollState.current
    val navigationBarHeight = LocalWindowInsets.current.calculateBottomPadding()
        .coerceAtLeast(16.dp)

    Surface(Modifier.padding(top = localBottomSheetScrollState.topPadding)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = navigationBarHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))

            // FinTrack logo
            Icon(
                painter = painterResource(R.drawable.ic_fintrack_logo),
                contentDescription = "FinTrack logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "FinTrack",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                ),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.hello),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(40.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                NumberedRow(
                    number = 1,
                    title = stringResource(R.string.help_set_budget_title),
                    subtitle = stringResource(R.string.help_set_budget_description),
                )
                NumberedRow(
                    number = 2,
                    title = stringResource(R.string.help_record_spends_title),
                    subtitle = stringResource(R.string.help_record_spends_description),
                )
                NumberedRow(
                    number = 3,
                    title = stringResource(R.string.help_good_luck_title),
                    subtitle = stringResource(R.string.help_good_luck_description),
                )
            }
            Spacer(Modifier.height(40.dp))
            DescriptionButton(
                title = { Text(stringResource(R.string.set_period_title)) },
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                onClick = {
                    onSetBudget()
                    onClose()
                },
            )
        }
    }
}

@Preview
@Composable
private fun PreviewDefault() {
    FinTrackTheme {
        Onboarding()
    }
}
