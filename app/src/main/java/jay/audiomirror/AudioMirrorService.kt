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

  private val muteLock = Object()

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
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action != ACTION_AUDIO_BECOMING_NOISY) return

      Log.d("AudioMirrorService", "Received noisy audio broadcast, muting audio")
      mute()
    }
  }

  override fun onBind(intent: Intent?) = null

  override fun onCreate() {
    Log.d("AudioMirrorService", "Service created")

    if (SDK_INT >= 26) notification.createNotificationChannel()
    start()

    registerReceiver(noisyAudioReceiver, IntentFilter(ACTION_AUDIO_BECOMING_NOISY))
  }

  override fun onDestroy() = stop()

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d("AudioMirrorService", "Received command, action: ${intent?.action}")

    when (intent?.action) {
      ACTION_UNMUTE -> unmute()
      ACTION_MUTE -> mute()
      ACTION_START -> start()
      ACTION_KILL -> stopSelf()
      ACTION_RESTART -> restart()
    }
    return START_STICKY
  }

  private fun start() {
    Log.d("AudioMirrorService", "Starting")
    unmute()
    startLoop()
  }

  private fun unmute() {
    Log.d("AudioMirrorService", "Unmuting audio")
    muted = false
    synchronized(muteLock, muteLock::notifyAll)
    notification.update()
  }

  private fun stop() {
    Log.d("AudioMirrorService", "Stopping")
    stopping = true
    mute()
  }

  private fun mute() {
    Log.d("AudioMirrorService", "Muting audio")
    muted = true
    notification.update()
  }

  private fun restart() {
    Log.d("AudioMirrorService", "Restarting")
    synchronized(muteLock, muteLock::notifyAll)
    stop()
    start()
  }

  private fun startLoop() = thread(name = "AudioMirror loop") {
    Log.d("AudioMirrorService", "Starting record loop")
    stopping = false
    try {
      input.startRecording()
      output.play()

      val buffer = ByteArray(inputBufferSize)

      while (!stopping) {
        if (muted) synchronized(muteLock, muteLock::wait)

        val size = input.read(buffer, 0, inputBufferSize)
        output.write(buffer, 0, size)
      }
    } catch (e: Throwable) {
      Log.e("AudioMirrorService", "Error with audio record or track", e)
    } finally {
      try {
        input.stop()
        output.stop()
      } catch (e: Throwable) {
      }
    }
  }

  companion object {
    private const val SAMPLE_RATE = 44100
    private const val FORMAT_ENCODING = ENCODING_PCM_16BIT
    private const val AUDIO_SESSION_ID = 1

    const val ACTION_MUTE = "$APPLICATION_ID.MUTE"
    const val ACTION_UNMUTE = "$APPLICATION_ID.UNMUTE"
    const val ACTION_START = "$APPLICATION_ID.START"
    const val ACTION_KILL = "$APPLICATION_ID.KILL"
    const val ACTION_RESTART = "$APPLICATION_ID.RESTART"
  }
}
