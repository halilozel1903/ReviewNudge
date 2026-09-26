package io.github.halilozel1903.reviewnudge.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.reviewnudge.ReviewNudge
import io.github.halilozel1903.reviewnudge.ReviewNudgeDebugPanel
import io.github.halilozel1903.reviewnudge.ReviewOutcome
import io.github.halilozel1903.reviewnudge.ReviewPromptEffect
import io.github.halilozel1903.reviewnudge.core.describe
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as SampleApp
        val nudge = app.reviewNudge
        // Used by scripts/screenshots.sh to show the eligible state.
        if (intent.getBooleanExtra("seedDemo", false)) {
            app.appScope.launch {
                nudge.recordLaunch()
                nudge.recordSignificantEvent()
            }
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                WorkoutScreen(nudge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutScreen(nudge: ReviewNudge) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var completedWorkouts by remember { mutableIntStateOf(0) }

    // Ask at a natural pause: right after a workout is finished.
    ReviewPromptEffect(
        nudge = nudge,
        trigger = completedWorkouts.takeIf { it > 0 },
        onOutcome = { outcome ->
            val message = when (outcome) {
                ReviewOutcome.Launched -> "Review flow launched 🎉"
                is ReviewOutcome.Skipped -> "Not yet: ${outcome.blockers.first().describe()}"
                is ReviewOutcome.Failed -> "Play error: ${outcome.error.message}"
            }
            scope.launch { snackbar.showSnackbar(message) }
        },
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("ReviewNudge sample") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Pretend this is a fitness app. Finishing a workout is a significant event; " +
                    "ReviewNudge asks for a review only when the policy says the moment is right.",
                style = MaterialTheme.typography.bodyLarge,
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        nudge.recordSignificantEvent()
                        completedWorkouts++
                    }
                },
            ) {
                Text("Finish workout ($completedWorkouts done)")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { scope.launch { nudge.optOut() } },
            ) {
                Text("Don't ask me again")
            }

            ReviewNudgeDebugPanel(nudge)
        }
    }
}
