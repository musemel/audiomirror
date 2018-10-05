package jay.audiomirror

import android.Manifest.permission.RECORD_AUDIO
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    runService()
  }

  private fun runService() {
    if (SDK_INT >= 23 && checkSelfPermission(RECORD_AUDIO) != PERMISSION_GRANTED) {
      requestPermissions(arrayOf(RECORD_AUDIO), 0)
      return
    }

    startFgService(Intent(this, AudioMirrorService::class.java))
  }

  private fun startFgService(intent: Intent): ComponentName? =
    if (SDK_INT >= 28) startForegroundService(intent) else startService(intent)

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
