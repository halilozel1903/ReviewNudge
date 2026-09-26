package io.github.halilozel1903.reviewnudge.core

import java.util.concurrent.TimeUnit

/**
 * The rules that decide when a user may be asked for a review.
 *
 * Every condition must hold at the same time. The defaults follow Google Play's
 * guidance: ask users who have experienced the app enough to give useful
 * feedback, and don't ask too often.
 *
 * @property minLaunches App launches required before the first prompt.
 * @property minDaysSinceInstall Days since the first launch before the first prompt.
 * @property minSignificantEvents Positive moments (a finished level, a saved document…) required.
 * @property cooldownDays Minimum days between two prompts.
 * @property maxPromptsPerVersion Prompts allowed for a single app version.
 * @property maxPromptsTotal Prompts allowed over the lifetime of the install.
 * @property resetEventsOnNewVersion Start counting significant events again after an update.
 */
public data class ReviewPolicy(
    val minLaunches: Int = 5,
    val minDaysSinceInstall: Int = 3,
    val minSignificantEvents: Int = 3,
    val cooldownDays: Int = 60,
    val maxPromptsPerVersion: Int = 1,
    val maxPromptsTotal: Int = 4,
    val resetEventsOnNewVersion: Boolean = false,
) {
    init {
        require(minLaunches >= 0) { "minLaunches must be >= 0" }
        require(minDaysSinceInstall >= 0) { "minDaysSinceInstall must be >= 0" }
        require(minSignificantEvents >= 0) { "minSignificantEvents must be >= 0" }
        require(cooldownDays >= 0) { "cooldownDays must be >= 0" }
        require(maxPromptsPerVersion >= 1) { "maxPromptsPerVersion must be >= 1" }
        require(maxPromptsTotal >= 1) { "maxPromptsTotal must be >= 1" }
    }

    /**
     * Evaluates [state] at [nowMillis] and lists everything that still blocks a prompt.
     */
    public fun evaluate(state: ReviewState, nowMillis: Long): ReviewDecision {
        val blockers = buildList {
            if (state.optedOut) add(ReviewBlocker.OptedOut)
            if (state.promptCount >= maxPromptsTotal) add(ReviewBlocker.LifetimeLimitReached)
            if (state.promptsForCurrentVersion >= maxPromptsPerVersion) add(ReviewBlocker.VersionLimitReached)

            if (state.launchCount < minLaunches) {
                add(ReviewBlocker.NotEnoughLaunches(current = state.launchCount, required = minLaunches))
            }
            if (state.significantEventCount < minSignificantEvents) {
                add(
                    ReviewBlocker.NotEnoughSignificantEvents(
                        current = state.significantEventCount,
                        required = minSignificantEvents,
                    ),
                )
            }

            val installReadyAt = state.firstSeenAtMillis + days(minDaysSinceInstall)
            if (nowMillis < installReadyAt) {
                add(ReviewBlocker.TooSoonAfterInstall(remainingMillis = installReadyAt - nowMillis))
            }

            val lastPrompt = state.lastPromptAtMillis
            if (lastPrompt != null) {
                val cooldownEndsAt = lastPrompt + days(cooldownDays)
                if (nowMillis < cooldownEndsAt) {
                    add(ReviewBlocker.CoolingDown(remainingMillis = cooldownEndsAt - nowMillis))
                }
            }
        }
        return if (blockers.isEmpty()) ReviewDecision.Eligible else ReviewDecision.NotYet(blockers)
    }

    public companion object {
        /** Balanced defaults: 5 launches, 3 days, 3 significant events, 60 day cooldown. */
        public val Default: ReviewPolicy = ReviewPolicy()

        /** For apps with short sessions or few features: ask early. */
        public val Eager: ReviewPolicy = ReviewPolicy(
            minLaunches = 2,
            minDaysSinceInstall = 0,
            minSignificantEvents = 1,
            cooldownDays = 30,
        )

        /** For apps where trust matters more than volume: ask late and rarely. */
        public val Patient: ReviewPolicy = ReviewPolicy(
            minLaunches = 10,
            minDaysSinceInstall = 7,
            minSignificantEvents = 5,
            cooldownDays = 120,
            maxPromptsTotal = 3,
        )

        private fun days(count: Int): Long = TimeUnit.DAYS.toMillis(count.toLong())
    }
}
