package local.sekigawa.monitornfc

import android.content.Context
import android.os.Vibrator
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*

//class NotifyStatus: CoroutineScope {
//    private var vibrateJob: Job? = null
//    private var mode: Int = 0
//    private var cancel: Boolean = false
//
//    fun startVibrate(): Boolean {
//
//        this.vibrateJob = GlobalScope.launch {
//            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
//            while (!cancel) {
//
//                when (mode) {
//                    0 -> vibrator.vibrate()
//                }
//            }
//        }
//        return true
//    }
//
//}