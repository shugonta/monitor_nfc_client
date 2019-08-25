package local.sekigawa.monitornfc

class BLEFrame constructor(
    private val bits: Int
) {
    companion object {
        val EMPTY = BLEFrame(0b0000)
        val MONITORING = BLEFrame(0b0001)
        val CARD_DETECTED = BLEFrame(0b0010)
        val ID_DETECTED = BLEFrame(0b0100)
        val SSID_DETECTED = BLEFrame(0b1000)
    }

    operator fun plus(bLEFrame: BLEFrame): BLEFrame =
        BLEFrame(this.bits or bLEFrame.bits)

    operator fun minus(bLEFrame: BLEFrame): BLEFrame =
        BLEFrame(this.bits and bLEFrame.bits.inv())

    operator fun times(bLEFrame: BLEFrame): BLEFrame =
        BLEFrame(this.bits and bLEFrame.bits)

    operator fun contains(bLEFrame: BLEFrame): Boolean =
        this * bLEFrame == bLEFrame

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is BLEFrame -> false
        else -> this.bits == other.bits
    }

    override fun hashCode() = bits.hashCode()

    override fun toString() =
        "${BLEFrame::class.java.simpleName}(${::bits.name}=0b${bits.toString(2)})"

}