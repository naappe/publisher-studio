package com.airphoneresearch.platform;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(7, 17, 30));
        getWindow().setNavigationBarColor(Color.rgb(7, 17, 30));
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        setContentView(new AirPhoneView());
    }

    private final class AirPhoneView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int tab = 0;
        private float phase = 0f;
        private long lastFrame = System.nanoTime();
        private final String[] tabs = {"Preview", "Workbench", "Experiments", "About"};

        AirPhoneView() {
            super(MainActivity.this);
            paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            setBackgroundColor(Color.rgb(7, 17, 30));
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
            lastFrame = now;
            phase += dt * 7.2f;

            int w = getWidth();
            int h = getHeight();
            drawHeader(c, w);
            if (tab == 0) drawPreview(c, w, h);
            else if (tab == 1) drawWorkbench(c, w, h);
            else if (tab == 2) drawExperiments(c, w, h);
            else drawAbout(c, w, h);
            drawTabs(c, w, h);
            postInvalidateOnAnimation();
        }

        private void drawHeader(Canvas c, int w) {
            paint.setColor(Color.rgb(238, 245, 255));
            paint.setTextSize(dp(24));
            paint.setFakeBoldText(true);
            c.drawText("AirPhone Research", dp(20), dp(38), paint);
            paint.setFakeBoldText(false);
            paint.setTextSize(dp(12));
            paint.setColor(Color.rgb(159, 176, 200));
            c.drawText("Offline simulation preview · not hardware validated", dp(20), dp(59), paint);
            paint.setColor(Color.rgb(36, 58, 87));
            c.drawRect(0, dp(72), w, dp(73), paint);
        }

        private void drawPreview(Canvas c, int w, int h) {
            card(c, dp(16), dp(90), w - dp(16), dp(150));
            metric(c, "Resonance", "119.954 Hz", dp(28), dp(113));
            metric(c, "Confidence", "97.8%", w / 2f + dp(5), dp(113));

            float cx = w * 0.5f;
            float cy = h * 0.43f;
            float angle = (float)Math.sin(phase) * 0.22f;

            paint.setColor(Color.rgb(85, 214, 155));
            paint.setStrokeWidth(dp(4));
            c.drawLine(cx, cy - dp(55), cx, cy - dp(135), paint);
            c.drawLine(cx, cy - dp(135), cx - dp(9), cy - dp(118), paint);
            c.drawLine(cx, cy - dp(135), cx + dp(9), cy - dp(118), paint);

            c.save();
            c.rotate((float)Math.toDegrees(angle), cx, cy);
            paint.setColor(Color.rgb(99, 181, 255));
            paint.setStrokeWidth(dp(7));
            c.drawLine(cx - dp(125), cy, cx - dp(42), cy, paint);
            c.drawLine(cx + dp(42), cy, cx + dp(125), cy, paint);
            paint.setColor(Color.rgb(23, 50, 82));
            paint.setStyle(Paint.Style.FILL);
            RectF body = new RectF(cx - dp(48), cy - dp(28), cx + dp(48), cy + dp(28));
            c.drawRoundRect(body, dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(168, 214, 255));
            c.drawRoundRect(body, dp(12), dp(12), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(13));
            c.drawText("AIRPHONE", cx, cy + dp(5), paint);
            c.restore();
            paint.setTextAlign(Paint.Align.LEFT);

            paint.setColor(Color.rgb(108, 134, 164));
            paint.setStrokeWidth(dp(2));
            c.drawLine(cx, cy + dp(28), cx - dp(45), cy + dp(155), paint);

            card(c, dp(16), h - dp(205), w - dp(16), h - dp(82));
            text(c, "Safety state", "NOMINAL", dp(28), h - dp(175), Color.rgb(85,214,155));
            text(c, "Optimizer", "ACTIVE", dp(28), h - dp(139), Color.rgb(255,199,102));
            text(c, "Mode", "Simulation proxy", dp(28), h - dp(103), Color.rgb(159,176,200));
        }

        private void drawWorkbench(Canvas c, int w, int h) {
            String[][] values = {
                    {"Measured frequency", "119.048 Hz"},
                    {"Twin resonance", "119.954 Hz"},
                    {"Estimator confidence", "97.8%"},
                    {"Safety state", "NOMINAL"},
                    {"Monte Carlo", "1,000 / 1,000"},
                    {"FEA gate", "TEMPLATE"}
            };
            float top = dp(92);
            for (int i = 0; i < values.length; i++) {
                float y = top + i * dp(70);
                card(c, dp(16), y, w - dp(16), y + dp(56));
                text(c, values[i][0], values[i][1], dp(28), y + dp(20),
                        i == 3 ? Color.rgb(85,214,155) : Color.rgb(238,245,255));
            }
            paint.setColor(Color.rgb(255, 199, 102));
            paint.setTextSize(dp(12));
            c.drawText("Lift, power and FEA values are not physical validation.", dp(20), h - dp(95), paint);
        }

        private void drawExperiments(Canvas c, int w, int h) {
            paint.setColor(Color.rgb(238,245,255));
            paint.setTextSize(dp(20));
            paint.setFakeBoldText(true);
            c.drawText("Experiment history", dp(18), dp(108), paint);
            paint.setFakeBoldText(false);
            String[] names = {"Baseline Experiment", "Stiffness Loss Test", "Sensor Dropout Test"};
            String[] status = {"COMPLETED", "SIMULATED", "SIMULATED"};
            for (int i = 0; i < names.length; i++) {
                float y = dp(130) + i * dp(92);
                card(c, dp(16), y, w - dp(16), y + dp(76));
                paint.setColor(Color.rgb(238,245,255));
                paint.setTextSize(dp(16));
                c.drawText(names[i], dp(28), y + dp(28), paint);
                paint.setColor(Color.rgb(85,214,155));
                paint.setTextSize(dp(12));
                c.drawText(status[i], dp(28), y + dp(53), paint);
            }
        }

        private void drawAbout(Canvas c, int w, int h) {
            paint.setColor(Color.rgb(238,245,255));
            paint.setTextSize(dp(21));
            paint.setFakeBoldText(true);
            c.drawText("What this app is", dp(18), dp(112), paint);
            paint.setFakeBoldText(false);
            String[] lines = {
                    "A mobile demonstration of the AirPhone research concept.",
                    "It visualizes a self-tuning resonant flight controller,",
                    "digital twin estimates, safety states and experiments.",
                    "",
                    "It does not control a real flying device.",
                    "It does not prove a new force or bee mechanism.",
                    "The current values are bundled simulation data."
            };
            paint.setTextSize(dp(14));
            paint.setColor(Color.rgb(159,176,200));
            float y = dp(150);
            for (String line : lines) {
                c.drawText(line, dp(18), y, paint);
                y += dp(25);
            }
        }

        private void drawTabs(Canvas c, int w, int h) {
            float top = h - dp(68);
            paint.setColor(Color.rgb(10, 24, 40));
            c.drawRect(0, top, w, h, paint);
            float cell = w / 4f;
            for (int i = 0; i < 4; i++) {
                if (i == tab) {
                    paint.setColor(Color.rgb(99,181,255));
                    c.drawRoundRect(new RectF(i * cell + dp(7), top + dp(7), (i + 1) * cell - dp(7), h - dp(7)), dp(10), dp(10), paint);
                }
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setColor(i == tab ? Color.rgb(7,17,30) : Color.rgb(159,176,200));
                paint.setTextSize(dp(12));
                paint.setFakeBoldText(i == tab);
                c.drawText(tabs[i], i * cell + cell / 2f, top + dp(40), paint);
            }
            paint.setFakeBoldText(false);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void card(Canvas c, float l, float t, float r, float b) {
            paint.setColor(Color.rgb(15, 27, 43));
            paint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(l,t,r,b), dp(14), dp(14), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(36,58,87));
            c.drawRoundRect(new RectF(l,t,r,b), dp(14), dp(14), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void metric(Canvas c, String label, String value, float x, float y) {
            paint.setColor(Color.rgb(159,176,200));
            paint.setTextSize(dp(11));
            c.drawText(label, x, y, paint);
            paint.setColor(Color.rgb(238,245,255));
            paint.setTextSize(dp(18));
            paint.setFakeBoldText(true);
            c.drawText(value, x, y + dp(25), paint);
            paint.setFakeBoldText(false);
        }

        private void text(Canvas c, String label, String value, float x, float y, int valueColor) {
            paint.setColor(Color.rgb(159,176,200));
            paint.setTextSize(dp(12));
            c.drawText(label, x, y, paint);
            paint.setColor(valueColor);
            paint.setTextSize(dp(16));
            paint.setFakeBoldText(true);
            c.drawText(value, x + dp(150), y, paint);
            paint.setFakeBoldText(false);
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP && event.getY() > getHeight() - dp(80)) {
                int next = Math.max(0, Math.min(3, (int)(event.getX() / (getWidth() / 4f))));
                tab = next;
                invalidate();
                return true;
            }
            return true;
        }
    }
}
