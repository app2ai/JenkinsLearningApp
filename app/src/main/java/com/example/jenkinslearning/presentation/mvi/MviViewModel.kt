package com.example.jenkinslearning.presentation.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Minimal MVI base:
 *  - the UI sends [I] intents through [onIntent]
 *  - the ViewModel reduces them into an immutable [S] state ([state])
 *  - one-time [E] effects (navigation, snackbars, opening a URL) go through a
 *    Channel, so each effect is delivered exactly once, even across
 *    configuration changes, and is never replayed to a new collector.
 */
abstract class MviViewModel<I, S, E>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    fun onIntent(intent: I) = handleIntent(intent)

    protected abstract fun handleIntent(intent: I)

    protected fun setState(reducer: S.() -> S) = _state.update(reducer)

    protected fun sendEffect(effect: E) {
        _effects.trySend(effect)
    }
}

/**
 * Collects one-time effects only while the screen is at least STARTED, so
 * effects aren't handled while the app is in the background, and are kept in
 * the channel's buffer until the screen is visible again.
 */
@Composable
fun <E> CollectEffects(effects: Flow<E>, onEffect: suspend (E) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { currentOnEffect(it) }
        }
    }
}
