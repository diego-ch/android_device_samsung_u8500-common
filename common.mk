#
# Copyright (C) 2012 The Android Open-Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
COMMON_PATH := device/samsung/u8500-common

DEVICE_PACKAGE_OVERLAYS := $(COMMON_PATH)/overlay

# Use the Dalvik VM specific for devices with 512 MB of RAM
$(call inherit-product, frameworks/native/build/phone-hdpi-512-dalvik-heap.mk)

# Our devices are HDPI
PRODUCT_AAPT_CONFIG := normal hdpi
PRODUCT_AAPT_PREF_CONFIG := hdpi

# Graphics
PRODUCT_PACKAGES += \
	libblt_hw
PRODUCT_PROPERTY_OVERRIDES += \
    ro.opengles.version=131072 \
    persist.sys.strictmode.disable=1 \
    hwui.render_dirty_regions=false \
	ro.zygote.disable_gl_preload=1 \
	ro.bq.gpu_to_cpu_unsupported=1

# Media
PRODUCT_COPY_FILES += \
	$(COMMON_PATH)/configs/omxloaders:system/etc/omxloaders \
	$(COMMON_PATH)/configs/media_codecs.xml:system/etc/media_codecs.xml \
	$(COMMON_PATH)/configs/media_profiles.xml:system/etc/media_profiles.xml
PRODUCT_PACKAGES += \
	libomxil-bellagio

# Wifi
PRODUCT_COPY_FILES += \
	$(COMMON_PATH)/configs/wpa_supplicant.conf:system/etc/wifi/wpa_supplicant.conf \
	$(COMMON_PATH)/configs/wpa_supplicant_overlay.conf:system/etc/wifi/wpa_supplicant_overlay.conf \
	$(COMMON_PATH)/configs/p2p_supplicant_overlay.conf:system/etc/wifi/p2p_supplicant_overlay.conf
PRODUCT_PACKAGES += \
	libnetcmdiface
PRODUCT_PROPERTY_OVERRIDES += \
	wifi.interface=wlan0 \
	wifi.supplicant_scan_interval=150

$(call inherit-product-if-exists, hardware/broadcom/wlan/bcmdhd/firmware/bcm4330/device-bcm.mk)

# Bluetooth
PRODUCT_COPY_FILES += \
	$(COMMON_PATH)/bluetooth/bt_vendor.conf:system/etc/bluetooth/bt_vendor.conf

# STE
PRODUCT_COPY_FILES += \
	$(COMMON_PATH)/configs/cspsa.conf:system/etc/cspsa.conf \
	$(COMMON_PATH)/configs/usbid_init.sh:system/bin/usbid_init.sh

# RIL
PRODUCT_PROPERTY_OVERRIDES += \
	ro.ril.hsxpa=1 \
	ro.ril.gprsclass=10 \
	mobiledata.interfaces=pdp0,wlan0,gprs,ppp0 \
	ro.telephony.ril_class=SamsungU8500RIL \
	ro.telephony.sends_barcount=1

# Audio
PRODUCT_COPY_FILES += \
	$(COMMON_PATH)/configs/asound.conf:system/etc/asound.conf
PRODUCT_PACKAGES += \
	audio.a2dp.default \
	audio.usb.default
	libaudioutils \
	libtinyalsa

$(call inherit-product, device/samsung/u8500-common/opensource/libasound/alsa-lib-products.mk)

# GPS
PRODUCT_COPY_FILES += \
	$(COMMON_PATH)/configs/sirfgps.conf:system/etc/sirfgps.conf

# Montblanc libs
PRODUCT_PACKAGES += \
	power.montblanc \
	lights.montblanc

# USB
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
	persist.sys.usb.config=mtp,adb \
	persist.service.adb.enable=1

# Charger
PRODUCT_COPY_FILES += \
	$(COMMON_PATH)/rootdir/lpm.rc:root/lpm.rc
PRODUCT_PACKAGES += \
	charger \
	charger_res_images

