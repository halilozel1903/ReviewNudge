package io.github.halilozel1903.reviewnudge.sample

import android.app.Application
import io.github.halilozel1903.reviewnudge.ReviewNudge
import io.github.halilozel1903.reviewnudge.core.ReviewPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SampleApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var reviewNudge: ReviewNudge
        private set

    override fun onCreate() {
        super.onCreate()
        reviewNudge = ReviewNudge.create(
            context = this,
            policy = ReviewPolicy.Eager,
            // The fake manager completes without UI, so the sample works on any emulator.
            useFakeReviewManager = true,
        )
        appScope.launch { reviewNudge.recordLaunch() }
    }
}
