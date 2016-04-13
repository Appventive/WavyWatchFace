/**
 * Wavy Watch Face shows a playful depiction of battery utilization as the water level in a wavy pool.
 *
 * Copyright (C) 2016 Steve Novack [Wavy Watch Face]
 *
 * Copyright (C) 2014 The Android Open Source Project [original Digital Watch Face sample app]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appventive.wearexperiment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final String TAG = MyWatchFace.class.getSimpleName();

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements Invalidate {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mHoursPaint;
        Paint mMinutesPaint;
        Paint mDatePaint;

        boolean mAmbient;
        GregorianCalendar mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime = new GregorianCalendar();
            }
        };

        int mTapCount;

        float mXOffset;
        float mYOffset;
        float mDateOffset;
        private float mMinutesOffset;


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        private WaveRenderer mWaveRenderer;
        private WaveAnimator mWaveAnimator;

        private float mWaterLevel;
        private float mBatteryPct;

        private String mHours = "";
        private String mMinutes = "";
        private float mHoursWidth = 0f;
        private String mDayName = "";
        private String mDate = "";

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            int backgroundColor = Color.BLACK;

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(backgroundColor);

            mHoursPaint = createTextPaint(Color.WHITE, true);
            mMinutesPaint = createTextPaint(Color.WHITE, false);

            int behindWaveColor = Color.parseColor("#0288D1");
            int frontWaveColor = Color.parseColor("#03A9F4");

            mDatePaint = createTextPaint(Color.parseColor("#FFF176"), false);

            mTime = new GregorianCalendar();

            mWaveRenderer = new WaveRenderer(this);
            mWaveAnimator = new WaveAnimator(mWaveRenderer);

            mWaveRenderer.setWaveColor(behindWaveColor, frontWaveColor);

            mWaveRenderer.setWaterLevelRatio(EMPTY_LEVEL + CAPACITY);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, boolean bold) {
            Typeface myTypeface = bold ? BOLD_TYPEFACE : NORMAL_TYPEFACE;

            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(myTypeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            Log.v(TAG, "onVisibilityChagned " + visible);

            if (visible) {
                registerTimeZoneReceiver();

                mTime = new GregorianCalendar();

                updateDisplayFields();

                mWaveAnimator.start(mWaterLevel);
            } else {
                unregisterTimeZoneReceiver();

                mWaveAnimator.cancel();

                mWaveRenderer.setWaterLevelRatio(0f);
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerTimeZoneReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterTimeZoneReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            mYOffset = resources.getDimension(isRound ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);

            mDateOffset = resources.getDimension(isRound ? R.dimen.digital_date_offset_round : R.dimen.digital_date_offset);

            mMinutesOffset = resources.getDimension(R.dimen.digital_minutes_offset);

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mHoursPaint.setTextSize(textSize);

            mMinutesPaint.setTextSize(textSize * .8f);

            mDatePaint.setTextSize(textSize/4);

            mWaveRenderer.setShapeType(isRound ? WaveRenderer.ShapeType.CIRCLE : WaveRenderer.ShapeType.SQUARE);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();

            updateDisplayFields();

            invalidate();
        }

        float textWidths[] = new float[2];

        int lastHour;
        int lastMinute;

        private void updateDisplayFields() {
            mTime.setTimeInMillis(System.currentTimeMillis());

            int currentHour = mTime.get(Calendar.HOUR_OF_DAY);
            int currentMinute = mTime.get(Calendar.MINUTE);

            if (currentHour != lastHour || currentMinute != lastMinute) {
                //only create new strings when hours or minutes have changed to avoid unnecessary GC passes

                lastHour = currentHour;
                lastMinute = currentMinute;

                mHours = String.format("%02d", currentHour);
                mMinutes = String.format("%02d", currentMinute);

                mHoursPaint.getTextWidths(mHours, textWidths);

                mHoursWidth = textWidths[0] + textWidths[1];

                mDayName = mTime.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());

                String month = mTime.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());

                mDate = String.format("%s %d", month, mTime.get(Calendar.DAY_OF_MONTH));
            }


            Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            mBatteryPct = level / (float)scale;

            if (BuildConfig.DEBUG) {
                Log.v(TAG, "updateBatteryLevel " + mBatteryPct);
            }

            mWaterLevel = getWaterLevel(mBatteryPct);
        }


        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;

                updateDisplayFields();

                mWaveRenderer.setWaterLevelRatio(mWaterLevel);

                if (mLowBitAmbient) {
                    mHoursPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();

            if (inAmbientMode) {
                mWaveAnimator.pause();
            } else {
                mWaveAnimator.resume();
            }
        }

        float testPercents[] = {.05f, .50f, 1.0f};

        final static float EMPTY_LEVEL = 0.13f;
        final static float CAPACITY = 0.40f;

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    if (BuildConfig.DEBUG) {
                        mTapCount++;
                        mWaterLevel = getWaterLevel(testPercents[mTapCount % testPercents.length]);
                        mWaveRenderer.setWaterLevelRatio(mWaterLevel);
                    }
                    break;
            }
            invalidate();
        }

        private float getWaterLevel(float percent) {
            return EMPTY_LEVEL + CAPACITY * percent;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            canvas.drawText(mHours, mXOffset, mYOffset, mHoursPaint);
            canvas.drawText(mMinutes, mXOffset+mHoursWidth, mYOffset-mMinutesOffset, mMinutesPaint);

            float lineHeight = mDatePaint.getFontSpacing();

            float dateOffset = mYOffset - lineHeight;
            canvas.drawText(mDayName, bounds.width()-mDateOffset, dateOffset, mDatePaint);

            canvas.drawText(mDate, bounds.width()-mDateOffset, dateOffset + lineHeight, mDatePaint);

            mWaveRenderer.draw(canvas, bounds);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

    }
}
