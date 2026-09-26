package com.dynablox.launcher.core.modes

import android.util.Log
import com.dynablox.launcher.core.AppSettings
import com.dynablox.launcher.optimize.Optimizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies and reverts [GameMode]s on top of the existing [Optimizer].
 *
 * The engine owns three guarantees:
 *
 *  1. **Exclusivity.** Engaging a mode first restores everything the previous one changed, so two
 *     modes can never leave overlapping state behind. Without this, switching TURBO -> ECO would
 *     keep TURBO's animation scales at zero forever.
 *  2. **Honesty.** The result reports what actually applied, what was skipped for lack of a
 *     privilege and what failed. A mode is never reported as engaged when nothing happened.
 *  3. **Serialisation.** Applying is `suspend` and guarded by a mutex: double-tapping the rail
 *     cannot interleave an apply with a restore and strand the device in a half-optimised state.
 */
class ModeEngine(
    private val settings: AppSettings,
    private val optimizer: Optimizer,
) {

    /** Outcome of one engage/disengage, surfaced verbatim in the hub feedback pill. */
    data class Result(
        val mode: GameMode,
        val applied: Int,
        val skipped: Int,
        val failed: Int,
        val ramDeltaMb: Float,
        val durationMs: Long,
    ) {
        /** True when the mode changed at least one thing — an all-skipped run is not "engaged". */
        val engaged: Boolean get() = mode.id != GameModes.OFF.id && applied > 0
    }

    data class State(
        val mode: GameMode = GameModes.OFF,
        val busy: Boolean = false,
        /** Last run's outcome; null before the first engage of this process. */
        val lastResult: Result? = null,
    )

    private val _state = MutableStateFlow(State(mode = GameModes.byId(settings.activeModeId)))
    val state: StateFlow<State> = _state

    private val lock = Mutex()

    val active: GameMode get() = _state.value.mode

    /**
     * Engages [mode]. Re-selecting the mode that is already active disengages it, which is what a
     * toggle rail should do — otherwise the only way back to OFF would be a separate control.
     */
    suspend fun engage(mode: GameMode): Result = lock.withLock {
        val current = _state.value.mode
        val target = if (mode.id == current.id && mode.id != GameModes.OFF.id) GameModes.OFF else mode
        _state.value = _state.value.copy(busy = true)
        try {
            // Always unwind the previous mode first: modes are exclusive, not cumulative.
            var restoredRam = 0f
            var elapsed = 0L
            if (current.actions.isNotEmpty()) {
                val restore = runCatching { optimizer.restoreAll() }.getOrNull()
                if (restore != null) {
                    restoredRam += restore.ramDeltaMb
                    elapsed += restore.durationMs
                }
            }

            if (target.actions.isEmpty()) {
                val result = Result(
                    mode = target,
                    applied = 0,
                    skipped = 0,
                    failed = 0,
                    ramDeltaMb = restoredRam,
                    durationMs = elapsed,
                )
                persist(target)
                _state.value = State(mode = target, busy = false, lastResult = result)
                return@withLock result
            }

            val report = optimizer.run(target.actions)
            val applied = report.entries.count {
                it.state == Optimizer.State.APPLIED || it.state == Optimizer.State.RESTORED
            }
            val skipped = report.entries.count {
                it.state == Optimizer.State.SKIPPED || it.state == Optimizer.State.NEEDS_PERMISSION
            }
            val result = Result(
                mode = target,
                applied = applied,
                skipped = skipped,
                failed = report.failedCount,
                ramDeltaMb = restoredRam + report.ramDeltaMb,
                durationMs = elapsed + report.durationMs,
            )
            // A mode that could not change anything is not engaged — record OFF so the rail does
            // not show a lit chevron over a device that was never touched.
            persist(if (result.engaged) target else GameModes.OFF)
            _state.value = State(
                mode = if (result.engaged) target else GameModes.OFF,
                busy = false,
                lastResult = result,
            )
            result
        } catch (t: Throwable) {
            Log.w(TAG, "engage failed for ${mode.id}", t)
            val result = Result(mode, applied = 0, skipped = 0, failed = 1, ramDeltaMb = 0f, durationMs = 0L)
            _state.value = State(mode = GameModes.OFF, busy = false, lastResult = result)
            persist(GameModes.OFF)
            result
        }
    }

    /** Reverts everything the active mode changed and returns to [GameModes.OFF]. */
    suspend fun disengage(): Result = engage(GameModes.OFF)

    private fun persist(mode: GameMode) {
        settings.activeModeId = mode.id
    }

    private companion object {
        const val TAG = "ModeEngine"
    }
}
