package io.github.halilozel1903.reviewnudge.core

/**
 * Everything ReviewNudge remembers about one install.
 */
public data class ReviewState(
    val firstSeenAtMillis: Long,
    val launchCount: Int = 0,
    val significantEventCount: Int = 0,
    val lastPromptAtMillis: Long? = null,
    val promptCount: Int = 0,
    val promptsForCurrentVersion: Int = 0,
    val trackedVersion: String? = null,
    val optedOut: Boolean = false,
) {
    /**
     * Returns this state moved to [appVersion]: per-version counters restart when
     * the version changes.
     */
    public fun forVersion(appVersion: String, resetEvents: Boolean): ReviewState {
        if (trackedVersion == appVersion) return this
        return copy(
            trackedVersion = appVersion,
            promptsForCurrentVersion = 0,
            significantEventCount = if (resetEvents) 0 else significantEventCount,
        )
    }
}

/** The result of [ReviewPolicy.evaluate]. */
public sealed interface ReviewDecision {
    /** Every condition holds; it is a good moment to ask. */
    public data object Eligible : ReviewDecision

    /** At least one condition does not hold yet. */
    public data class NotYet(val blockers: List<ReviewBlocker>) : ReviewDecision

    public val isEligible: Boolean get() = this is Eligible
}

/** A single reason why a review prompt is not shown yet. */
public sealed interface ReviewBlocker {
    public data class NotEnoughLaunches(val current: Int, val required: Int) : ReviewBlocker

    public data class NotEnoughSignificantEvents(val current: Int, val required: Int) : ReviewBlocker

    public data class TooSoonAfterInstall(val remainingMillis: Long) : ReviewBlocker

    public data class CoolingDown(val remainingMillis: Long) : ReviewBlocker

    public data object VersionLimitReached : ReviewBlocker

    public data object LifetimeLimitReached : ReviewBlocker

    public data object OptedOut : ReviewBlocker
}

/** A short, human-readable explanation, handy for debug screens and logs. */
public fun ReviewBlocker.describe(): String = when (this) {
    is ReviewBlocker.NotEnoughLaunches -> "Launches: $current of $required"
    is ReviewBlocker.NotEnoughSignificantEvents -> "Significant events: $current of $required"
    is ReviewBlocker.TooSoonAfterInstall -> "Too soon after install: ${formatDuration(remainingMillis)} left"
    is ReviewBlocker.CoolingDown -> "Cooling down: ${formatDuration(remainingMillis)} left"
    ReviewBlocker.VersionLimitReached -> "Already asked for this version"
    ReviewBlocker.LifetimeLimitReached -> "Lifetime prompt limit reached"
    ReviewBlocker.OptedOut -> "User opted out"
}

internal fun formatDuration(millis: Long): String {
    val totalMinutes = (millis + 59_999) / 60_000
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
