package io.github.halilozel1903.reviewnudge

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.halilozel1903.reviewnudge.core.ReviewDecision
import io.github.halilozel1903.reviewnudge.core.ReviewPolicy
import io.github.halilozel1903.reviewnudge.core.describe
import kotlinx.coroutines.launch

/**
 * Remembers a [ReviewNudge] for the current application.
 */
@Composable
public fun rememberReviewNudge(
    policy: ReviewPolicy = ReviewPolicy.Default,
    useFakeReviewManager: Boolean = false,
): ReviewNudge {
    val context = LocalContext.current.applicationContext
    return remember(context, policy, useFakeReviewManager) {
        ReviewNudge.create(context, policy = policy, useFakeReviewManager = useFakeReviewManager)
    }
}

/**
 * Requests a review when [trigger] changes and the policy allows it.
 *
 * Put it on a screen the user reaches after a good moment, keyed on something
 * that changes once per moment, for example the id of a finished order.
 *
 * ```kotlin
 * ReviewPromptEffect(nudge, trigger = completedOrder?.id)
 * ```
 */
@Composable
public fun ReviewPromptEffect(
    nudge: ReviewNudge,
    trigger: Any?,
    onOutcome: (ReviewOutcome) -> Unit = {},
) {
    val activity = LocalActivity.current
    val currentOnOutcome by rememberUpdatedState(onOutcome)
    LaunchedEffect(nudge, trigger, activity) {
        if (trigger == null || activity == null) return@LaunchedEffect
        currentOnOutcome(nudge.requestReviewIfEligible(activity))
    }
}

/**
 * A card that shows the live state and decision, with buttons to simulate
 * launches and events. Drop it into a debug menu to tune your [ReviewPolicy].
 */
@Composable
public fun ReviewNudgeDebugPanel(
    nudge: ReviewNudge,
    modifier: Modifier = Modifier,
) {
    val state by nudge.state.collectAsStateWithLifecycle(initialValue = null)
    val decision by nudge.decision.collectAsStateWithLifecycle(initialValue = null)
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("ReviewNudge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            val current = state
            val policy = nudge.policy
            Text(
                text = if (current == null) {
                    "No launch recorded yet"
                } else {
                    "Launches ${current.launchCount}/${policy.minLaunches} · " +
                        "Events ${current.significantEventCount}/${policy.minSignificantEvents} · " +
                        "Prompts ${current.promptCount}/${policy.maxPromptsTotal}"
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider()

            when (val value = decision) {
                null -> Unit
                ReviewDecision.Eligible -> Text(
                    "✅ Eligible: the next request will show the review flow",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is ReviewDecision.NotYet -> value.blockers.forEach { blocker ->
                    Text("⏳ ${blocker.describe()}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { scope.launch { nudge.recordLaunch() } }, label = { Text("+ Launch") })
                AssistChip(onClick = { scope.launch { nudge.recordSignificantEvent() } }, label = { Text("+ Event") })
                AssistChip(
                    onClick = { activity?.let { scope.launch { nudge.requestReviewIfEligible(it) } } },
                    label = { Text("Request") },
                )
                AssistChip(onClick = { scope.launch { nudge.reset() } }, label = { Text("Reset") })
            }
        }
    }
}
