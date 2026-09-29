#!/system/bin/sh
MODDIR=${0%/*}

# Wait until system is booted
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

# Ensure overlays are enabled & prioritized
cmd overlay enable --user current com.pixel.overlay.wifi 2>/dev/null
cmd overlay enable --user current com.pixel.overlay.signal 2>/dev/null
cmd overlay enable --user current com.pixel.overlay.systemui 2>/dev/null
cmd overlay enable --user current com.pixel.overlay.battery.systemui 2>/dev/null

# Enforce Circle Ring battery meter style and percent outside
content insert --uri content://lineagesettings/system --bind name:s:status_bar_battery_style --bind value:i:1 2>/dev/null
content insert --uri content://lineagesettings/system --bind name:s:status_bar_show_battery_percent --bind value:i:2 2>/dev/null
