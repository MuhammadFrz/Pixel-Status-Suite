package com.pixel.statussuite;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.WeakHashMap;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class StatusHook implements IXposedHookLoadPackage {

    private static final WeakHashMap<Object, CapsuleBatteryDrawable> sCustomBatteries = new WeakHashMap<>();

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) return;

        Class<?> batteryMeterViewClass = XposedHelpers.findClass(
            "com.android.systemui.battery.BatteryMeterView", lpparam.classLoader
        );

        // 1. Hook onBatteryLevelChanged to update custom capsule drawable
        XposedHelpers.findAndHookMethod(
            batteryMeterViewClass,
            "onBatteryLevelChanged",
            int.class, boolean.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object view = param.thisObject;
                    int level = (int) param.args[0];
                    boolean plugged = (boolean) param.args[1];

                    CapsuleBatteryDrawable custom = sCustomBatteries.get(view);
                    if (custom != null) {
                        custom.setBatteryLevel(level);
                        custom.setCharging(plugged);
                    }
                }
            }
        );

        // 2. Hook scaleBatteryMeterViews
        XposedHelpers.findAndHookMethod(
            batteryMeterViewClass,
            "scaleBatteryMeterViews",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    Context context = view.getContext();
                    ImageView iconView = (ImageView) XposedHelpers.getObjectField(view, "mBatteryIconView");
                    TextView percentView = (TextView) XposedHelpers.getObjectField(view, "mBatteryPercentView");

                    // Hide separate percent view if drawing inside
                    if (percentView != null) {
                        percentView.setVisibility(View.GONE);
                    }

                    CapsuleBatteryDrawable custom = sCustomBatteries.get(view);
                    if (custom == null) {
                        custom = new CapsuleBatteryDrawable(context);
                        sCustomBatteries.put(view, custom);
                    }

                    try {
                        int level = XposedHelpers.getIntField(view, "mLevel");
                        boolean plugged = XposedHelpers.getBooleanField(view, "mPluggedIn");
                        custom.setBatteryLevel(level);
                        custom.setCharging(plugged);
                    } catch (Throwable t) {}

                    if (iconView != null) {
                        iconView.setImageDrawable(custom);
                        ViewGroup.LayoutParams lp = iconView.getLayoutParams();
                        if (lp != null) {
                            lp.width = custom.getIntrinsicWidth();
                            lp.height = custom.getIntrinsicHeight();
                            iconView.setLayoutParams(lp);
                        }
                    }
                }
            }
        );

        // 3. Hook updateColors to keep battery colors matched with light/dark theme
        XposedHelpers.findAndHookMethod(
            batteryMeterViewClass,
            "updateColors",
            int.class, int.class, int.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object view = param.thisObject;
                    int fg = (int) param.args[0];
                    int bg = (int) param.args[1];
                    CapsuleBatteryDrawable custom = sCustomBatteries.get(view);
                    if (custom != null) {
                        custom.setColors(fg, bg);
                    }
                }
            }
        );

        // 4. Hook updatePercentText to prevent separate % text from appearing
        try {
            XposedHelpers.findAndHookMethod(
                batteryMeterViewClass,
                "updatePercentText",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        TextView percentView = (TextView) XposedHelpers.getObjectField(param.thisObject, "mBatteryPercentView");
                        if (percentView != null) {
                            percentView.setVisibility(View.GONE);
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}
    }
}
