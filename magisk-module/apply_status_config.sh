#!/system/bin/sh
set -e

SCALE="${1:-100}"
WIDTH="${2:-16}"
HEIGHT="${3:-16}"
SPACING="${4:-8}"
MODE="${5:-circle}"

# Calculate icon size: default 17dp * scale / 100
ICON_SIZE=$(( 17 * SCALE / 100 ))

# Calculate battery dimensions according to selected mode
if [ "$MODE" = "portrait" ]; then
    # Pixel Material 3 Portrait: authentic 8.5dp width x 14.5dp height
    WIDTH="8.5"
    HEIGHT="14.5"
else
    # Circle Ring: 15.5dp x 15.5dp
    WIDTH="15.5"
    HEIGHT="15.5"
fi

TMP="/data/local/tmp/battery_build"
mkdir -p "$TMP/systemui/res/values"
mkdir -p "$TMP/out"

cat << 'EOF' > "$TMP/systemui/AndroidManifest.xml"
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.pixel.overlay.battery.systemui"
    android:versionCode="1"
    android:versionName="1.0">
    <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="34"/>
    <overlay android:targetPackage="com.android.systemui"
             android:priority="200"
             android:isStatic="false"/>
    <application android:hasCode="false" android:label="PixelBatterySystemUIOverlay"/>
</manifest>
EOF

cat << EOF > "$TMP/systemui/res/values/dimens.xml"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <dimen name="status_bar_icon_size">${ICON_SIZE}.0dp</dimen>
    <dimen name="status_bar_icon_drawing_size">${ICON_SIZE}.0dp</dimen>
    <dimen name="signal_icon_size">${ICON_SIZE}.0dp</dimen>
    <dimen name="status_bar_wifi_signal_size">${ICON_SIZE}.0dp</dimen>
    <dimen name="status_bar_mobile_signal_size">${ICON_SIZE}.0dp</dimen>
    <dimen name="status_bar_icon_horizontal_margin">${SPACING}.0dp</dimen>
    <dimen name="status_bar_battery_icon_width">${WIDTH}dp</dimen>
    <dimen name="status_bar_battery_icon_height">${HEIGHT}dp</dimen>
    <dimen name="battery_margin_bottom">0.0dp</dimen>
</resources>
EOF

# Always disable any legacy framework pill overlay
cmd overlay disable --user 0 com.pixel.overlay.battery.framework 2>/dev/null || true

if [ "$MODE" = "portrait" ]; then
    content insert --uri content://lineagesettings/system --bind name:s:status_bar_battery_style --bind value:i:0
    content insert --uri content://lineagesettings/system --bind name:s:status_bar_show_battery_percent --bind value:i:2
else
    # Circle Ring with percentage outside
    content insert --uri content://lineagesettings/system --bind name:s:status_bar_battery_style --bind value:i:1
    content insert --uri content://lineagesettings/system --bind name:s:status_bar_show_battery_percent --bind value:i:2
fi

AAPT2="/data/local/tmp/bin/aapt2"
ZIPALIGN="/data/local/tmp/bin/zipalign"
FRAMEWORK="/system/framework/framework-res.apk"
SYSUI="/system/system_ext/priv-app/SystemUI/SystemUI.apk"

"$AAPT2" compile --dir "$TMP/systemui/res" -o "$TMP/systemui_res.zip"
"$AAPT2" link --min-sdk-version 31 --target-sdk-version 34 -o "$TMP/out/bat_sysui_raw.apk" -I "$FRAMEWORK" -I "$SYSUI" --manifest "$TMP/systemui/AndroidManifest.xml" "$TMP/systemui_res.zip"
cp -f "$TMP/out/bat_sysui_aligned.apk" /data/adb/modules/pixel_status_icons/system/product/overlay/PixelBatterySystemUIOverlay.apk 2>/dev/null || true
mount -o bind "$TMP/out/bat_sysui_aligned.apk" /system/product/overlay/PixelBatterySystemUIOverlay.apk 2>/dev/null || true

pkill -f com.android.systemui
