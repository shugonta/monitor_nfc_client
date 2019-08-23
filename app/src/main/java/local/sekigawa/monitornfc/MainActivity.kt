package local.sekigawa.monitornfc

import android.Manifest;
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Build;
import android.widget.Button
import android.widget.Toast;
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
import android.bluetooth.le.ScanResult;
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import java.util.*


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

    //BLE
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBleGatt: BluetoothGatt? = null
    private var mBluetoothGattCharacteristic: BluetoothGattCharacteristic? = null

    //Androidの固定値
    private val ANDROID_CENTRAL_UUID = "00002902-0000-1000-8000-00805f9b34fb"

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
    }

    /**
     * BLEの初期設定をおこなうところ
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

        //permisstion
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
     * Permisstionの許可をする関数
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
                            this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
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
        scanner?.startScan(mScanCallback)
    }

    /**
     * DisConncetDevice
     */
    private fun DisConnectDevice() {
        mBleGatt?.close()
        mBleGatt = null
        Toast.makeText(this, "切断", Toast.LENGTH_SHORT).show()
        disconnected()
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

                    //Notifyの接続を試みてる
                    mBluetoothGattCharacteristic =
                        service.getCharacteristic(UUID.fromString(CUSTOM_CHARACTERSTIC_UUID))
                    if (mBluetoothGattCharacteristic != null) {
                        val registered =
                            gatt.setCharacteristicNotification(mBluetoothGattCharacteristic, true)
                        val descriptor = mBluetoothGattCharacteristic?.getDescriptor(
                            UUID.fromString(ANDROID_CENTRAL_UUID)
                        )
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        mBleGatt?.writeDescriptor(descriptor)

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
                }
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
                val RecvByteValueStr = RecvByteValue.toHexString()

                Toast.makeText(this@MainActivity, RecvByteValueStr, Toast.LENGTH_SHORT)
                    .show();
            })
        }
    }

    private fun connected() {
        val connect_btn = findViewById<Button>(R.id.connect_btn) as Button
        connect_btn.isEnabled = true
        connect_btn.setText(R.string.disconnect)
    }

    private fun disconnected() {
        val connect_btn = findViewById<Button>(R.id.connect_btn) as Button
        connect_btn.isEnabled = true
        connect_btn.setText(R.string.connect)
    }

    fun ByteArray.toHexString(): String {
        return this.joinToString("") {
            java.lang.String.format("%02x", it)
        }
    }
}
