package id.biz.id.cumi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import id.biz.id.cumi.ui.CumiRoot
import id.biz.id.cumi.ui.theme.CumiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CumiTheme {
                CumiRoot()
            }
        }
    }
}
