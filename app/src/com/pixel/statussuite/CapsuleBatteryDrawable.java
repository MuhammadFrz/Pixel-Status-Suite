package com.pixel.statussuite;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

public class CapsuleBatteryDrawable extends Drawable {
    private int mLevel = 94;
    private boolean mCharging = true;
    private float mDensity = 2.0f;

    // Dimensions in dp
    private static final float HEIGHT_DP = 13.0f;
    private static final float PILL_PAD_H_DP = 5.0f;
    private static final float BOLT_GAP_DP = 2.5f;
    private static final float BOLT_WIDTH_DP = 6.0f;
    private static final float BOLT_HEIGHT_DP = 11.0f;

    private final Paint mPillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBoltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mPillRect = new RectF();
    private final Path mBoltPath = new Path();

    // Theme colors
    private int mForegroundColor = 0xFFFFFFFF;
    private int mBackgroundColor = 0x4DFFFFFF;

    // Google Android 17 / Pixel Green (#3DDC84)
    public static final int GOOGLE_GREEN = 0xFF3DDC84;
    public static final int GOOGLE_RED = 0xFFE53935;

    public CapsuleBatteryDrawable(Context context) {
        if (context != null && context.getResources() != null && context.getResources().getDisplayMetrics() != null) {
            mDensity = context.getResources().getDisplayMetrics().density;
        }

        mPillPaint.setStyle(Paint.Style.FILL);

        mTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        mBoltPaint.setStyle(Paint.Style.FILL);

        updateBounds();
    }

    public void setBatteryLevel(int level) {
        mLevel = Math.max(0, Math.min(100, level));
        updateBounds();
        invalidateSelf();
    }

    public void setCharging(boolean charging) {
        mCharging = charging;
        updateBounds();
        invalidateSelf();
    }

    public void setColors(int fg, int bg) {
        mForegroundColor = fg;
        mBackgroundColor = bg;
        invalidateSelf();
    }

    private float getPillWidthDp() {
        if (mLevel >= 100) {
            return 28.0f;
        } else if (mLevel >= 10) {
            return 24.5f;
        } else {
            return 19.0f;
        }
    }

    private void updateBounds() {
        setBounds(0, 0, getIntrinsicWidth(), getIntrinsicHeight());
    }

    @Override
    public int getIntrinsicWidth() {
        float totalDp = getPillWidthDp();
        if (mCharging) {
            totalDp += BOLT_GAP_DP + BOLT_WIDTH_DP;
        }
        return (int) Math.ceil(totalDp * mDensity);
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) Math.ceil(HEIGHT_DP * mDensity);
    }

    @Override
    public void draw(Canvas canvas) {
        float h = HEIGHT_DP * mDensity;
        float pillW = getPillWidthDp() * mDensity;
        float radius = h / 2.0f; // Perfect capsule roundness

        // 1. Determine Pill Color and Text Color
        int pillColor;
        int textColor;
        int boltColor;

        int r = (mForegroundColor >> 16) & 0xFF;
        int g = (mForegroundColor >> 8) & 0xFF;
        int bCol = mForegroundColor & 0xFF;
        boolean isDarkTheme = ((r * 299 + g * 587 + bCol * 114) / 1000) > 128; // Light text = Dark theme

        if (mCharging) {
            // Authentic Pixel Android 17: Vibrant Google Android Green
            pillColor = GOOGLE_GREEN;
            textColor = 0xFF000000; // Bold black text inside green pill
            boltColor = isDarkTheme ? 0xFFFFFFFF : 0xFF000000; // High contrast bolt next to pill
        } else if (mLevel <= 15) {
            pillColor = GOOGLE_RED;
            textColor = 0xFFFFFFFF;
            boltColor = mForegroundColor;
        } else {
            // Neutral pill matching current status bar theme
            pillColor = mForegroundColor;
            textColor = isDarkTheme ? 0xFF000000 : 0xFFFFFFFF;
            boltColor = mForegroundColor;
        }

        // 2. Draw Pill Container
        mPillRect.set(0, 0, pillW, h);
        mPillPaint.setColor(pillColor);
        canvas.drawRoundRect(mPillRect, radius, radius, mPillPaint);

        // 3. Draw Percentage Text centered inside the pill
        float textSize = h * 0.72f;
        mTextPaint.setTextSize(textSize);
        mTextPaint.setColor(textColor);

        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        float textY = (h / 2.0f) - (fm.ascent + fm.descent) / 2.0f;
        canvas.drawText(String.valueOf(mLevel), pillW / 2.0f, textY, mTextPaint);

        // 4. If Charging: Draw Lightning Bolt attached to the right of the pill
        if (mCharging) {
            float boltLeft = pillW + (BOLT_GAP_DP * mDensity);
            float boltTop = (h - (BOLT_HEIGHT_DP * mDensity)) / 2.0f;
            float boltW = BOLT_WIDTH_DP * mDensity;
            float boltH = BOLT_HEIGHT_DP * mDensity;

            drawGoogleBolt(canvas, boltLeft, boltTop, boltW, boltH, boltColor);
        }
    }

    /**
     * Authentic Google Pixel lightning bolt from ic_battery_charging.xml
     * Path: M11,20l4,-7.5h-2L13,7l-4,7.5h2z in 24x24 box
     * Normalized: (x-7)/8, (y-7)/13
     */
    private void drawGoogleBolt(Canvas canvas, float left, float top, float width, float height, int color) {
        mBoltPath.reset();
        // Exact proportion of Google's M11,20 l4,-7.5 h-2 L13,7 l-4,7.5 h2 z
        // Points: (11,20) -> (15, 12.5) -> (13, 12.5) -> (13, 7) -> (9, 14.5) -> (11, 14.5) -> close
        // In normalized [0, 1] relative to bounding box (width, height):
        // (11-9)/6 = 0.33, (20-7)/13 = 1.0
        // (15-9)/6 = 1.00, (12.5-7)/13 = 0.423
        // (13-9)/6 = 0.67, (12.5-7)/13 = 0.423
        // (13-9)/6 = 0.67, (7-7)/13 = 0.0
        // (9-9)/6  = 0.00, (14.5-7)/13 = 0.577
        // (11-9)/6 = 0.33, (14.5-7)/13 = 0.577

        mBoltPath.moveTo(left + width * 0.33f, top + height * 1.00f);
        mBoltPath.lineTo(left + width * 1.00f, top + height * 0.42f);
        mBoltPath.lineTo(left + width * 0.66f, top + height * 0.42f);
        mBoltPath.lineTo(left + width * 0.66f, top + height * 0.00f);
        mBoltPath.lineTo(left + width * 0.00f, top + height * 0.58f);
        mBoltPath.lineTo(left + width * 0.33f, top + height * 0.58f);
        mBoltPath.close();

        mBoltPaint.setColor(color);
        canvas.drawPath(mBoltPath, mBoltPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mPillPaint.setAlpha(alpha);
        mTextPaint.setAlpha(alpha);
        mBoltPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPillPaint.setColorFilter(cf);
        mTextPaint.setColorFilter(cf);
        mBoltPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
