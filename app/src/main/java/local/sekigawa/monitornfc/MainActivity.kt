package local.sekigawa.monitornfc

import android.Manifest;
import androidx.appcompat.app.AppCompatActivity
import android.Manifest.permission
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.content.pm.PackageManager
import android.annotation.TargetApi
import android.view.InputDevice.getDevice
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.collections.ArrayList
import local.sekigawa.monitornfc.BLEFrame
import java.text.SimpleDateFormat
import kotlin.concurrent.schedule

class MainActivity : AppCompatActivity() {
    val connected: Boolean = false

    private val TAG = "DEVICE_INFO"
    private val REQUEST_ENABLE_BT = 1
    private val REQUEST_CODE_LOCATE_PERMISSION = 5

    //PERIPHERAL_NAME
    private val PERIPHERAL_NAME = "nfcTrans"
    //UUID
    private val CUSTOM_SERVICE_UUID = "54c3259f-a142-4711-bbca-2efba019868e"
    private val CUSTOM_CHARACTERSTIC_UUID = "5170e77f-4076-40b7-9a87-15fbe60e816d"
    private val SSID_CHARACTERSTIC_UUID = "5f9207af-19d1-4151-a075-eb8d24db496f"

    //BLE
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBleGatt: BluetoothGatt? = null
    private var mBleGattQueue: BluetoothGattQueue? = null
    private var mBluetoothGattCharacteristic: BluetoothGattCharacteristic? = null
    private var mBluetoothGattSSIDCharacteristic: BluetoothGattCharacteristic? = null

    //Androidの固定値
    private val ANDROID_CENTRAL_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    private var SSID_LIST: ArrayList<String>? = null
    private var spinner_adapter: ArrayAdapter<String>? = null

    private var inc_date_chklist: Map<String, CheckBox>? = null
    private var exc_date_chklist: Map<String, CheckBox>? = null
    private var incDate: ArrayList<String>? = null
    private var incStartTime: Date? = null
    private var incEndTime: Date? = null
    private var excDate: ArrayList<String>? = null
    private var excStartTime: Date? = null
    private var excEndTime: Date? = null

    private var vibrator: Vibrator? = null
    private var player: MediaPlayer? = null
    private var timer: Timer? = null
    private var timer_cnt: Int = 0
    private var notify_alert: AlertDialog? = null
    private var ble_status: BLEFrame? = null

    val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // デバイスがBLEに対応していなければトースト表示.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        InitializeBleSetting()

        val connect_btn = findViewById<Button>(R.id.connect_btn) as Button
        connect_btn.setOnClickListener {
            if (mBleGatt != null) {
                //切断
                DisConnectDevice()
            } else {
                //接続
                connect_btn.setText(R.string.connecting)
                connect_btn.isEnabled = false
                ConnectBleDevice()
            }
        }


        val gson = Gson();
        val sharedPreferences = getSharedPreferences("monitor_nfc", Context.MODE_PRIVATE)
        val ssidList = gson.fromJson<ArrayList<String>>(
            sharedPreferences.getString("ssid_list", "[]"),
            object : TypeToken<ArrayList<String>>() {}.type
        )
        this.SSID_LIST = ArrayList(ssidList)

        val ssid_btn = findViewById<Button>(R.id.ssid_btn)
        ssid_btn.setOnClickListener {
            if (ssid_btn.text != null && ssid_btn.text == getString(R.string.add)) {
                val editText = EditText(this);
                AlertDialog.Builder(this)
                    .setTitle("SSID名")
                    .setView(editText)
                    .setPositiveButton("OK") { dialog, which ->
                        val target_ssid = editText.text.toString()
                        SSID_LIST?.add(target_ssid)
                        spinner_adapter?.add(target_ssid)
                        spinner_ssid.setSelection(spinner_ssid.count - 1)
                        if (mBleGattQueue != null) {
                            val pref = getSharedPreferences("monitor_nfc", Context.MODE_PRIVATE)
                            pref.edit().putString("ssid_list", Gson().toJson(SSID_LIST!!)).apply()
                            //不整合確認後にwriteされるはず
                            mBleGattQueue?.push(
                                "readCharacteristic", mBluetoothGattSSIDCharacteristic!!
                            )
                        }
                    }.show();
            } else {
                val target_ssid = spinner_ssid.selectedItem.toString()
                AlertDialog.Builder(this)
                    .setTitle("SSID削除")
                    .setMessage(target_ssid + "を削除します")
                    .setPositiveButton("OK") { dialog, which ->
                        SSID_LIST?.remove(target_ssid)
                        spinner_adapter?.remove(target_ssid)
                        if (mBleGattQueue != null) {
                            val pref = getSharedPreferences("monitor_nfc", Context.MODE_PRIVATE)
                            pref.edit().putString("ssid_list", Gson().toJson(SSID_LIST!!)).apply()
                            //不整合確認後にwriteされるはず
                            mBleGattQueue?.push(
                                "readCharacteristic", mBluetoothGattSSIDCharacteristic!!
                            )
                        }
                    }.show();
            }
        }

