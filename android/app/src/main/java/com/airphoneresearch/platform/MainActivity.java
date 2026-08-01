package com.airphoneresearch.platform;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(7, 17, 30));
        getWindow().setNavigationBarColor(Color.rgb(7, 17, 30));
        setContentView(new AirPhoneView());
    }

    private final class AirPhoneView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String[] tabs = {"Preview", "Workbench", "Experiments", "About"};
        private int tab = 0;
        private float phase = 0f;
        private long previousFrame = System.nanoTime();

        AirPhoneView() {
            super(MainActivity.this);
            paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            setBackgroundColor(Color.rgb(7, 17, 30));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - previousFrame) / 1_000_000_000f);
            previousFrame = now;
            phase += dt * 7.2f;

            int width = getWidth();
            int height = getHeight();
            drawHeader(canvas, width);
            if (tab == 0) drawPreview(canvas, width, height);
            else if (tab == 1) drawWorkbench(canvas, width, height);
            else if (tab == 2) drawExperiments(canvas, width, height);
            else drawAbout(canvas, width, height);
            drawTabs(canvas, width, height);
            postInvalidateOnAnimation();
        }

        private void drawHeader(Canvas canvas, int width) {
            paint.setColor(Color.rgb(238, 245, 255));
            paint.setTextSize(dp(23));
            paint.setFakeBoldText(true);
            canvas.drawText("AirPhone Research", dp(20), dp(38), paint);
            paint.setFakeBoldText(false);
            paint.setTextSize(dp(12));
            paint.setColor(Color.rgb(159, 176, 200));
            canvas.drawText("Offline simulation preview · not hardware validated", dp(20), dp(59), paint);
            paint.setColor(Color.rgb(36, 58, 87));
            canvas.drawRect(0, dp(72), width, dp(73), paint);
        }

        private void drawPreview(Canvas canvas, int width, int height) {
            paint.setColor(Color.rgb(99, 181, 255));
            paint.setTextSize(dp(15));
            paint.setFakeBoldText(true);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("RESONANT TETHERED FLIGHT CONCEPT", width / 2f, dp(105), paint);
            paint.setFakeBoldText(false);
            paint.setTextAlign(Paint.Align.LEFT);

            card(canvas, dp(16), dp(120), width - dp(16), dp(184));
            metric(canvas, "Measured frequency", "119.048 Hz", dp(28), dp(144));
            metric(canvas, "Twin resonance", "119.954 Hz", width / 2f + dp(4), dp(144));

            float cx = width * 0.5f;
            float cy = height * 0.43f;
            float angle = (float) Math.sin(phase) * 0.22f;

            paint.setColor(Color.rgb(85, 214, 155));
            paint.setStrokeWidth(dp(4));
            canvas.drawLine(cx, cy - dp(55), cx, cy - dp(135), paint);
            canvas.drawLine(cx, cy - dp(135), cx - dp(9), cy - dp(118), paint);
            canvas.drawLine(cx, cy - dp(135), cx + dp(9), cy - dp(118), paint);

            canvas.save();
            canvas.rotate((float) Math.toDegrees(angle), cx, cy);
            paint.setColor(Color.rgb(99, 181, 255));
            paint.setStrokeWidth(dp(7));
            canvas.drawLine(cx - dp(125), cy, cx - dp(42), cy, paint);
            canvas.drawLine(cx + dp(42), cy, cx + dp(125), cy, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(23, 50, 82));
            RectF body = new RectF(cx - dp(48), cy - dp(28), cx + dp(48), cy + dp(28));
            canvas.drawRoundRect(body, dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(168, 214, 255));
            canvas.drawRoundRect(body, dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(13));
            canvas.drawText("AIRPHONE", cx, cy + dp(5), paint);
            canvas.restore();
            paint.setTextAlign(Paint.Align.LEFT);

            paint.setColor(Color.rgb(108, 134, 164));
            paint.setStrokeWidth(dp(2));
            canvas.drawLine(cx, cy + dp(28), cx - dp(45), cy + dp(150), paint);

            card(canvas, dp(16), height - dp(222), width - dp(16), height - dp(82));
            row(canvas, "Estimator confidence", "97.8%", dp(28), height - dp(188), Color.rgb(238, 245, 255));
            row(canvas, "Safety state", "NOMINAL", dp(28), height - dp(151), Color.rgb(85, 214, 155));
            row(canvas, "Optimizer", "ACTIVE", dp(28), height - dp(114), Color.rgb(255, 199, 102));
        }

        private void drawWorkbench(Canvas canvas, int width, int height) {
            String[][] values = {
                    {"Measured frequency", "119.048 Hz"},
                    {"Twin resonance", "119.954 Hz"},
                    {"Estimator confidence", "97.8%"},
                    {"Safety state", "NOMINAL"},
                    {"Monte Carlo", "1,000 / 1,000"},
                    {"FEA status", "TEMPLATE / NOT VALIDATED"}
            };
            float top = dp(92);
            for (int i = 0; i < values.length; i++) {
                float y = top + i * dp(70);
                card(canvas, dp(16), y, width - dp(16), y + dp(56));
                row(canvas, values[i][0], values[i][1], dp(28), y + dp(33),
                        i == 3 ? Color.rgb(85, 214, 155) : Color.rgb(238, 245, 255));
            }
            paint.setColor(Color.rgb(255, 199, 102));
            paint.setTextSize(dp(12));
            canvas.drawText("No lift or power claim is shown until physical models are calibrated.", dp(20), height - dp(95), paint);
        }

        private void drawExperiments(Canvas canvas, int width, int height) {
            title(canvas, "Experiment history", dp(18), dp(108));
            String[] names = {"Baseline Experiment", "Stiffness Loss Test", "Sensor Dropout Test"};
            String[] status = {"COMPLETED", "SIMULATED", "SIMULATED"};
            for (int i = 0; i < names.length; i++) {
                float y = dp(130) + i * dp(92);
                card(canvas, dp(16), y, width - dp(16), y + dp(76));
                paint.setColor(Color.rgb(238, 245, 255));
                paint.setTextSize(dp(16));
                canvas.drawText(names[i], dp(28), y + dp(28), paint);
                paint.setColor(Color.rgb(85, 214, 155));
                paint.setTextSize(dp(12));
                canvas.drawText(status[i], dp(28), y + dp(53), paint);
            }
        }

        private void drawAbout(Canvas canvas, int width, int height) {
            title(canvas, "What this app is", dp(18), dp(112));
            String[] lines = {
                    "A mobile demonstration of a tethered resonant-flight concept.",
                    "It visualizes self-tuning control, digital-twin estimates,",
                    "safety states and simulated experiments.",
                    "",
                    "It does not control a real flying device.",
                    "It does not demonstrate levitation or a new force.",
                    "It does not prove a hidden bee mechanism.",
                    "Current values are bundled software simulation data."
            };
            paint.setTextSize(dp(14));
            paint.setColor(Color.rgb(159, 176, 200));
            float y = dp(150);
            for (String line : lines) {
                canvas.drawText(line, dp(18), y, paint);
                y += dp(25);
            }
        }

        private void drawTabs(Canvas canvas, int width, int height) {
            float top = height - dp(68);
            paint.setColor(Color.rgb(10, 24, 40));
            canvas.drawRect(0, top, width, height, paint);
            float cell = width / 4f;
            for (int i = 0; i < tabs.length; i++) {
                if (i == tab) {
                    paint.setColor(Color.rgb(99, 181, 255));
                    canvas.drawRoundRect(new RectF(i * cell + dp(7), top + dp(7), (i + 1) * cell - dp(7), height - dp(7)), dp(10), dp(10), paint);
                }
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setColor(i == tab ? Color.rgb(7, 17, 30) : Color.rgb(159, 176, 200));
                paint.setTextSize(dp(12));
                paint.setFakeBoldText(i == tab);
                canvas.drawText(tabs[i], i * cell + cell / 2f, top + dp(40), paint);
            }
            paint.setFakeBoldText(false);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void card(Canvas canvas, float left, float top, float right, float bottom) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(15, 27, 43));
            canvas.drawRoundRect(new RectF(left, top, right, bottom), dp(14), dp(14), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(36, 58, 87));
            canvas.drawRoundRect(new RectF(left, top, right, bottom), dp(14), dp(14), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void metric(Canvas canvas, String label, String value, float x, float y) {
            paint.setColor(Color.rgb(159, 176, 200));
            paint.setTextSize(dp(11));
            canvas.drawText(label, x, y, paint);
            paint.setColor(Color.rgb(238, 245, 255));
            paint.setTextSize(dp(18));
            paint.setFakeBoldText(true);
            canvas.drawText(value, x, y + dp(25), paint);
            paint.setFakeBoldText(false);
        }

        private void row(Canvas canvas, String label, String value, float x, float y, int valueColor) {
            paint.setColor(Color.rgb(159, 176, 200));
            paint.setTextSize(dp(12));
            canvas.drawText(label, x, y, paint);
            paint.setColor(valueColor);
            paint.setTextSize(dp(15));
            paint.setFakeBoldText(true);
            canvas.drawText(value, x + dp(150), y, paint);
            paint.setFakeBoldText(false);
        }

        private void title(Canvas canvas, String value, float x, float y) {
            paint.setColor(Color.rgb(238, 245, 255));
            paint.setTextSize(dp(20));
            paint.setFakeBoldText(true);
            canvas.drawText(value, x, y, paint);
            paint.setFakeBoldText(false);
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP && event.getY() > getHeight() - dp(80)) {
                tab = Math.max(0, Math.min(3, (int) (event.getX() / (getWidth() / 4f))));
                invalidate();
            }
            return true;
        }
    }
}
