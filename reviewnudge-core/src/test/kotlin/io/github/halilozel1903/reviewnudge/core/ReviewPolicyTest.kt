package io.github.halilozel1903.reviewnudge.core

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReviewPolicyTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val installedAt = 1_000_000_000_000L

    private val ready = ReviewState(
        firstSeenAtMillis = installedAt,
        launchCount = 5,
        significantEventCount = 3,
        trackedVersion = "1.0",
    )

    @Test
    fun `eligible when every condition holds`() {
        val decision = ReviewPolicy.Default.evaluate(ready, installedAt + 3 * day)
        assertEquals(ReviewDecision.Eligible, decision)
        assertTrue(decision.isEligible)
    }

    @Test
    fun `lists every blocker for a fresh install`() {
        val fresh = ReviewState(firstSeenAtMillis = installedAt, launchCount = 1)
        val decision = assertIs<ReviewDecision.NotYet>(ReviewPolicy.Default.evaluate(fresh, installedAt))

        assertEquals(
            listOf(
                ReviewBlocker.NotEnoughLaunches(current = 1, required = 5),
                ReviewBlocker.NotEnoughSignificantEvents(current = 0, required = 3),
                ReviewBlocker.TooSoonAfterInstall(remainingMillis = 3 * day),
            ),
            decision.blockers,
        )
    }

    @Test
    fun `cooldown blocks until it has passed`() {
        val prompted = ready.copy(lastPromptAtMillis = installedAt + 10 * day, promptCount = 1)
        val policy = ReviewPolicy.Default.copy(maxPromptsPerVersion = 5)

        val during = assertIs<ReviewDecision.NotYet>(policy.evaluate(prompted, installedAt + 40 * day))
        assertEquals(listOf(ReviewBlocker.CoolingDown(remainingMillis = 30 * day)), during.blockers)

        assertEquals(ReviewDecision.Eligible, policy.evaluate(prompted, installedAt + 70 * day))
    }

    @Test
    fun `per version and lifetime limits`() {
        val policy = ReviewPolicy.Default.copy(cooldownDays = 0, maxPromptsTotal = 2)
        val now = installedAt + 100 * day

        val sameVersion = ready.copy(promptCount = 1, promptsForCurrentVersion = 1)
        assertEquals(
            listOf(ReviewBlocker.VersionLimitReached),
            assertIs<ReviewDecision.NotYet>(policy.evaluate(sameVersion, now)).blockers,
        )

        val exhausted = ready.copy(promptCount = 2)
        assertEquals(
            listOf(ReviewBlocker.LifetimeLimitReached),
            assertIs<ReviewDecision.NotYet>(policy.evaluate(exhausted, now)).blockers,
        )
    }

    @Test
    fun `opting out always blocks`() {
        val decision = ReviewPolicy.Eager.evaluate(ready.copy(optedOut = true), installedAt + day)
        assertEquals(listOf(ReviewBlocker.OptedOut), assertIs<ReviewDecision.NotYet>(decision).blockers)
    }

    @Test
    fun `rejects invalid configuration`() {
        assertFailsWith<IllegalArgumentException> { ReviewPolicy(minLaunches = -1) }
        assertFailsWith<IllegalArgumentException> { ReviewPolicy(maxPromptsTotal = 0) }
    }

    @Test
    fun `describes blockers for humans`() {
        assertEquals("Launches: 1 of 5", ReviewBlocker.NotEnoughLaunches(1, 5).describe())
        assertEquals("Cooling down: 2d 3h left", ReviewBlocker.CoolingDown(2 * day + TimeUnit.HOURS.toMillis(3)).describe())
        assertEquals("Too soon after install: 1m left", ReviewBlocker.TooSoonAfterInstall(1_000).describe())
    }
}
