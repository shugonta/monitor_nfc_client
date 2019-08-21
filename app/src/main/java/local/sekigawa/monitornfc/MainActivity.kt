package local.sekigawa.monitornfc

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button

class MainActivity : AppCompatActivity() {
    val connected: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val connect_btn = findViewById<Button>(R.id.button) as Button
        connect_btn.setOnClickListener {
            if (connected) {

                connect_btn.setText(R.string.connect)
            } else {
                connect_btn.setText(R.string.connected)
            }
        }
    }
}
