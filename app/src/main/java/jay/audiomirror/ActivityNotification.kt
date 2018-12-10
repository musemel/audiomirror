package jay.audiomirror

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import jay.audiomirror.AudioMirrorService.Companion.ACTION_MUTE
import jay.audiomirror.AudioMirrorService.Companion.ACTION_RESTART
import jay.audiomirror.AudioMirrorService.Companion.ACTION_STOP
import jay.audiomirror.AudioMirrorService.Companion.ACTION_UNMUTE
import jay.audiomirror.BuildConfig.APPLICATION_ID

class ActivityNotification(private val service: AudioMirrorService) {

  private val notificationManager: NotificationManager by lazy {
    service.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  }

  @TargetApi(26)
  fun createNotificationChannel(): NotificationChannel {
    val channel = notificationManager.getNotificationChannel(CHANNEL)
    if (channel != null) return channel

    Log.d("ActivityNotification", "Creating notification channel")

    val newChannel =
      NotificationChannel(CHANNEL, service.getString(R.string.channel), NotificationManager.IMPORTANCE_LOW)
    notificationManager.createNotificationChannel(newChannel)
    return newChannel
  }

  fun update() {
    Log.d("ActivityNotification", "Updating notification")

    if (service.stopping) {
      service.stopForeground(false)
      notificationManager.cancel(ID)

      if (!service.restarting) return
    }

    val toggleIntent = PendingIntent.getService(
      service, /* request code */ 0,
      Intent(service, AudioMirrorService::class.java)
        .setAction(if (service.muted) ACTION_UNMUTE else ACTION_MUTE),
      FLAG_UPDATE_CURRENT
    )

    val restartIntent = PendingIntent.getService(
      service, /* request code */ 0,
      Intent(service, AudioMirrorService::class.java)
        .setAction(ACTION_RESTART),
      FLAG_UPDATE_CURRENT
    )

    val deleteIntent = PendingIntent.getService(
      service, /* request code */ 0,
      Intent(service, AudioMirrorService::class.java)
        .setAction(ACTION_STOP),
      FLAG_UPDATE_CURRENT
    )

    val notif = NotificationCompat.Builder(service, CHANNEL).apply {
      setContentTitle(service.getString(if (service.muted) R.string.inactive else R.string.active))
      setSmallIcon(if (service.muted) R.drawable.mic_off else R.drawable.mic)
      addAction(
        if (service.muted) R.drawable.mic else R.drawable.mic_off,
        service.getString(if (service.muted) R.string.unmute else R.string.mute),
        toggleIntent
      )
      addAction(R.drawable.restart, service.getString(R.string.restart), restartIntent)
      setDeleteIntent(deleteIntent)
      setOngoing(!service.muted)
    }.build()

    if (service.muted) {
      service.stopForeground(false)
      notificationManager.notify(ID, notif)
    } else {
      service.startForeground(ID, notif)
    }
  }

  companion object {
    private const val ID = 1
    private const val CHANNEL = "$APPLICATION_ID.ACTIVITY"
  }
}
