package jay.audiomirror

import android.app.Service
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
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
import android.preference.PreferenceManager
import android.util.Log
import jay.audiomirror.BuildConfig.APPLICATION_ID
import kotlin.concurrent.thread

class AudioMirrorService : Service(), OnSharedPreferenceChangeListener {
  var stopping = false
    private set
  var restarting = false
    private set
  var muted = false
    private set

  private val muteLock = Object()

  private val notification = ActivityNotification(this)

  private lateinit var prefs: SharedPreferences

  private var sampleRate = -1
  private var audioSource = -1

  private var inputBufferSize = -1
  private var outputBufferSize = -1

  private lateinit var input: AudioRecord
  private lateinit var output: AudioTrack

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

    prefs = PreferenceManager.getDefaultSharedPreferences(this)
    prefs.registerOnSharedPreferenceChangeListener(this)

    start()

    registerReceiver(noisyAudioReceiver, IntentFilter(ACTION_AUDIO_BECOMING_NOISY))
  }

  override fun onDestroy() {
    stop()
    unregisterReceiver(noisyAudioReceiver)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d("AudioMirrorService", "Received command, action: ${intent?.action}")

    when (intent?.action) {
      ACTION_UNMUTE -> unmute()
      ACTION_MUTE -> mute()
      ACTION_START -> start()
      ACTION_STOP -> stopSelf()
      ACTION_RESTART -> restart()
    }
    return START_STICKY
  }

  private fun initialize() {
    Log.d("AudioMirrorService", "Initializing")

    sampleRate = prefs.getInt(PREF_SAMPLE_RATE, 44100)
    audioSource = prefs.getInt(PREF_AUDIO_SOURCE, MIC)

    inputBufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)
    outputBufferSize = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_OUT_MONO, ENCODING_PCM_16BIT)

    if (::input.isInitialized) input.release()
    if (::output.isInitialized) output.release()

    input =
      AudioRecord(audioSource, sampleRate, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, inputBufferSize)
    output =
      AudioTrack(
        AudioAttributes.Builder().apply {
          setLegacyStreamType(STREAM_MUSIC)
          setUsage(USAGE_MEDIA)
          setContentType(CONTENT_TYPE_MUSIC)
        }.build(),
        AudioFormat.Builder().apply {
          setChannelMask(CHANNEL_OUT_MONO)
          setEncoding(ENCODING_PCM_16BIT)
          setSampleRate(sampleRate)
        }.build(),
        outputBufferSize,
        MODE_STREAM,
        /* audio session id */ 1
      )
  }

  private fun start() {
    initialize()
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
    restarting = true
    synchronized(muteLock, muteLock::notifyAll)
    stop()
    start()
    restarting = false
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

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
    when (key) {
      PREF_SAMPLE_RATE -> sampleRate = sharedPreferences.getInt(PREF_SAMPLE_RATE, 44100)
      PREF_AUDIO_SOURCE -> audioSource = sharedPreferences.getInt(PREF_AUDIO_SOURCE, MIC)
    }
    restart()
  }

  companion object {
    const val PREF_SAMPLE_RATE = "sample-rate"
    const val PREF_AUDIO_SOURCE = "audio-source"

    const val ACTION_MUTE = "$APPLICATION_ID.MUTE"
    const val ACTION_UNMUTE = "$APPLICATION_ID.UNMUTE"
    const val ACTION_START = "$APPLICATION_ID.START"
    const val ACTION_STOP = "$APPLICATION_ID.STOP"
    const val ACTION_RESTART = "$APPLICATION_ID.RESTART"
  }
}