# Misc Packages
PRODUCT_PACKAGES += \
	com.android.future.usb.accessory \
	SamsungServiceMode \
	CMAccount \
	Torch

# Filesystem management tools
PRODUCT_PACKAGES += \
	make_ext4fs \
	setup_fs

# Keylayout
PRODUCT_COPY_FILES += \
	$(COMMON_PATH)/configs/usr/keylayout/gpio-keys.kl:system/usr/keylayout/gpio-keys.kl \
	$(COMMON_PATH)/configs/usr/keylayout/sec_touchkey.kl:system/usr/keylayout/sec_touchkey.kl

# These are the hardware-specific features
PRODUCT_COPY_FILES += \
	frameworks/native/data/etc/android.hardware.bluetooth_le.xml:system/etc/permissions/android.hardware.bluetooth_le.xml \
	frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
	frameworks/native/data/etc/android.hardware.audio.low_latency.xml:system/etc/permissions/android.hardware.audio.low_latency.xml \
	frameworks/native/data/etc/android.hardware.camera.autofocus.xml:system/etc/permissions/android.hardware.camera.autofocus.xml \
	frameworks/native/data/etc/android.hardware.camera.flash-autofocus.xml:system/etc/permissions/android.hardware.camera.flash-autofocus.xml \
	frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
	frameworks/native/data/etc/android.hardware.camera.xml:system/etc/permissions/android.hardware.camera.xml \
	frameworks/native/data/etc/android.hardware.location.gps.xml:system/etc/permissions/android.hardware.location.gps.xml \
	frameworks/native/data/etc/android.hardware.location.xml:system/etc/permissions/android.hardware.location.xml \
	frameworks/native/data/etc/android.hardware.sensor.accelerometer.xml:system/etc/permissions/android.hardware.sensor.accelerometer.xml \
	frameworks/native/data/etc/android.hardware.sensor.compass.xml:system/etc/permissions/android.hardware.sensor.compass.xml \
	frameworks/native/data/etc/android.hardware.sensor.proximity.xml:system/etc/permissions/android.hardware.sensor.proximity.xml \
	frameworks/native/data/etc/android.hardware.telephony.gsm.xml:system/etc/permissions/android.hardware.telephony.gsm.xml \
	frameworks/native/data/etc/android.hardware.touchscreen.multitouch.jazzhand.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.jazzhand.xml \
	frameworks/native/data/etc/android.hardware.touchscreen.multitouch.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.xml \
	frameworks/native/data/etc/android.hardware.touchscreen.xml:system/etc/permissions/android.hardware.touchscreen.xml \
	frameworks/native/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml \
	frameworks/native/data/etc/android.hardware.usb.host.xml:system/etc/permissions/android.hardware.usb.host.xml \
	frameworks/native/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml \
	frameworks/native/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
	frameworks/native/data/etc/android.software.sip.voip.xml:system/etc/permissions/android.software.sip.voip.xml \
	frameworks/native/data/etc/android.software.sip.xml:system/etc/permissions/android.software.sip.xml \
	frameworks/native/data/etc/handheld_core_hardware.xml:system/etc/permissions/handheld_core_hardware.xml \
	packages/wallpapers/LivePicker/android.software.live_wallpaper.xml:system/etc/permissions/android.software.live_wallpaper.xml

# Live Wallpapers
PRODUCT_PACKAGES += \
	Galaxy4 \
	HoloSpiralWallpaper \
	LiveWallpapers \
	LiveWallpapersPicker \
	MagicSmokeWallpapers \
	NoiseField \
	PhaseBeam \
	VisualizationWallpapers \
	librs_jni

# Error Checking
PRODUCT_PROPERTY_OVERRIDES += \
	ro.kernel.android.checkjni=0 \
	dalvik.vm.checkjni=false

# Disable SELinux
PRODUCT_PROPERTY_OVERRIDES += \
	ro.boot.selinux=disabled

# Precise GC data
PRODUCT_TAGS += dalvik.gc.type-precise

# Use the non-open-source parts, if they're present
include vendor/samsung/u8500-common/vendor-common.mk
