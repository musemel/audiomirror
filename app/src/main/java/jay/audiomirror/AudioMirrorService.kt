package jay.audiomirror

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioAttributes.CONTENT_TYPE_MUSIC
import android.media.AudioAttributes.USAGE_MEDIA
import android.media.AudioFormat
import android.media.AudioFormat.*
import android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY
import android.media.AudioManager.STREAM_MUSIC
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioTrack.MODE_STREAM
import android.media.MediaRecorder.AudioSource.MIC
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import jay.audiomirror.BuildConfig.APPLICATION_ID
import kotlin.concurrent.thread

class AudioMirrorService : Service() {
  var stopping = false
    private set
  var muted = false
    private set

  private val notification = ActivityNotification(this)

  private val inputBufferSize =
    AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN_MONO, FORMAT_ENCODING)
  private val outputBufferSize =
    AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT_MONO, FORMAT_ENCODING)

  private val input =
    AudioRecord(MIC, SAMPLE_RATE, CHANNEL_IN_MONO, FORMAT_ENCODING, inputBufferSize)
  private val output =
    AudioTrack(
      AudioAttributes.Builder().apply {
        setLegacyStreamType(STREAM_MUSIC)
        setUsage(USAGE_MEDIA)
        setContentType(CONTENT_TYPE_MUSIC)
      }.build(),
      AudioFormat.Builder().apply {
        setChannelMask(CHANNEL_OUT_MONO)
        setEncoding(FORMAT_ENCODING)
        setSampleRate(SAMPLE_RATE)
      }.build(),
      outputBufferSize,
      MODE_STREAM,
      AUDIO_SESSION_ID
    )

  private val noisyAudioReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) =
      if (intent?.action == ACTION_AUDIO_BECOMING_NOISY) mute() else Unit
  }

  override fun onBind(intent: Intent?) = null

  override fun onCreate() {
    if (SDK_INT >= 26) notification.createNotificationChannel()
    start()

    registerReceiver(noisyAudioReceiver, IntentFilter(ACTION_AUDIO_BECOMING_NOISY))
  }

  override fun onDestroy() = stop()

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_UNMUTE -> unmute()
      ACTION_MUTE -> mute()
      ACTION_KILL -> stopSelf()
    }
    return START_STICKY
  }

  private fun start() {
    startLoop()
    unmute()
  }

  private fun unmute() {
    muted = false
    notification.update()
  }

  private fun stop() {
    stopping = true
    mute()
  }

  private fun mute() {
    muted = true
    notification.update()
  }

  private fun startLoop() = thread {
    try {
      input.startRecording()
      output.play()

      val buffer = ByteArray(inputBufferSize)

      while (!stopping) if (muted) Thread.sleep(100) else {
        val size = input.read(buffer, 0, inputBufferSize)
        output.write(buffer, 0, size)
      }

      input.stop()
      output.stop()
    } catch (e: Throwable) {
      Log.e("AudioMirrorService", "Error with audio record or track", e)
    }
  }

  companion object {
    private const val SAMPLE_RATE = 44100
    private const val FORMAT_ENCODING = ENCODING_PCM_16BIT
    private const val AUDIO_SESSION_ID = 1

    const val ACTION_MUTE = "$APPLICATION_ID.MUTE"
    const val ACTION_UNMUTE = "$APPLICATION_ID.UNMUTE"
    const val ACTION_KILL = "$APPLICATION_ID.KILL"
  }
}
