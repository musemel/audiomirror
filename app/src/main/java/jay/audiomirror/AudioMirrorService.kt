package jay.audiomirror

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.Service
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import androidx.core.app.NotificationCompat
import jay.audiomirror.BuildConfig.APPLICATION_ID


class AudioMirrorService : Service() {

  private var stopping = false

  private val notificationManager: NotificationManager by lazy {
    getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  }

  override fun onBind(intent: Intent?) = null

  override fun onCreate() {
    if (SDK_INT >= 28) createNotifChannel()
    start()
  }

  override fun onDestroy() {
    stopping = true
    stop()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_UNMUTE -> unmute()
      ACTION_MUTE -> mute()
      ACTION_KILL -> stopSelf()
    }
    return START_STICKY
  }

  private fun start() {
    unmute()
  }

  private fun unmute() {
    val muteIntent = Intent(this, AudioMirrorService::class.java).setAction(ACTION_MUTE)
    val mutePendingIntent = PendingIntent.getService(this, 0, muteIntent, FLAG_UPDATE_CURRENT)

    val notif = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL).apply {
      setContentTitle(getString(R.string.active))
      setSmallIcon(R.drawable.mic)
      addAction(R.drawable.mic_off, getString(R.string.mute), mutePendingIntent)
      setOngoing(true)
    }.build()

    startForeground(NOTIFICATION_ID, notif)
  }

  private fun stop() {
    mute()
  }

  private fun mute() {
    val unmuteIntent = Intent(this, AudioMirrorService::class.java).setAction(ACTION_UNMUTE)
    val unmutePendingIntent = PendingIntent.getService(this, 0, unmuteIntent, FLAG_UPDATE_CURRENT)

    val deleteIntent = Intent(this, AudioMirrorService::class.java).setAction(ACTION_KILL)
    val deletePendingIntent = PendingIntent.getService(this, 0, deleteIntent, FLAG_UPDATE_CURRENT)

    val notif = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL).apply {
      setContentTitle(getString(R.string.inactive))
      setSmallIcon(R.drawable.mic_off)
      addAction(R.drawable.mic, getString(R.string.unmute), unmutePendingIntent)
      setDeleteIntent(deletePendingIntent)

      setOngoing(false)
    }.build()

    stopForeground(stopping)
    if (!stopping) notificationManager.notify(NOTIFICATION_ID, notif)
  }

  @TargetApi(26)
  private fun createNotifChannel() =
    notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL)
      ?: NotificationChannel(
        NOTIFICATION_CHANNEL, getString(R.string.channel),
        NotificationManager.IMPORTANCE_LOW
      ).also {
        notificationManager.createNotificationChannel(it)
      }

  companion object {
    private const val ACTION_UNMUTE = "$APPLICATION_ID.UNMUTE"
    private const val ACTION_MUTE = "$APPLICATION_ID.MUTE"
    private const val ACTION_KILL = "$APPLICATION_ID.KILL"
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_CHANNEL = "$APPLICATION_ID.ACTIVITY"
  }
}
