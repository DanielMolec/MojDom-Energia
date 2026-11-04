package pl.mojdom.energia;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Okrągły wskaźnik temperatury: łuk 270°, duża wartość w środku. */
final class Ring extends View {
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent, track, textColor, muted;
    private String value = "—", top = "", bottom = "";
    private float fraction = 0, target = -1;

    Ring(Context c, int accent, int track, int textColor, int muted) {
        super(c); this.accent = accent; this.track = track; this.textColor = textColor; this.muted = muted;
        arc.setStyle(Paint.Style.STROKE); arc.setStrokeCap(Paint.Cap.ROUND);
        txt.setTextAlign(Paint.Align.CENTER);
    }

    /** fraction i target w zakresie 0..1 (target < 0 = brak znacznika temperatury zadanej). */
    void set(String value, String top, String bottom, float fraction, float target) {
        this.value = value; this.top = top; this.bottom = bottom;
        this.fraction = Math.max(0, Math.min(1, fraction)); this.target = target; invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int size = Math.min(width, Math.round(230 * getResources().getDisplayMetrics().density));
        setMeasuredDimension(width, size);
    }

    @Override protected void onDraw(Canvas c) {
        float size = getHeight(), cx = getWidth() / 2f, cy = size / 2f, stroke = size * 0.075f;
        float r = size / 2f - stroke;
        RectF box = new RectF(cx - r, cy - r, cx + r, cy + r);
        arc.setStrokeWidth(stroke); arc.setShader(null); arc.setColor(track);
        c.drawArc(box, 135, 270, false, arc);
        if (fraction > 0) {
            arc.setShader(new SweepGradient(cx, cy, new int[]{Color.rgb(96, 196, 180), accent, Color.rgb(96, 196, 180)}, new float[]{0, 0.5f, 1}));
            c.save(); c.rotate(90, cx, cy);
            c.drawArc(box, 45, 270 * fraction, false, arc);
            c.restore();
            arc.setShader(null);
        }
        if (target >= 0 && target <= 1) {
            double a = Math.toRadians(135 + 270 * target);
            arc.setColor(textColor); arc.setStrokeWidth(stroke * 0.35f);
            c.drawLine((float) (cx + (r - stroke * 0.9) * Math.cos(a)), (float) (cy + (r - stroke * 0.9) * Math.sin(a)),
                (float) (cx + (r + stroke * 0.9) * Math.cos(a)), (float) (cy + (r + stroke * 0.9) * Math.sin(a)), arc);
        }
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        txt.setColor(muted); txt.setTextSize(size * 0.07f);
        c.drawText(top, cx, cy - size * 0.17f, txt);
        txt.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        txt.setColor(textColor); txt.setTextSize(size * 0.22f);
        c.drawText(value, cx, cy + size * 0.07f, txt);
        txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        txt.setColor(muted); txt.setTextSize(size * 0.068f);
        c.drawText(bottom, cx, cy + size * 0.22f, txt);
    }
}
