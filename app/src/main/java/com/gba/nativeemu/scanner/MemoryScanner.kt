package com.gba.nativeemu.scanner

import com.gba.nativeemu.core.GbaBridge

data class ScanResult(
    val address: Int,
    var value: Int,
    val prevValue: Int,
    val formattedAddr: String = "0x" + (address.toLong() and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')
)

class MemoryScanner {

    var results: List<ScanResult> = emptyList()
        private set

    var currentValueType: Int = GbaBridge.SCAN_TYPE_U16
    var currentCompareType: Int = GbaBridge.SCAN_COMPARE_EXACT

    fun searchFirst(targetVal: Int, valType: Int, compType: Int): List<ScanResult> {
        currentValueType = valType
        currentCompareType = compType

        if (!GbaBridge.isLibraryLoaded || !GbaBridge.nativeIsRomLoaded()) {
            results = emptyList()
            return results
        }

        val addrs = GbaBridge.nativeScanMemory(targetVal, valType, compType, null)
        val list = ArrayList<ScanResult>(addrs.size)

        for (addr in addrs) {
            val cur = GbaBridge.nativeReadMemory(addr, valType)
            list.add(ScanResult(address = addr, value = cur, prevValue = cur))
        }

        results = list
        return results
    }

    fun searchNext(targetVal: Int, valType: Int, compType: Int): List<ScanResult> {
        currentValueType = valType
        currentCompareType = compType

        if (results.isEmpty() || !GbaBridge.isLibraryLoaded || !GbaBridge.nativeIsRomLoaded()) {
            return results
        }

        val prevAddrs = results.map { it.address }.toIntArray()
        val prevMap = results.associate { it.address to it.value }

        val addrs = GbaBridge.nativeScanMemory(targetVal, valType, compType, prevAddrs)
        val list = ArrayList<ScanResult>(addrs.size)

        for (addr in addrs) {
            val cur = GbaBridge.nativeReadMemory(addr, valType)
            val prev = prevMap[addr] ?: cur
            list.add(ScanResult(address = addr, value = cur, prevValue = prev))
        }

        results = list
        return results
    }

    fun editValue(address: Int, newVal: Int, valType: Int): Boolean {
        if (!GbaBridge.isLibraryLoaded || !GbaBridge.nativeIsRomLoaded()) return false
        val ok = GbaBridge.nativeWriteMemory(address, newVal, valType)
        if (ok) {
            results = results.map {
                if (it.address == address) it.copy(value = newVal) else it
            }
        }
        return ok
    }

    fun editAll(newVal: Int, valType: Int): Int {
        if (!GbaBridge.isLibraryLoaded || !GbaBridge.nativeIsRomLoaded()) return 0
        var count = 0
        for (item in results) {
            val ok = GbaBridge.nativeWriteMemory(item.address, newVal, valType)
            if (ok) {
                item.value = newVal
                count++
            }
        }
        return count
    }

    fun generateCheatCode(address: Int, value: Int, valType: Int): String? {
        val uAddr = address.toLong() and 0xFFFFFFFFL
        val isEwram = uAddr in 0x02000000L until 0x02040000L
        val isIwram = uAddr in 0x03000000L until 0x03008000L
        if (!isEwram && !isIwram) return null

        val prefix16 = if (isEwram) "82" else "83"
        val prefix8 = if (isEwram) "32" else "33"
        val offsetHex = (uAddr and 0x00FFFFFFL).toString(16).uppercase().padStart(6, '0')

        return when (valType) {
            GbaBridge.SCAN_TYPE_U8 -> {
                val hexVal = (value and 0xFF).toString(16).uppercase().padStart(2, '0')
                "$prefix8$offsetHex 00$hexVal"
            }
            GbaBridge.SCAN_TYPE_U32 -> {
                val offsetHex2 = ((uAddr + 2) and 0x00FFFFFFL).toString(16).uppercase().padStart(6, '0')
                val low16 = (value and 0xFFFF).toString(16).uppercase().padStart(4, '0')
                val high16 = ((value ushr 16) and 0xFFFF).toString(16).uppercase().padStart(4, '0')
                "$prefix16$offsetHex $low16\n$prefix16$offsetHex2 $high16"
            }
            else -> {
                val hexVal = (value and 0xFFFF).toString(16).uppercase().padStart(4, '0')
                "$prefix16$offsetHex $hexVal"
            }
        }
    }

    fun reset() {
        results = emptyList()
    }
}
