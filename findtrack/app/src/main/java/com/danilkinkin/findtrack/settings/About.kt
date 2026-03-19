package com.danilkinkin.findtrack.settings

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.*
import com.danilkinkin.findtrack.base.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.findtrack.R
import com.danilkinkin.findtrack.base.DescriptionButton
import com.danilkinkin.findtrack.data.AppViewModel
import com.danilkinkin.findtrack.data.PathState
import com.danilkinkin.findtrack.ui.FindTrackTheme
import com.danilkinkin.findtrack.util.openInBrowser

@Composable
fun About(
    modifier: Modifier = Modifier,
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        val contentColor = LocalContentColor.current

        Column(Modifier.padding(16.dp)) {
            // FindTrack logo header in About screen
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_findtrack_logo),
                    contentDescription = "FindTrack logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "FindTrack",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val annotatedString = buildAnnotatedString {
                withStyle(style = SpanStyle(color = contentColor)) {
                    append("${stringResource(R.string.developer)} ")
                }

                pushStringAnnotation(
                    tag = "developer",
                    annotation = "https://danilkinkin.com",
                )
                withStyle(
                    style = SpanStyle(color = MaterialTheme.colorScheme.primary)
                ) {
                    append("@danilkinkin ")
                }

                appendInlineContent("openInBrowser")

                pop()
            }

            ClickableText(
                text = annotatedString,
                inlineContent = mapOf(
                    "openInBrowser" to InlineTextContent(
                        Placeholder(
                            MaterialTheme.typography.bodyLarge.fontSize,
                            MaterialTheme.typography.bodyLarge.fontSize,
                            PlaceholderVerticalAlign.TextCenter,
                        )
                    ) {
                        Icon(
                            tint = MaterialTheme.colorScheme.primary,
                            painter = painterResource(R.drawable.ic_open_in_browser_small),
                            contentDescription = null,
                        )
                    }
                ),
                style = MaterialTheme.typography.bodyLarge,
                onClick = { offset ->
                    annotatedString.getStringAnnotations(
                        tag = "developer",
                        start = offset,
                        end = offset,
                    ).firstOrNull()?.let {
                        openInBrowser(
                            context,
                            "https://danilkinkin.com",
                        )
                    }

                },
            )
            Spacer(modifier = Modifier.height(24.dp))
            DescriptionButton(
                title = { Text(stringResource(R.string.contribute)) },
                icon = painterResource(R.drawable.ic_contribute),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                    end = 12.dp,
                ),
                onClick = {
                    val currentLocale = configuration.locales[0].language

                    openInBrowser(
                        context,
                        if (currentLocale === "ru") {
                            "https://findtrack.app/ru/contribute"
                        } else {
                            "https://findtrack.app/contribute"
                        },
                    )
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
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
    FindTrackTheme {
        About()
    }
}

@Preview(name = "Night mode", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun PreviewNightMode() {
    FindTrackTheme {
        About()
    }
}