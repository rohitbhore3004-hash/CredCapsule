package com.fintrack.app.settings

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fintrack.app.R
import com.fintrack.app.base.DescriptionButton
import com.fintrack.app.data.AppViewModel
import com.fintrack.app.data.PathState
import com.fintrack.app.ui.FinTrackTheme

@Composable
fun About(
    modifier: Modifier = Modifier,
    appViewModel: AppViewModel = hiltViewModel(),
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(16.dp)) {
            // App logo + name header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_fintrack_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "FinTrack",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.version_label_short),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.5f),
                    )
                }
            }

            Text(
                text = stringResource(R.string.description),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.8f),
            )

            Spacer(modifier = Modifier.height(20.dp))

            DescriptionButton(
                title = { Text(stringResource(R.string.report_bug)) },
                icon = painterResource(R.drawable.ic_bug_report),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                    end = 12.dp,
                ),
                onClick = {
                    appViewModel.openSheet(PathState(BUG_REPORTER_SHEET))
                },
            )
        }
    }
}

@Preview(name = "Default")
@Composable
private fun PreviewDefault() {
    FinTrackTheme {
        About()
    }
}

@Preview(name = "Night mode", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewNightMode() {
    FinTrackTheme {
        About()
    }
}
