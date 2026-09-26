package io.github.halilozel1903.reviewnudge.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists [ReviewState]. The Android artifact ships a DataStore implementation;
 * implement this interface to use your own storage.
 */
public interface ReviewStore {
    /** The stored state, or `null` before the first launch was recorded. */
    public val state: Flow<ReviewState?>

    /** Atomically replaces the state with the result of [transform] and returns it. */
    public suspend fun update(transform: (ReviewState?) -> ReviewState?): ReviewState?
}

/** A [ReviewStore] that lives in memory. Useful for tests and previews. */
public class InMemoryReviewStore(initial: ReviewState? = null) : ReviewStore {
    private val mutex = Mutex()
    private val current = MutableStateFlow(initial)

    override val state: Flow<ReviewState?> = current.asStateFlow()

    override suspend fun update(transform: (ReviewState?) -> ReviewState?): ReviewState? =
        mutex.withLock {
            transform(current.value).also { current.value = it }
        }
}
