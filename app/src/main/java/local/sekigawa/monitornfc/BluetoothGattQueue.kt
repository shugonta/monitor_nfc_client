package local.sekigawa.monitornfc

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor

public class BluetoothGattQueue(gatt: BluetoothGatt) {
    private val _gatt: BluetoothGatt = gatt
    private var _queue: MutableList<BluetoothGattQueueElem> = mutableListOf()

    public fun push(method: String, target: BluetoothGattDescriptor): Boolean {
        if (method == "writeDescriptor" || method == "ReadDescriptor") {
            if (this._queue.size > 0) {
                this._queue.add(BluetoothGattQueueElem(method, target))
            } else {
                val elem = BluetoothGattQueueElem(method, target)
                this._queue.add(elem)
                this.exec(elem)
            }
            return true
        } else {
            return false
        }
    }

    public fun push(method: String, target: BluetoothGattCharacteristic): Boolean {
        if (method == "writeCharacteristic" || method == "readCharacteristic") {
            if (this._queue.size > 0) {
                this._queue.add(BluetoothGattQueueElem(method, target))
            } else {
                val elem = BluetoothGattQueueElem(method, target)
                this._queue.add(elem)
                this.exec(elem)
            }
            return true
        } else {
            return false
        }
    }

    public fun pop() {
        this._queue.removeAt(0)
        if (this._queue.size > 0) {
            val elem = this._queue[0]
            this.exec(elem)
        }
    }

    public fun exec(elem: BluetoothGattQueueElem) {
        when (elem.method) {
            "writeDescriptor" -> {
                if (elem.targetDescriptor != null)
                    _gatt.writeDescriptor(elem.targetDescriptor)
            }
            "readDescriptor" -> {
                if (elem.targetDescriptor != null)
                    _gatt.readDescriptor(elem.targetDescriptor)
            }
            "writeCharacteristic" -> {
                if (elem.targetCharacteristic != null)
                    _gatt.writeCharacteristic(elem.targetCharacteristic)
            }
            "readCharacteristic" -> {
                if (elem.targetCharacteristic != null)
                    _gatt.readCharacteristic(elem.targetCharacteristic)
            }
        }
    }
}

public class BluetoothGattQueueElem(method: String) {
    public val method = method
    public var targetDescriptor: BluetoothGattDescriptor? = null
    public var targetCharacteristic: BluetoothGattCharacteristic? = null

    constructor(method: String, target: BluetoothGattDescriptor) : this(method) {
        this.targetDescriptor = target
    }

    constructor(method: String, target: BluetoothGattCharacteristic) : this(method) {
        this.targetCharacteristic = target
    }
}