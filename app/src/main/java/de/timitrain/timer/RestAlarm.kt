package de.timitrain.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper

object RestChime {
    private val lock = Any()
    private var lastToken = Long.MIN_VALUE

    fun playOnce(context: Context, token: Long) {
        synchronized(lock) {
            if (token == lastToken) return
            lastToken = token
        }
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 900)
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getLongExtra(EXTRA_TOKEN, Long.MIN_VALUE)
        if (token == Long.MIN_VALUE) return
        RestChime.playOnce(context, token)
    }

    companion object {
        const val EXTRA_TOKEN = "token"
    }
}

class RestScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(endsAtEpochMillis: Long) {
        val pending = pending(endsAtEpochMillis)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtEpochMillis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtEpochMillis, pending)
        }
    }

    fun cancel() {
        alarmManager.cancel(pending(0L))
    }

    private fun pending(endsAtEpochMillis: Long): PendingIntent {
        val intent = Intent(appContext, RestAlarmReceiver::class.java).apply {
            putExtra(RestAlarmReceiver.EXTRA_TOKEN, endsAtEpochMillis)
        }
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST = 41
    }
}
