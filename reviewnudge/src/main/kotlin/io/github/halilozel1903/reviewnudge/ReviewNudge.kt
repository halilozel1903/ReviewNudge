package io.github.halilozel1903.reviewnudge

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import io.github.halilozel1903.reviewnudge.core.ReviewBlocker
import io.github.halilozel1903.reviewnudge.core.ReviewDecision
import io.github.halilozel1903.reviewnudge.core.ReviewPolicy
import io.github.halilozel1903.reviewnudge.core.ReviewState
import io.github.halilozel1903.reviewnudge.core.ReviewStore
import io.github.halilozel1903.reviewnudge.core.ReviewTracker
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Asks for a Google Play review at the right moment, and only then.
 *
 * ```kotlin
 * val nudge = ReviewNudge.create(context)
 * nudge.recordLaunch()                       // Application.onCreate
 * nudge.recordSignificantEvent()             // after a good moment
 * nudge.requestReviewIfEligible(activity)    // at a natural pause
 * ```
 */
public class ReviewNudge(
    public val tracker: ReviewTracker,
    private val reviewManager: ReviewManager,
) {
    /** The current state; `null` before the first recorded launch. */
    public val state: Flow<ReviewState?> get() = tracker.state

    /** The live decision, handy for debug UIs. */
    public val decision: Flow<ReviewDecision> get() = tracker.decision

    public val policy: ReviewPolicy get() = tracker.policy

    public suspend fun recordLaunch(): ReviewState = tracker.recordLaunch()

    public suspend fun recordSignificantEvent(weight: Int = 1): ReviewState =
        tracker.recordSignificantEvent(weight)

    public suspend fun optOut(): ReviewState = tracker.optOut()

    public suspend fun reset(): Unit = tracker.reset()

    public suspend fun currentDecision(): ReviewDecision = tracker.currentDecision()

    /**
     * Launches the Play in-app review flow if the [policy] allows it.
     *
     * Play decides on its own whether the dialog is actually displayed (it has
     * its own quota), and never tells the app whether the user rated. That is by
     * design, so [ReviewOutcome.Launched] only means the flow was handed to Play.
     */
    public suspend fun requestReviewIfEligible(activity: Activity): ReviewOutcome =
        when (val decision = tracker.currentDecision()) {
            is ReviewDecision.NotYet -> ReviewOutcome.Skipped(decision.blockers)
            ReviewDecision.Eligible -> launch(activity)
        }

    /**
     * Launches the review flow regardless of the policy, for example from a
     * "Rate this app" item in settings. The prompt still counts towards limits.
     */
    public suspend fun requestReviewNow(activity: Activity): ReviewOutcome = launch(activity)

    private suspend fun launch(activity: Activity): ReviewOutcome = try {
        val reviewInfo = reviewManager.requestReview()
        tracker.markPrompted()
        reviewManager.launchReview(activity, reviewInfo)
        ReviewOutcome.Launched
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ReviewOutcome.Failed(e)
    }

    public companion object {
        /**
         * Creates a [ReviewNudge] backed by DataStore and the real Play review API.
         *
         * @param useFakeReviewManager Use Play's `FakeReviewManager`, which shows
         *   nothing but completes successfully. Handy for debug builds and emulators.
         */
        public fun create(
            context: Context,
            policy: ReviewPolicy = ReviewPolicy.Default,
            appVersion: String = context.appVersionName(),
            store: ReviewStore = DataStoreReviewStore(context),
            useFakeReviewManager: Boolean = false,
        ): ReviewNudge {
            val appContext = context.applicationContext
            val manager = if (useFakeReviewManager) {
                FakeReviewManager(appContext)
            } else {
                ReviewManagerFactory.create(appContext)
            }
            return ReviewNudge(
                tracker = ReviewTracker(store = store, policy = policy, appVersion = appVersion),
                reviewManager = manager,
            )
        }
    }
}

/** What happened when a review was requested. */
public sealed interface ReviewOutcome {
    /** The flow was handed to Google Play. */
    public data object Launched : ReviewOutcome

    /** The policy said "not yet". */
    public data class Skipped(val blockers: List<ReviewBlocker>) : ReviewOutcome

    /** Play services returned an error, for example on a device without the Play Store. */
    public data class Failed(val error: Exception) : ReviewOutcome
}

internal fun Context.appVersionName(): String {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return info.versionName ?: "unknown"
}
