package jay.audiomirror

import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startForegroundService

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
