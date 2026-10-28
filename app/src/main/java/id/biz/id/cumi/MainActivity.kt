package id.biz.id.cumi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import id.biz.id.cumi.ui.KaosKakiRoot
import id.biz.id.cumi.ui.theme.KaosKakiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KaosKakiTheme {
                KaosKakiRoot()
            }
        }
    }
}
