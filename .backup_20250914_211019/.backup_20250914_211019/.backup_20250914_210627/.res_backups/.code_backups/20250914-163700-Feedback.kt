package 

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

object Feedback {
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }
    fun success(ctx: Context) {
        val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(30)
        }
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }
}