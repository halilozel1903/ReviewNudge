package io.github.halilozel1903.reviewnudge.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Records the signals a [ReviewPolicy] needs and answers "may we ask now?".
 *
 * Platform independent: the Android `ReviewNudge` class wraps a tracker and
 * adds Google Play's in-app review flow on top.
 *
 * @param store Where the state lives.
 * @param policy The rules to apply.
 * @param appVersion The running app version, used for per-version limits.
 * @param clock Returns the current time in epoch milliseconds.
 */
public class ReviewTracker(
    private val store: ReviewStore,
    public val policy: ReviewPolicy = ReviewPolicy.Default,
    public val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The current state, already moved to [appVersion]. `null` before the first launch. */
    public val state: Flow<ReviewState?> = store.state.map { it?.normalized() }

    /** The decision, re-evaluated whenever the state changes. */
    public val decision: Flow<ReviewDecision> = state.map(::evaluate)

    /** Call once per app launch, for example in `Application.onCreate`. */
    public suspend fun recordLaunch(): ReviewState = updateState {
        it.copy(launchCount = it.launchCount + 1)
    }

    /**
     * Call when the user has a good moment: finished a workout, exported a photo,
     * completed a purchase. Heavier moments can count more than once.
     */
    public suspend fun recordSignificantEvent(weight: Int = 1): ReviewState {
        require(weight >= 1) { "weight must be >= 1" }
        return updateState { it.copy(significantEventCount = it.significantEventCount + weight) }
    }

    /** Evaluates the policy right now. */
    public suspend fun currentDecision(): ReviewDecision = evaluate(state.first())

    /** Records that the review flow was shown. */
    public suspend fun markPrompted(): ReviewState {
        val now = clock()
        return updateState {
            it.copy(
                lastPromptAtMillis = now,
                promptCount = it.promptCount + 1,
                promptsForCurrentVersion = it.promptsForCurrentVersion + 1,
            )
        }
    }

    /** Never ask this user again, for example after they tapped "Don't ask again". */
    public suspend fun optOut(): ReviewState = updateState { it.copy(optedOut = true) }

    /** Forgets everything. Mostly useful for debug screens. */
    public suspend fun reset() {
        store.update { null }
    }

    private fun evaluate(state: ReviewState?): ReviewDecision =
        policy.evaluate(state ?: fresh(), clock())

    private suspend fun updateState(transform: (ReviewState) -> ReviewState): ReviewState =
        requireNotNull(store.update { transform(it?.normalized() ?: fresh()) })

    private fun fresh(): ReviewState = ReviewState(firstSeenAtMillis = clock(), trackedVersion = appVersion)

    private fun ReviewState.normalized(): ReviewState =
        forVersion(appVersion, resetEvents = policy.resetEventsOnNewVersion)
}
