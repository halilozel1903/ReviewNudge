package io.github.halilozel1903.reviewnudge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.halilozel1903.reviewnudge.core.ReviewState
import io.github.halilozel1903.reviewnudge.core.ReviewStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reviewNudgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "review_nudge")

/**
 * A [ReviewStore] backed by Jetpack DataStore. Survives process death and app updates.
 */
public class DataStoreReviewStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) : ReviewStore {

    public constructor(context: Context) : this(context.applicationContext.reviewNudgeDataStore)

    override val state: Flow<ReviewState?> = dataStore.data.map { it.toReviewState() }

    override suspend fun update(transform: (ReviewState?) -> ReviewState?): ReviewState? {
        var result: ReviewState? = null
        dataStore.edit { preferences ->
            result = transform(preferences.toReviewState())
            preferences.write(result)
        }
        return result
    }

    private object Keys {
        val firstSeen = longPreferencesKey("first_seen_at")
        val launches = intPreferencesKey("launch_count")
        val events = intPreferencesKey("significant_event_count")
        val lastPrompt = longPreferencesKey("last_prompt_at")
        val prompts = intPreferencesKey("prompt_count")
        val promptsForVersion = intPreferencesKey("prompts_for_version")
        val version = stringPreferencesKey("tracked_version")
        val optedOut = booleanPreferencesKey("opted_out")
    }

    private fun Preferences.toReviewState(): ReviewState? {
        val firstSeen = this[Keys.firstSeen] ?: return null
        return ReviewState(
            firstSeenAtMillis = firstSeen,
            launchCount = this[Keys.launches] ?: 0,
            significantEventCount = this[Keys.events] ?: 0,
            lastPromptAtMillis = this[Keys.lastPrompt],
            promptCount = this[Keys.prompts] ?: 0,
            promptsForCurrentVersion = this[Keys.promptsForVersion] ?: 0,
            trackedVersion = this[Keys.version],
            optedOut = this[Keys.optedOut] ?: false,
        )
    }

    private fun MutablePreferences.write(state: ReviewState?) {
        if (state == null) {
            clear()
            return
        }
        this[Keys.firstSeen] = state.firstSeenAtMillis
        this[Keys.launches] = state.launchCount
        this[Keys.events] = state.significantEventCount
        state.lastPromptAtMillis?.let { this[Keys.lastPrompt] = it } ?: remove(Keys.lastPrompt)
        this[Keys.prompts] = state.promptCount
        this[Keys.promptsForVersion] = state.promptsForCurrentVersion
        state.trackedVersion?.let { this[Keys.version] = it } ?: remove(Keys.version)
        this[Keys.optedOut] = state.optedOut
    }
}
