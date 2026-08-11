package nl.dicomcamera.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nl.dicomcamera.app.ui.Phase3App
import nl.dicomcamera.app.ui.theme.DicomCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DicomCameraTheme {
                Phase3App()
            }
        }
    }
}
