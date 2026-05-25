package com.usbcam.app.usb

enum class DeviceType {
    UVC_STANDARD, EASYCAP_UTVF007, EASYCAP_STK1160, EASYCAP_EM2860,
    EASYCAP_SMI2021, SUPERCAMERA_GEEK, NON_UVC_ENDOSCOPE, CAPTURE_CARD, UNKNOWN
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
    val profiles = listOf(
        DeviceProfile(0x1B71, 0x3002, DeviceType.EASYCAP_UTVF007, "EasyCap UTVF007/HTV600/HTV800",
            720, 576, 30, false, emptyList(), listOf("YUY2","MJPG"), false, "VID_1B71:PID_3002"),
        DeviceProfile(0x05E1, 0x0408, DeviceType.EASYCAP_STK1160, "EasyCap STK1160 + SAA7113",
            720, 576, 30, true, listOf(48000,8000), listOf("YUY2"), false, "VID_05E1:PID_0408"),
        DeviceProfile(0xEB1A, 0x2861, DeviceType.EASYCAP_EM2860, "EasyCap EM2860 + SAA7113",
            720, 576, 30, true, listOf(48000), listOf("YUY2","MJPG"), false, "VID_EB1A:PID_2861"),
        DeviceProfile(0x1C88, 0x0007, DeviceType.EASYCAP_SMI2021, "EasyCap SMI2021 (PID 0007)",
            720, 576, 30, true, listOf(48000), listOf("YUY2"), false, "VID_1C88:PID_0007"),
        DeviceProfile(0x1C88, 0x003C, DeviceType.EASYCAP_SMI2021, "EasyCap SMI2021 (PID 003C)",
            720, 576, 30, true, listOf(48000), listOf("YUY2"), false),
        DeviceProfile(0x1C88, 0x003D, DeviceType.EASYCAP_SMI2021, "EasyCap SMI2021 (PID 003D)",
            720, 576, 30, true, listOf(48000), listOf("YUY2"), false),
        DeviceProfile(0x1C88, 0x003E, DeviceType.EASYCAP_SMI2021, "EasyCap SMI2021 (PID 003E)",
            720, 576, 30, true, listOf(48000), listOf("YUY2"), false),
        DeviceProfile(0x1C88, 0x003F, DeviceType.EASYCAP_SMI2021, "EasyCap SMI2021 (PID 003F)",
            720, 576, 30, true, listOf(48000), listOf("YUY2"), false),
        DeviceProfile(0x1C88, 0x1001, DeviceType.EASYCAP_SMI2021, "EasyCap SMI2021 (PID 1001)",
            720, 576, 30, true, listOf(48000), listOf("YUY2"), false),
        DeviceProfile(0x2CE3, 0x3828, DeviceType.SUPERCAMERA_GEEK, "Supercamera Geek szitman",
            1920, 1080, 60, false, emptyList(), listOf("MJPG","H264"), true, "VID 2ce3:3828")
    )
    fun findByVidPid(vid: Int, pid: Int) = profiles.firstOrNull { it.vendorId == vid && it.productId == pid }
    fun isUvcDevice(usbClass: Int) = usbClass == 0x0E
}
