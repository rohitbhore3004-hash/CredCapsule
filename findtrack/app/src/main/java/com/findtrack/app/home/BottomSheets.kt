package com.findtrack.app.home

import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.findtrack.app.base.BottomSheetWrapper
import com.findtrack.app.data.AppViewModel
import com.findtrack.app.data.PathState
import com.findtrack.app.data.SpendsViewModel
import com.findtrack.app.di.TUTORS
import com.findtrack.app.editor.toolbar.DEBUG_MENU_SHEET
import com.findtrack.app.editor.toolbar.DebugMenu
import com.findtrack.app.editor.toolbar.restBudgetPill.BUDGET_IS_OVER_DESCRIPTION_SHEET
import com.findtrack.app.editor.toolbar.restBudgetPill.BudgetIsOverDescription
import com.findtrack.app.editor.toolbar.restBudgetPill.NEW_DAY_BUDGET_DESCRIPTION_SHEET
import com.findtrack.app.editor.toolbar.restBudgetPill.NewDayBudgetDescription
import com.findtrack.app.effects.Confetti
import com.findtrack.app.analytics.ANALYTICS_SHEET
import com.findtrack.app.analytics.Analytics
import com.findtrack.app.analytics.VIEWER_HISTORY_SHEET
import com.findtrack.app.analytics.ViewerHistory
import com.findtrack.app.onboarding.ON_BOARDING_SHEET
import com.findtrack.app.onboarding.Onboarding
import com.findtrack.app.recalcBudget.RECALCULATE_DAILY_BUDGET_SHEET
import com.findtrack.app.recalcBudget.RecalcBudget
import com.findtrack.app.settings.*
import com.findtrack.app.wallet.*
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BottomSheets(
    activityResultRegistryOwner: ActivityResultRegistryOwner?,
    appViewModel: AppViewModel = hiltViewModel(),
    spendsViewModel: SpendsViewModel = hiltViewModel(),
) {
    val isDebug = appViewModel.isDebug.observeAsState(false)
    val coroutineScope = rememberCoroutineScope()

    val requireSetBudget by spendsViewModel.requireSetBudget.observeAsState(false)
    val periodFinished by spendsViewModel.periodFinished.observeAsState(false)

    BottomSheetWrapper(
        name = WALLET_SHEET,
        cancelable = !requireSetBudget && !periodFinished,
    ) { state ->
        Wallet(
            forceChange = periodFinished || requireSetBudget,
            activityResultRegistryOwner = activityResultRegistryOwner,
            onClose = {
                coroutineScope.launch {
                    state.hide()
                }
            }
        )
    }

    BottomSheetWrapper(
        name = DEFAULT_RECALC_BUDGET_CHOOSER,
    ) { state ->
        DefaultRecalcBudgetChooser(
            onClose = {
                coroutineScope.launch {
                    state.hide()
                }
            }
        )
    }

    BottomSheetWrapper(
        name = CURRENCY_EDITOR,
    ) { state ->
        CurrencyEditor(
            onClose = {
                coroutineScope.launch {
                    state.hide()
                }
            }
        )
    }

    BottomSheetWrapper(
        name = FINISH_DATE_SELECTOR_SHEET,
    ) { state ->
        FinishDateSelector(
            selectDate = state.args["initialDate"] as Date?,
            onBackPressed = {
                coroutineScope.launch {
                    state.hide()
                }
            },
            onApply = {
                coroutineScope.launch {
                    state.hide(mapOf("finishDate" to it))
                }
            },
        )
    }

    BottomSheetWrapper(
        name = SETTINGS_SHEET,
    ) { state ->
        Settings(
            onTriedWidget = {
                coroutineScope.launch { state.callback(emptyMap()) }
            }
        )
    }

    BottomSheetWrapper(
        name = RECALCULATE_DAILY_BUDGET_SHEET,
        cancelable = false,
    ) { state ->
        RecalcBudget(
            onClose = {
                coroutineScope.launch { state.hide() }
            }
        )
    }

    BottomSheetWrapper(
        name = ANALYTICS_SHEET,
        cancelable = !periodFinished,
    ) { state ->
        Analytics(
            activityResultRegistryOwner = activityResultRegistryOwner,
            onCreateNewPeriod = {
                appViewModel.openSheet(PathState(WALLET_SHEET))
            },
            onClose = {
                coroutineScope.launch { state.hide() }
            },
        )
    }

    BottomSheetWrapper(name = VIEWER_HISTORY_SHEET) {
        ViewerHistory(
            onClose = {
                coroutineScope.launch { it.hide() }
            }
        )
    }

    BottomSheetWrapper(
        name = ON_BOARDING_SHEET,
        cancelable = false,
    ) { state ->
        Onboarding(
            onSetBudget = {
                appViewModel.openSheet(PathState(WALLET_SHEET))
                appViewModel.activateTutorial(TUTORS.SWIPE_EDIT_SPENT)
            },
            onClose = {
                coroutineScope.launch { state.hide() }
            },
        )
    }

    BottomSheetWrapper(
        name = NEW_DAY_BUDGET_DESCRIPTION_SHEET,
    ) { state ->
        NewDayBudgetDescription(
            onClose = {
                coroutineScope.launch { state.hide() }
            },
        )
    }

    BottomSheetWrapper(
        name = BUDGET_IS_OVER_DESCRIPTION_SHEET,
    ) { state ->
        BudgetIsOverDescription(
            onClose = {
                coroutineScope.launch { state.hide() }
            },
        )
    }

    if (isDebug.value) {
        BottomSheetWrapper(
            name = DEBUG_MENU_SHEET,
        ) { state ->
            DebugMenu(
                onClose = {
                    coroutineScope.launch { state.hide() }
                },
            )
        }
    }

    BottomSheetWrapper(
        name = BUG_REPORTER_SHEET,
    ) { state ->
        BugReporter(
            onClose = {
                coroutineScope.launch { state.hide() }
            },
        )
    }

    BottomSheetWrapper(
        name = SETTINGS_CHANGE_THEME_SHEET,
    ) { state ->
        ThemeSwitcherDialog(
            onClose = {
                coroutineScope.launch { state.hide() }
            }
        )
    }

    BottomSheetWrapper(
        name = SETTINGS_CHANGE_LOCALE_SHEET,
    ) { state ->
        LangSwitcherDialog(
            onClose = {
                coroutineScope.launch { state.hide() }
            }
        )
    }

    BottomSheetWrapper(
        name = SETTINGS_TRY_WIDGET_SHEET,
    ) { state ->
        TryWidgetDialog()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Confetti(
            modifier = Modifier.fillMaxSize(),
            controller = appViewModel.confettiController,
        )
    }
}