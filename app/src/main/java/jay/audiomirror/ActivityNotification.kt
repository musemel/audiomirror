package jay.audiomirror

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import jay.audiomirror.AudioMirrorService.Companion.ACTION_KILL
import jay.audiomirror.AudioMirrorService.Companion.ACTION_MUTE
import jay.audiomirror.AudioMirrorService.Companion.ACTION_UNMUTE
import jay.audiomirror.BuildConfig.APPLICATION_ID

class ActivityNotification(private val service: AudioMirrorService) {

  private val notificationManager: NotificationManager by lazy {
    service.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  }

  @TargetApi(26)
  fun createNotificationChannel(): NotificationChannel {
    Log.d(javaClass.simpleName, "Creating notification channel")

    return notificationManager.getNotificationChannel(CHANNEL)
      ?: NotificationChannel(CHANNEL, service.getString(R.string.channel), IMPORTANCE_LOW)
        .also(notificationManager::createNotificationChannel)
  }

  fun update() {
    Log.d(javaClass.simpleName, "Updating notification")

    val toggleIntent = PendingIntent.getService(
      service, /* request code */ 0,
      Intent(service, AudioMirrorService::class.java)
        .setAction(if (service.muted) ACTION_UNMUTE else ACTION_MUTE),
      FLAG_UPDATE_CURRENT
    )

    val deleteIntent = PendingIntent.getService(
      service, /* request code */ 0,
      Intent(service, AudioMirrorService::class.java)
        .setAction(ACTION_KILL),
      FLAG_UPDATE_CURRENT
    )

    val notif = NotificationCompat.Builder(service, CHANNEL).apply {
      setContentTitle(service.getString(if (service.muted) R.string.inactive else R.string.active))
      setSmallIcon(if (service.muted) R.drawable.mic_off else R.drawable.mic)
      addAction(
        if (service.muted) R.drawable.mic else R.drawable.mic_off,
        service.getString(if (service.muted) R.string.unmute else R.string.unmute),
        toggleIntent
      )
      setDeleteIntent(deleteIntent)
      setOngoing(!service.muted)
    }.build()

    service.stopForeground(service.stopping)
    if (!service.stopping) notificationManager.notify(ID, notif)
  }

  companion object {
    private const val ID = 1
    private const val CHANNEL = "$APPLICATION_ID.ACTIVITY"
  }
}
