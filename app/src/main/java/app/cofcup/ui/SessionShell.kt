package app.cofcup.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import app.cofcup.R

/**
 * The session shell: owns everything about a live session that must survive
 * screen switches (LiveSession, Settings, later Ratings / share screens).
 *
 * It is composed for as long as a live session exists, independent of which
 * session screen is on top, so:
 *  - the screen stays on,
 *  - timer-driven step chimes and vibration are played (skipped steps stay silent),
 *  - the Done save happens in the ViewModel's ticker, not from any screen.
 */
@Composable
fun SessionShell(vm: CuppingViewModel) {
    val live by vm.live.collectAsState()
    if (live == null) return

    // A cupping timer must stay visible: keep the screen on during the session.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // A soft chime and/or vibration whenever the timer advances to the next step.
    // The ViewModel only emits for timer-driven advances, so "Skip step" stays silent.
    val context = LocalContext.current
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    }
    val chimeId = remember(soundPool) { soundPool.load(context, R.raw.step_ding, 1) }
    DisposableEffect(soundPool) {
        onDispose { soundPool.release() }
    }
    val soundOn by vm.soundOn.collectAsState()
    val vibrateOn by vm.vibrateOn.collectAsState()
    val vibrator = remember { context.stepVibrator() }
    LaunchedEffect(soundPool, chimeId) {
        vm.stepChimes.collect {
            if (soundOn) soundPool.play(chimeId, 0.55f, 0.55f, 1, 0, 1f)
            if (vibrateOn) vibrator?.vibrate(CHIME_VIBRATION)
        }
    }
}

/** Quick double pulse — a "brrr brrr" — for step advances. */
private val CHIME_VIBRATION: VibrationEffect =
    VibrationEffect.createWaveform(longArrayOf(0, 60, 120, 60), -1)

/** Default Vibrator, working across API levels (26+). */
private fun Context.stepVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
