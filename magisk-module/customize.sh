SKIPUNZIP=0

ui_print "- Installing Pixel Status Suite Magisk Module"
ui_print "- Setting permissions..."

set_perm_recursive $MODPATH 0 0 0755 0644
set_perm $MODPATH/service.sh 0 0 0755
set_perm $MODPATH/apply_status_config.sh 0 0 0755

ui_print "- Overlays deployed to system/product/overlay"
ui_print "- Standalone App & LSPosed module deployed to system/priv-app"
ui_print "- Reboot to activate!"
