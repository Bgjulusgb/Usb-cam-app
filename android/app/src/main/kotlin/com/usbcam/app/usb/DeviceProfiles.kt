package com.usbcam.app.usb

enum class DeviceType {
    UVC_STANDARD,
    EASYCAP_UTVF007,
    EASYCAP_STK1160,
    EASYCAP_EM2860,
    EASYCAP_SMI2021,
    SUPERCAMERA_GEEK,
    NON_UVC_ENDOSCOPE,
    CAPTURE_CARD,
    UNKNOWN
}

data class DeviceProfile(
    val vendorId: Int,
    val productId: Int,
    val deviceType: DeviceType,
    val name: String,
    val maxResolutionWidth: Int = 1920,
    val maxResolutionHeight: Int = 1080,
    val maxFps: Int = 60,
    val supportsAudio: Boolean = false,
    val audioSampleRates: List<Int> = emptyList(),
    val supportedFormats: List<String> = listOf("MJPG", "YUY2"),
    val isUvc: Boolean = true,
    val notes: String = ""
)

object DeviceDatabase {
    val knownDevices = listOf(
        // EasyCap UTVF007 / HTV600 / HTV800
        DeviceProfile(
            vendorId = 0x1B71, productId = 0x3002,
            deviceType = DeviceType.EASYCAP_UTVF007,
            name = "EasyCap UTVF007/HTV600/HTV800",
            maxResolutionWidth = 720, maxResolutionHeight = 576,
            maxFps = 30, supportsAudio = false,
            supportedFormats = listOf("YUY2", "MJPG"),
            isUvc = false,
            notes = "VGA analog capture, PAL/NTSC"
        ),
        // EasyCap STK1160 + SAA7113/GM7113
        DeviceProfile(
            vendorId = 0x05E1, productId = 0x0408,
            deviceType = DeviceType.EASYCAP_STK1160,
            name = "EasyCap STK1160 + SAA7113",
            maxResolutionWidth = 720, maxResolutionHeight = 576,
            maxFps = 30, supportsAudio = true,
            audioSampleRates = listOf(48000, 8000),
            supportedFormats = listOf("YUY2"),
            isUvc = false,
            notes = "Composite/S-Video, 48kHz & 8kHz audio"
        ),
        // EasyCap EM2860 + SAA7113/GM7113
        DeviceProfile(
            vendorId = 0xEB1A, productId = 0x2861,
            deviceType = DeviceType.EASYCAP_EM2860,
            name = "EasyCap EM2860 + SAA7113",
            maxResolutionWidth = 720, maxResolutionHeight = 576,
            maxFps = 30, supportsAudio = true,
            audioSampleRates = listOf(48000),
            supportedFormats = listOf("YUY2", "MJPG"),
            isUvc = false,
            notes = "EM2860 bridge, composite input"
        ),
        // EasyCap SMI2021 variants
        DeviceProfile(
            vendorId = 0x1C88, productId = 0x0007,
            deviceType = DeviceType.EASYCAP_SMI2021,
            name = "EasyCap SMI2021 (PID 0007)",
            maxResolutionWidth = 720, maxResolutionHeight = 576,
            maxFps = 30, supportsAudio = true,
            audioSampleRates = listOf(48000),
            supportedFormats = listOf("YUY2"),
            isUvc = false
        ),
        DeviceProfile(
            vendorId = 0x1C88, productId = 0x003C,
            deviceType = DeviceType.EASYCAP_SMI2021,
            name = "EasyCap SMI2021 (PID 003C)",
            maxResolutionWidth = 720, maxResolutionHeight = 576,
            maxFps = 30, supportsAudio = true,
            audioSampleRates = listOf(48000),
            supportedFormats = listOf("YUY2"),
            isUvc = false
        ),
        DeviceProfile(
            vendorId = 0x1C88, productId = 0x003D,
            deviceType = DeviceType.EASYCAP_SMI2021,
            name = "EasyCap SMI2021 (PID 003D)",
            maxResolutionWidth = 720, maxResolutionHeight = 576,
            maxFps = 30, supportsAudio = true,
            audioSampleRates = listOf(48000),
            supportedFormats = listOf("YUY2"),
            isUvc = false
        ),
        DeviceProfile(
            vendorId = 0x1C88, productId = 0x003E,
            deviceType = DeviceType.EASYCAP_SMI2021,
            name = "EasyCap SMI2021 (PID 003E)",
            maxResolutionWidth = 720, maxResolutionHeight = 576,
            maxFps = 30, supportsAudio = true,
            audioSampleRates = listOf(48000),
            supportedFormats = listOf("YUY2"),
            isUvc = false
        ),
        DeviceProfile(
            vendorId = 0x1C88, productId = 0x003F,
            deviceType = DeviceType.EASYCAP_SMI2021,
            name = "EasyCap SMI2021 (PID 003F)",
            maxResolutionWidth = 720, maxResolutionHeight = 576,
            maxFps = 30, supportsAudio = true,
            audioSampleRates = listOf(48000),
            supportedFormats = listOf("YUY2"),
            isUvc = false
        ),
        DeviceProfile(
            vendorId = 0x1C88, productId = 0x1001,
            deviceType = DeviceType.EASYCAP_SMI2021,
            name = "EasyCap SMI2021 (PID 1001)",
            maxResolutionWidth = 720, maxResolutionHeight = 576,
            maxFps = 30, supportsAudio = true,
            audioSampleRates = listOf(48000),
            supportedFormats = listOf("YUY2"),
            isUvc = false
        ),
        // Supercamera Geek szitman
        DeviceProfile(
            vendorId = 0x2CE3, productId = 0x3828,
            deviceType = DeviceType.SUPERCAMERA_GEEK,
            name = "Supercamera Geek szitman",
            maxResolutionWidth = 1920, maxResolutionHeight = 1080,
            maxFps = 60, supportsAudio = false,
            supportedFormats = listOf("MJPG", "H264"),
            isUvc = true,
            notes = "VID 2ce3:3828"
        )
    )

    fun findDevice(vendorId: Int, productId: Int): DeviceProfile? {
        return knownDevices.firstOrNull { it.vendorId == vendorId && it.productId == productId }
    }

    fun isKnownDevice(vendorId: Int, productId: Int): Boolean {
        return findDevice(vendorId, productId) != null
    }

    fun isUvcClass(usbClass: Int, usbSubclass: Int): Boolean {
        return usbClass == 0x0E
    }

    fun createGenericUvcProfile(vendorId: Int, productId: Int, name: String): DeviceProfile {
        return DeviceProfile(
            vendorId = vendorId,
            productId = productId,
            deviceType = DeviceType.UVC_STANDARD,
            name = name,
            maxResolutionWidth = 1920,
            maxResolutionHeight = 1080,
            maxFps = 60,
            supportedFormats = listOf("MJPG", "YUY2", "H264", "H265", "NV12"),
            isUvc = true
        )
    }
}
