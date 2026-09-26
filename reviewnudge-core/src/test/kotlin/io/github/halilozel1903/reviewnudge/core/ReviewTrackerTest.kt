package io.github.halilozel1903.reviewnudge.core

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewTrackerTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 1_700_000_000_000L
    private val store = InMemoryReviewStore()

    private fun tracker(version: String = "1.0", policy: ReviewPolicy = ReviewPolicy.Default) =
        ReviewTracker(store = store, policy = policy, appVersion = version, clock = { now })

    @Test
    fun `first launch creates state`() = runTest {
        val state = tracker().recordLaunch()

        assertEquals(now, state.firstSeenAtMillis)
        assertEquals(1, state.launchCount)
        assertEquals("1.0", state.trackedVersion)
    }

    @Test
    fun `becomes eligible after launches, events and days`() = runTest {
        val tracker = tracker()
        repeat(5) { tracker.recordLaunch() }
        tracker.recordSignificantEvent(weight = 2)
        tracker.recordSignificantEvent()
        assertIs<ReviewDecision.NotYet>(tracker.currentDecision())

        now += 3 * day

        assertEquals(ReviewDecision.Eligible, tracker.currentDecision())
        assertEquals(ReviewDecision.Eligible, tracker.decision.first())
    }

    @Test
    fun `marking prompted starts the cooldown`() = runTest {
        val tracker = tracker(policy = ReviewPolicy.Eager)
        tracker.recordLaunch()
        tracker.recordLaunch()
        tracker.recordSignificantEvent()
        assertTrue(tracker.currentDecision().isEligible)

        val state = tracker.markPrompted()

        assertEquals(1, state.promptCount)
        assertEquals(now, state.lastPromptAtMillis)
        val blockers = assertIs<ReviewDecision.NotYet>(tracker.currentDecision()).blockers
        assertTrue(ReviewBlocker.VersionLimitReached in blockers)
        assertTrue(blockers.any { it is ReviewBlocker.CoolingDown })
    }

    @Test
    fun `new app version resets the per version counter`() = runTest {
        val v1 = tracker(version = "1.0", policy = ReviewPolicy.Eager)
        v1.recordLaunch()
        v1.recordLaunch()
        v1.recordSignificantEvent()
        v1.markPrompted()

        now += 31 * day
        val v2 = tracker(version = "2.0", policy = ReviewPolicy.Eager)

        assertEquals(ReviewDecision.Eligible, v2.currentDecision())
        val state = v2.state.first()!!
        assertEquals("2.0", state.trackedVersion)
        assertEquals(0, state.promptsForCurrentVersion)
        assertEquals(1, state.promptCount)
    }

    @Test
    fun `can reset events on update`() = runTest {
        val policy = ReviewPolicy.Eager.copy(resetEventsOnNewVersion = true)
        tracker(version = "1.0", policy = policy).recordSignificantEvent(weight = 4)

        val state = tracker(version = "1.1", policy = policy).recordLaunch()

        assertEquals(0, state.significantEventCount)
    }

    @Test
    fun `opt out and reset`() = runTest {
        val tracker = tracker()
        tracker.recordLaunch()
        tracker.optOut()
        assertTrue(ReviewBlocker.OptedOut in assertIs<ReviewDecision.NotYet>(tracker.currentDecision()).blockers)

        tracker.reset()

        assertNull(tracker.state.first())
    }
}
