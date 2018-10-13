package jay.audiomirror

import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.media.AudioFormat.CHANNEL_IN_MONO
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.AudioRecord
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startForegroundService
import jay.audiomirror.AudioMirrorService.Companion.PREF_SAMPLE_RATE
import kotlinx.android.synthetic.main.main.*

class MainActivity : AppCompatActivity() {

  private val sampleRates = listOf(8000, 11025, 16000, 22050, 44100, 48000)
    .filter { AudioRecord.getMinBufferSize(it, CHANNEL_IN_MONO, ENCODING_PCM_16BIT) > 0 }

  private val sampleRatesHz = sampleRates.map { "$it Hz" }

  private val prefs: SharedPreferences by lazy {
    PreferenceManager.getDefaultSharedPreferences(this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    runService()

    sampleRateSpinner.adapter =
      ArrayAdapter(this, android.R.layout.simple_spinner_item, sampleRatesHz)
        .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    sampleRateSpinner.setSelection(sampleRates.indexOf(prefs.getInt(PREF_SAMPLE_RATE, 44100)))
    sampleRateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>) = Unit
      override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        prefs.edit().putInt(PREF_SAMPLE_RATE, sampleRates[position]).apply()
      }
    }


  }

  private fun runService() {
    if (SDK_INT >= 23 && checkSelfPermission(RECORD_AUDIO) != PERMISSION_GRANTED) {
      Log.d("MainActivity", "Requesting record audio permissions")
      requestPermissions(arrayOf(RECORD_AUDIO), 0)
      return
    }

    Log.d("MainActivity", "Starting service")
    startForegroundService(this, Intent(this, AudioMirrorService::class.java))
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    if (grantResults.getOrNull(permissions.indexOf(RECORD_AUDIO)) == PERMISSION_GRANTED) {
      runService()
    }
  }
}