        ssidList.add(0, "新規登録")

        spinner_adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            ssidList
        )

        spinner_adapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val spinner_ssid = findViewById<Spinner>(R.id.spinner_ssid)
        spinner_ssid.isEnabled = false
        spinner_ssid.adapter = spinner_adapter
        spinner_ssid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val spinnerParent = parent as Spinner
                val item = spinnerParent.selectedItem as String
                if (item == "新規登録" && spinnerParent.selectedItemId == 0L) {
                    ssid_btn.setText(R.string.add)
                } else {
                    ssid_btn.setText(R.string.del)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                ssid_btn.setText(R.string.add)
            }
        }


        //監視時間周り
        inc_date_chklist = mapOf(
            "sun" to (findViewById<CheckBox>(R.id.incdate_chk_sun)),
            "mon" to (findViewById<CheckBox>(R.id.incdate_chk_mon)),
            "tue" to (findViewById<CheckBox>(R.id.incdate_chk_tue)),
            "wed" to (findViewById<CheckBox>(R.id.incdate_chk_wed)),
            "thu" to (findViewById<CheckBox>(R.id.incdate_chk_thu)),
            "fri" to (findViewById<CheckBox>(R.id.incdate_chk_fri)),
            "sat" to (findViewById<CheckBox>(R.id.incdate_chk_sat))
        )

        exc_date_chklist = mapOf(
            "sun" to (findViewById<CheckBox>(R.id.excdate_chk_sun)),
            "mon" to (findViewById<CheckBox>(R.id.excdate_chk_mon)),
            "tue" to (findViewById<CheckBox>(R.id.excdate_chk_tue)),
            "wed" to (findViewById<CheckBox>(R.id.excdate_chk_wed)),
            "thu" to (findViewById<CheckBox>(R.id.excdate_chk_thu)),
            "fri" to (findViewById<CheckBox>(R.id.excdate_chk_fri)),
            "sat" to (findViewById<CheckBox>(R.id.excdate_chk_sat))
        )

        val inc_date = gson.fromJson<ArrayList<String>>(
            sharedPreferences.getString("inc_date", "['mon','tue','wed','thu','fri']"),
            object : TypeToken<ArrayList<String>>() {}.type
        )
        val inc_start_time = sharedPreferences.getString("inc_start_time", "00:00")
        val inc_end_time = sharedPreferences.getString("inc_end_time", "23:59")

        val exc_date = gson.fromJson<ArrayList<String>>(
            sharedPreferences.getString("exc_date", "['sun','sat']"),
            object : TypeToken<ArrayList<String>>() {}.type
        )
        val exc_start_time = sharedPreferences.getString("exc_start_time", "0:00")
        val exc_end_time = sharedPreferences.getString("exc_end_time", "23:59")

        //コントロール設定
        inc_date.forEach {
            inc_date_chklist!![it]?.isChecked = true
        }
        exc_date.forEach {
            exc_date_chklist!![it]?.isChecked = true
        }

        //時刻反映
        val formatter = SimpleDateFormat("H:mm", Locale.JAPAN)

        val inc_start_txt = findViewById<EditText>(R.id.incdate_start_txt)
        val inc_end_txt = findViewById<EditText>(R.id.incdate_end_txt)

        val inc_start_date = formatter.parse(inc_start_time!!)
        val inc_end_date = formatter.parse(inc_end_time!!)
        inc_start_txt.setText(formatter.format(inc_start_date!!), TextView.BufferType.NORMAL)
        inc_end_txt.setText(formatter.format(inc_end_date!!), TextView.BufferType.NORMAL)

        val exc_start_txt = findViewById<EditText>(R.id.excdate_start_txt)
        val exc_end_txt = findViewById<EditText>(R.id.excdate_end_txt)

        val exc_start_date = formatter.parse(exc_start_time!!)
        val exc_end_date = formatter.parse(exc_end_time!!)
        exc_start_txt.setText(formatter.format(exc_start_date!!), TextView.BufferType.NORMAL)
        exc_end_txt.setText(formatter.format(exc_end_date!!), TextView.BufferType.NORMAL)


        val settime_btn = findViewById<Button>(R.id.settime_btn)
        if (setTime())
            settime_btn.isEnabled = false

        //イベントハンドラ設定
        settime_btn.setOnClickListener() {
            if (setTime())
                settime_btn.isEnabled = false
            statusCheck()
        }

        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                findViewById<Button>(R.id.settime_btn).isEnabled = true
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        }

        val chkboxClickListener = object : View.OnClickListener {
            override fun onClick(p0: View?) {
                findViewById<Button>(R.id.settime_btn).isEnabled = true
            }
        }

        inc_start_txt.addTextChangedListener(textWatcher)
        inc_end_txt.addTextChangedListener(textWatcher)
        exc_start_txt.addTextChangedListener(textWatcher)
        exc_end_txt.addTextChangedListener(textWatcher)

        inc_date_chklist!!.forEach { (k, v) ->
            v.setOnClickListener(chkboxClickListener)
        }
        exc_date_chklist!!.forEach { (k, v) ->
            v.setOnClickListener(chkboxClickListener)
        }

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        player = MediaPlayer()
    }

    fun setTime(): Boolean {
        val formatter = SimpleDateFormat("H:mm", Locale.JAPAN)

        val inc_start_txt = findViewById<EditText>(R.id.incdate_start_txt)
        val inc_end_txt = findViewById<EditText>(R.id.incdate_end_txt)
        this.incDate = ArrayList<String>()

        try {
            this.incStartTime = formatter.parse(inc_start_txt.text.toString())
            this.incEndTime = formatter.parse(inc_end_txt.text.toString())
        } catch (t: Throwable) {
            return false
        }
        inc_start_txt.setText(formatter.format(this.incStartTime!!), TextView.BufferType.NORMAL)
        inc_end_txt.setText(formatter.format(this.incEndTime!!), TextView.BufferType.NORMAL)

        if (inc_date_chklist != null) {
            inc_date_chklist!!.forEach { (k, v) ->
                if (v.isChecked) {
                    incDate!!.add(k)
                }
            }
        }

        val exc_start_txt = findViewById<EditText>(R.id.excdate_start_txt)
        val exc_end_txt = findViewById<EditText>(R.id.excdate_end_txt)
        this.excDate = ArrayList<String>()

        try {
            this.excStartTime = formatter.parse(exc_start_txt.text.toString())
            this.excEndTime = formatter.parse(exc_end_txt.text.toString())
        } catch (t: Throwable) {
            return false
        }
        exc_start_txt.setText(formatter.format(this.excStartTime!!), TextView.BufferType.NORMAL)
        exc_end_txt.setText(formatter.format(this.excEndTime!!), TextView.BufferType.NORMAL)


        if (exc_date_chklist != null) {
            exc_date_chklist!!.forEach { (k, v) ->
                if (v.isChecked) {
                    excDate!!.add(k)
                }
            }
        }

        //保存
        val pref = getSharedPreferences("monitor_nfc", Context.MODE_PRIVATE)
        pref.edit().putString("inc_start_time", formatter.format(this.incStartTime!!)).apply()
        pref.edit().putString("inc_end_time", formatter.format(this.incEndTime!!)).apply()
        pref.edit().putString("inc_date", Gson().toJson(this.incDate!!)).apply()
        pref.edit().putString("exc_start_time", formatter.format(this.excStartTime!!)).apply()
        pref.edit().putString("exc_end_time", formatter.format(this.excEndTime!!)).apply()
        pref.edit().putString("exc_date", Gson().toJson(this.excDate!!)).apply()

        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBleGatt != null) {
            mBleGatt?.disconnect()
        }
    }

    /**
     * BLEの初期設定
     */
    private fun InitializeBleSetting() {
        //bluetoothがONになっているか確認
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE)
        if (bluetoothManager is BluetoothManager) {
            mBluetoothAdapter = bluetoothManager.getAdapter()
        }

        if (mBluetoothAdapter == null || !mBluetoothAdapter!!.isEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //permission
        if (PermissionChecker.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            requestLocatePermission();
            return;
        }
    }


    /**
     */
    private fun requestLocatePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            AlertDialog.Builder(this)
                .setTitle("パーミッションの追加説明")
                .setMessage("このアプリを使うには位置情報の許可が必要です")
                .setPositiveButton(
                    android.R.string.ok,
                    DialogInterface.OnClickListener() { _, _ ->
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            REQUEST_CODE_LOCATE_PERMISSION
                        )
                    })
                .create()
                .show();
            return;
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_CODE_LOCATE_PERMISSION
        );
        return;
    }

    /**
     * ConnctBleDevice
     */
    private fun ConnectBleDevice() {
        val scanner = mBluetoothAdapter?.getBluetoothLeScanner()
        val scanFilter =
            ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(CUSTOM_SERVICE_UUID))
                .build();
        val scanFilterList = ArrayList<ScanFilter>();
        scanFilterList.add(scanFilter);
        val scanSettings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();

        scanner?.startScan(scanFilterList, scanSettings, mScanCallback)
    }

    /**
     * DisConncetDevice
     */
    private fun DisConnectDevice() {
        mBleGatt?.disconnect()
    }


    /**
     * callback
     * ScanCallback
     * BLEの探索
     */
    private val mScanCallback = (object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            Log.i(TAG, "onScanResult()")
            Log.i(TAG, "DeviceName:" + result.getDevice().getName())
            Log.i(TAG, "DeviceAddr:" + result.getDevice().getAddress())
            Log.i(TAG, "RSSI:" + result.getRssi())
            Log.i(TAG, "UUID:" + result.getScanRecord()?.getServiceUuids())

            //接続するPeripheralNameが見つかったら接続
            if (PERIPHERAL_NAME.equals(result.getDevice().getName(), ignoreCase = true)) {
                mBluetoothAdapter?.getBluetoothLeScanner()?.stopScan(this) //探索を停止

                result.getDevice().connectGatt(this@MainActivity, false, mGattCallback)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            mBluetoothAdapter?.getBluetoothLeScanner()?.stopScan(this) //探索を停止
            super.onScanFailed(errorCode)
        }
    })


    /**
     * CallBack
     * GATTの処理関係
     * Peripheralへの接続,切断,データのやりとり
     */
    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // 接続できたらサービスの検索
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) { //マイコンの応答がなくなった時の処理
                mBleGatt?.close()
                mBleGatt = null
                handler.post(Runnable {
                    Toast.makeText(
                        this@MainActivity,
                        "切断されました",
                        Toast.LENGTH_SHORT
                    ).show()
                    disconnected()
                })
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID.fromString(CUSTOM_SERVICE_UUID))

                if (service != null) {
                    mBleGatt = gatt
                    if (mBleGatt != null) {
                        mBleGattQueue = BluetoothGattQueue(mBleGatt!!)
                    }

                    mBluetoothGattCharacteristic =
                        service.getCharacteristic(UUID.fromString(CUSTOM_CHARACTERSTIC_UUID))
                    if (mBluetoothGattCharacteristic != null) {
                        val registered =
                            gatt.setCharacteristicNotification(
                                mBluetoothGattCharacteristic,
                                true
                            )
                        val descriptor = mBluetoothGattCharacteristic?.getDescriptor(
                            UUID.fromString(ANDROID_CENTRAL_UUID)
                        )
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
//                        mBleGatt?.writeDescriptor(descriptor)
                        mBleGattQueue?.push("writeDescriptor", descriptor!!)

                        if (registered) {
                            Log.e("INFO", "notify ok")
                            handler.post(Runnable {
                                Toast.makeText(
                                    this@MainActivity,
                                    "接続",
                                    Toast.LENGTH_SHORT
                                ).show()
                                connected()
                            })
                        } else {
                            Log.e("INFO", "notify ng")
                            DisConnectDevice()
                        }
                    }
                    mBluetoothGattSSIDCharacteristic =
                        service.getCharacteristic(UUID.fromString(SSID_CHARACTERSTIC_UUID))

                    if (mBluetoothGattSSIDCharacteristic != null) {
                        mBleGattQueue?.push(
                            "readCharacteristic",
                            mBluetoothGattSSIDCharacteristic!!
                        )
//                        val readres = mBleGatt?.readCharacteristic(mBluetoothGattSSIDCharacteristic)
//                        Log.e("INFO", readres.toString())

                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            mBleGattQueue?.pop()
            if (characteristic != null &&
                characteristic.uuid == UUID.fromString(SSID_CHARACTERSTIC_UUID)
            ) {
                val ssid_list_str_recv =
                    characteristic.getStringValue(0)

                if (SSID_LIST != null) {
                    //SSIDリスト不整合
                    val ssid_list_str = SSID_LIST?.joinToString(separator = ",")
                    if (ssid_list_str_recv != ssid_list_str) {
                        mBluetoothGattSSIDCharacteristic?.setValue(ssid_list_str)
//                        mBluetoothGattSSIDCharacteristic?.setValue(
//                            -1,
//                            BluetoothGattCharacteristic.FORMAT_SINT8,
//                            0
//                        )
                        if (mBleGattQueue != null) {
                            mBleGattQueue?.push("writeCharacteristic", characteristic)
//                        val res = mBleGatt?.writeCharacteristic(characteristic)
//                        Log.e("INFO", res.toString())
                        }
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            mBleGattQueue?.pop()

        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
            mBleGattQueue?.pop()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            mBleGattQueue?.pop()
            if (descriptor != null && descriptor.uuid == UUID.fromString(ANDROID_CENTRAL_UUID)) {
                Log.e("INFO", "Notification Enabled")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            //回避:android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
            handler.post(Runnable {
                val RecvByteValue = characteristic.value

                ble_status = BLEFrame(RecvByteValue[0].toInt())

                Toast.makeText(this@MainActivity, ble_status!!.toString(), Toast.LENGTH_SHORT)
                    .show();
                val status_txt = findViewById<Button>(R.id.status_txt) as TextView
                if (BLEFrame.ID_DETECTED in ble_status!!) {
                    status_txt.setText(R.string.id_detected_jp)
                } else {
                    status_txt.setText(R.string.id_not_detected_jp)
                }

                val wifi_txt = findViewById<Button>(R.id.wifi_txt) as TextView
                if (BLEFrame.SSID_DETECTED in ble_status!!) {
                    wifi_txt.setText(R.string.wifi_found)
                } else {
                    wifi_txt.setText(R.string.wifi_not_found)
                }
                statusCheck()
            })
        }
    }

    private fun cancelNotify() {
        if (vibrator != null) {
            vibrator!!.cancel()
        }
        if (player != null && player!!.isPlaying) {
            player!!.stop()
            player!!.prepare()
        }
        if (timer != null)
            timer!!.cancel()
        timer_cnt = 0

        if (notify_alert != null) {
            notify_alert!!.dismiss()
        }
    }

    private fun statusCheck() {
        //判定時間確認
        val curdate = Date(System.currentTimeMillis());
        val calender = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"))
        calender.time = curdate
        val day_of_week = calender.get(Calendar.DAY_OF_WEEK)

        val formatter = SimpleDateFormat("H:mm", Locale.JAPAN)
        val curtime = formatter.parse(formatter.format(curdate.time))
        if (curtime != null && incStartTime != null && incEndTime != null) {

            val dayofweek_list =
                listOf<String>("sun", "mon", "tue", "wed", "thu", "fri", "sat")
            val dayofweek = dayofweek_list[day_of_week - 1]
            val inc_chklist_checked = inc_date_chklist!![dayofweek]!!.isChecked
            val exc_chklist_checked = exc_date_chklist!![dayofweek]!!.isChecked

            if (inc_chklist_checked && incStartTime!!.time <= curtime.time && incEndTime!!.time >= curtime.time) {
                if (BLEFrame.ID_DETECTED !in ble_status!! && BLEFrame.SSID_DETECTED !in ble_status!!) {
                    notify_alert = AlertDialog.Builder(this@MainActivity)
                        .setTitle("社員証が確認できません")
                        .setPositiveButton("確認") { dialog, which ->
                            cancelNotify()
                        }.show();

                    if (vibrator != null) {
                        vibrator!!.vibrate(longArrayOf(500, 1000), 0)
                    }

                    if (timer != null) {
                        timer!!.cancel()
                    }
                    timer = Timer()
                    timer!!.schedule(0, 1000) {
                        val audio_attribute = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()

                        if (timer_cnt == 10) {
                            if (player != null) {
                                player!!.reset()
                                player!!.setAudioAttributes(audio_attribute); // アラームのボリュームで再生
                                player!!.setLooping(true);                              // ループ再生を設定
                                val uri =
                                    Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.jishin);
                                player!!.setDataSource(
                                    applicationContext,
                                    uri
                                )                   // 音声を設定
                                player!!.prepare(); // 音声を読み込み
                                player!!.start(); // 再生
                            }
                        } else if (timer_cnt == 20) {
                            if (player != null) {
                                player!!.reset()
                                player!!.setAudioAttributes(audio_attribute); // アラームのボリュームで再生
                                player!!.setLooping(true);                              // ループ再生を設定
                                val uri =
                                    Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.siren);
                                player!!.setDataSource(
                                    applicationContext,
                                    uri
                                )                   // 音声を設定
                                player!!.prepare(); // 音声を読み込み
                                player!!.start(); // 再生
                            }
                        }

                        timer_cnt++
                    }

                } else {
                    //キャンセル
                    cancelNotify()
                }
            } else if (exc_chklist_checked && excStartTime!!.time <= curtime.time && excEndTime!!.time >= curtime.time) {
                if (BLEFrame.ID_DETECTED in ble_status!! && BLEFrame.SSID_DETECTED !in ble_status!!) {
                    notify_alert = AlertDialog.Builder(this@MainActivity)
                        .setTitle("社員証を持ち出していませんか")
                        .setPositiveButton("確認") { dialog, which ->
                            cancelNotify()
                        }.show();

                    if (vibrator != null) {
                        vibrator!!.vibrate(longArrayOf(500, 1000), 0)
                    }
                } else {
                    //キャンセル
                    cancelNotify()
                }
            } else {
                //キャンセル
                cancelNotify()
            }
        }
    }


    private fun connected() {
        val connect_btn = findViewById<Button>(R.id.connect_btn)
        connect_btn.isEnabled = true
        connect_btn.setText(R.string.disconnect)
        val status_txt = findViewById<TextView>(R.id.status_txt)
        status_txt.setText(R.string.id_not_detected_jp)
        val ssid_btn = findViewById<Button>(R.id.ssid_btn)
        ssid_btn.isEnabled = true
        val ssid_spinner = findViewById<Spinner>(R.id.spinner_ssid)
        ssid_spinner.isEnabled = true
    }

    private fun disconnected() {
        val connect_btn = findViewById<Button>(R.id.connect_btn)
        connect_btn.isEnabled = true
        connect_btn.setText(R.string.connect)
        val status_txt = findViewById<TextView>(R.id.status_txt)
        status_txt.setText(R.string.monitoring_stop_jp)
        val ssid_btn = findViewById<Button>(R.id.ssid_btn)
        ssid_btn.isEnabled = false
        val ssid_spinner = findViewById<Spinner>(R.id.spinner_ssid)
        ssid_spinner.isEnabled = false
    }

    fun ByteArray.toHexString(): String {
        return this.joinToString("") {
            java.lang.String.format("%02x", it)
        }
    }
}
