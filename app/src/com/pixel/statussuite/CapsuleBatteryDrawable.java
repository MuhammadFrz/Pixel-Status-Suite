package com.pixel.statussuite;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

public class CapsuleBatteryDrawable extends Drawable {
    private int mLevel = 67;
    private boolean mCharging = false;
    private int mWidth = 26; // dp
    private int mHeight = 13; // dp
    private float mDensity = 2.0f;

    private final Paint mOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBoltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mCapsuleRect = new RectF();
    private final RectF mCapRect = new RectF();
    private final RectF mFillRect = new RectF();
    private final Path mBoltPath = new Path();

    private int mForegroundColor = 0xFFFFFFFF;
    private int mBackgroundColor = 0x4DFFFFFF;

    public CapsuleBatteryDrawable(Context context) {
        if (context != null && context.getResources() != null && context.getResources().getDisplayMetrics() != null) {
            mDensity = context.getResources().getDisplayMetrics().density;
        }

        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setColor(mForegroundColor);
        mOutlinePaint.setStrokeWidth(1.2f * mDensity);

        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(mForegroundColor);

        mTextPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setColor(0xFF000000);

        mBoltPaint.setStyle(Paint.Style.FILL);
        mBoltPaint.setColor(0xFF000000);

        setBounds(0, 0, getIntrinsicWidth(), getIntrinsicHeight());
    }

    public void setBatteryLevel(int level) {
        mLevel = Math.max(0, Math.min(100, level));
        invalidateSelf();
    }

    public void setCharging(boolean charging) {
        mCharging = charging;
        invalidateSelf();
    }

    public void setColors(int fg, int bg) {
        mForegroundColor = fg;
        mBackgroundColor = bg;
        mOutlinePaint.setColor(fg);
        mFillPaint.setColor(fg);
        invalidateSelf();
    }

    public void setDimensions(int wDp, int hDp) {
        mWidth = wDp;
        mHeight = hDp;
        setBounds(0, 0, (int) (wDp * mDensity), (int) (hDp * mDensity));
        invalidateSelf();
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) (mWidth * mDensity);
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) (mHeight * mDensity);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) {
            b = new Rect(0, 0, getIntrinsicWidth(), getIntrinsicHeight());
        }

        float stroke = 1.2f * mDensity;
        float halfStroke = stroke / 2.0f;
        float capWidth = 1.8f * mDensity;
        float capHeight = b.height() * 0.40f;
        float radius = b.height() * 0.32f;

        // Capsule body bounds
        float bodyRight = b.right - capWidth - halfStroke;
        float bodyLeft = b.left + halfStroke;
        float bodyTop = b.top + halfStroke;
        float bodyBottom = b.bottom - halfStroke;

        mCapsuleRect.set(bodyLeft, bodyTop, bodyRight, bodyBottom);

        // Terminal cap on right
        float capTop = (b.top + b.bottom - capHeight) / 2.0f;
        float capBottom = capTop + capHeight;
        mCapRect.set(bodyRight, capTop, b.right - halfStroke, capBottom);

        // 1. Draw outline capsule
        mOutlinePaint.setColor(mForegroundColor);
        canvas.drawRoundRect(mCapsuleRect, radius, radius, mOutlinePaint);

        // Draw terminal cap
        Paint capPaint = new Paint(mOutlinePaint);
        capPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(mCapRect, capWidth / 2.0f, capWidth / 2.0f, capPaint);

        // 2. Draw Horizontal Fill
        float innerPadding = stroke + 0.8f * mDensity;
        float fillLeft = bodyLeft + innerPadding;
        float fillTop = bodyTop + innerPadding;
        float fillMaxRight = bodyRight - innerPadding;
        float fillBottom = bodyBottom - innerPadding;
        float fillRadius = Math.max(1.0f, radius - innerPadding);

        float fillWidth = (fillMaxRight - fillLeft) * (mLevel / 100.0f);
        mFillRect.set(fillLeft, fillTop, fillLeft + fillWidth, fillBottom);

        // Background fill tint (subtle 20% opacity)
        Paint bgFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgFill.setColor(mForegroundColor);
        bgFill.setAlpha(40);
        RectF totalFillRect = new RectF(fillLeft, fillTop, fillMaxRight, fillBottom);
        canvas.drawRoundRect(totalFillRect, fillRadius, fillRadius, bgFill);

        // Level fill
        if (mLevel > 0) {
            canvas.save();
            canvas.clipRect(fillLeft, fillTop, fillLeft + fillWidth, fillBottom);
            canvas.drawRoundRect(totalFillRect, fillRadius, fillRadius, mFillPaint);
            canvas.restore();
        }

        // Contrast calculation based on theme
        int r = (mForegroundColor >> 16) & 0xFF;
        int g = (mForegroundColor >> 8) & 0xFF;
        int bCol = mForegroundColor & 0xFF;
        boolean isFgDark = (r * 299 + g * 587 + bCol * 114) / 1000 < 128;
        int contrastText = isFgDark ? 0xFFFFFFFF : 0xFF000000;
        int emptyText = mForegroundColor;

        // 3. Percentage Text or Charging Bolt
        if (mCharging) {
            drawChargingBolt(canvas, mCapsuleRect.centerX(), mCapsuleRect.centerY(), b.height() * 0.65f, (mLevel >= 50) ? contrastText : emptyText);
        } else {
            float textSize = b.height() * 0.62f;
            mTextPaint.setTextSize(textSize);

            int textColor = (mLevel >= 50) ? contrastText : emptyText;
            mTextPaint.setColor(textColor);

            Paint.FontMetrics fm = mTextPaint.getFontMetrics();
            float textY = mCapsuleRect.centerY() - (fm.ascent + fm.descent) / 2.0f;
            canvas.drawText(String.valueOf(mLevel), mCapsuleRect.centerX(), textY, mTextPaint);
        }
    }

    private void drawChargingBolt(Canvas canvas, float cx, float cy, float h, int color) {
        mBoltPath.reset();
        float w = h * 0.55f;
        mBoltPath.moveTo(cx + w * 0.1f, cy - h * 0.5f);
        mBoltPath.lineTo(cx - w * 0.45f, cy + h * 0.05f);
        mBoltPath.lineTo(cx - w * 0.05f, cy + h * 0.05f);
        mBoltPath.lineTo(cx - w * 0.2f, cy + h * 0.5f);
        mBoltPath.lineTo(cx + w * 0.45f, cy - h * 0.05f);
        mBoltPath.lineTo(cx + w * 0.05f, cy - h * 0.05f);
        mBoltPath.close();

        mBoltPaint.setColor(color);
        canvas.drawPath(mBoltPath, mBoltPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mOutlinePaint.setAlpha(alpha);
        mFillPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mOutlinePaint.setColorFilter(cf);
        mFillPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
