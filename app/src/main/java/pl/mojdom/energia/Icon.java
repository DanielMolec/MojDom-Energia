package pl.mojdom.energia;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Proste ikony liniowe rysowane w siatce 24×24 (bez zewnętrznych zasobów). */
final class Icon extends View {
    static final int HOME = 0, FLAME = 1, BOLT = 2, BOILER = 3, WRENCH = 4, GEAR = 5, THERMO = 6, GAUGE = 7, CLOCK = 8, DROP = 9, CHART = 10, COIN = 11, CHECK = 12, ALERT = 13, FAN = 14, CALENDAR = 15;
    private int type, color;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    Icon(Context c, int type, int color) {
        super(c); this.type = type; this.color = color;
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
    }

    void set(int type, int color) { this.type = type; this.color = color; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2, (getHeight() - 24 * s) / 2);
        c.scale(s, s);
        p.setColor(color); p.setStrokeWidth(2f); p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (type) {
            case HOME:
                path.moveTo(3, 11); path.lineTo(12, 3.5f); path.lineTo(21, 11);
                path.moveTo(5.5f, 9.5f); path.lineTo(5.5f, 20); path.lineTo(18.5f, 20); path.lineTo(18.5f, 9.5f);
                path.moveTo(10, 20); path.lineTo(10, 14); path.lineTo(14, 14); path.lineTo(14, 20);
                c.drawPath(path, p); break;
            case FLAME:
                path.moveTo(12, 21);
                path.cubicTo(7.5f, 21, 5.5f, 17.5f, 6.5f, 14);
                path.cubicTo(7.3f, 11, 10, 9.5f, 10.5f, 3.5f);
                path.cubicTo(14, 6, 18.5f, 10, 17.8f, 15);
                path.cubicTo(17.3f, 18.5f, 15, 21, 12, 21); path.close();
                path.moveTo(12, 18); path.cubicTo(10.5f, 18, 9.8f, 16.5f, 10.5f, 15); path.cubicTo(11, 14, 12, 13.5f, 12.2f, 12.3f);
                path.cubicTo(13.8f, 13.6f, 14.2f, 16.8f, 12, 18);
                c.drawPath(path, p); break;
            case BOLT:
                path.moveTo(13, 2.5f); path.lineTo(5.5f, 13.5f); path.lineTo(11.5f, 13.5f); path.lineTo(10.5f, 21.5f); path.lineTo(18.5f, 10); path.lineTo(12.5f, 10); path.close();
                c.drawPath(path, p); break;
            case BOILER:
                c.drawRoundRect(new RectF(5, 3, 19, 21), 2.5f, 2.5f, p);
                c.drawLine(5, 8, 19, 8, p);
                c.drawCircle(12, 14.5f, 3.2f, p);
                c.drawLine(8, 5.5f, 9, 5.5f, p); c.drawLine(15, 5.5f, 16, 5.5f, p);
                break;
            case WRENCH:
                path.moveTo(14.5f, 4); path.cubicTo(17, 3, 20, 4.5f, 20.3f, 7.5f); path.lineTo(17.5f, 9.3f); path.lineTo(15, 8.9f); path.lineTo(14.6f, 6.5f); path.lineTo(16.4f, 3.7f);
                path.moveTo(14.5f, 4); path.cubicTo(12.2f, 5, 11.5f, 8, 12.6f, 10); path.lineTo(4.3f, 18.3f); path.cubicTo(3.5f, 19.1f, 4.9f, 20.5f, 5.7f, 19.7f); path.lineTo(14, 11.4f);
                path.cubicTo(16, 12.5f, 19, 11.8f, 20.3f, 7.5f);
                c.drawPath(path, p); break;
            case GEAR:
                c.drawCircle(12, 12, 3, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine((float) (12 + 6 * Math.cos(a)), (float) (12 + 6 * Math.sin(a)), (float) (12 + 8.5 * Math.cos(a)), (float) (12 + 8.5 * Math.sin(a)), p);
                }
                c.drawCircle(12, 12, 6, p); break;
            case THERMO:
                path.moveTo(10, 14); path.lineTo(10, 5); path.cubicTo(10, 2.5f, 14, 2.5f, 14, 5); path.lineTo(14, 14);
                c.drawPath(path, p);
                c.drawCircle(12, 17, 3.5f, p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(12, 17, 1.6f, p); p.setStyle(Paint.Style.STROKE);
                c.drawLine(12, 15, 12, 8, p); break;
            case GAUGE:
                c.drawArc(new RectF(3, 5, 21, 23), 180, 180, false, p);
                c.drawLine(12, 14, 16, 9.5f, p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(12, 14, 1.6f, p); p.setStyle(Paint.Style.STROKE);
                c.drawLine(3, 14, 21, 14, p); break;
            case CLOCK:
                c.drawCircle(12, 12, 9, p); c.drawLine(12, 7, 12, 12, p); c.drawLine(12, 12, 15.5f, 14, p); break;
            case DROP:
                path.moveTo(12, 3); path.cubicTo(8, 8, 6, 11, 6, 14.5f); path.cubicTo(6, 18, 8.7f, 21, 12, 21); path.cubicTo(15.3f, 21, 18, 18, 18, 14.5f); path.cubicTo(18, 11, 16, 8, 12, 3); path.close();
                c.drawPath(path, p); break;
            case CHART:
                c.drawLine(4, 20, 20, 20, p);
                path.moveTo(5, 16); path.lineTo(9.5f, 11); path.lineTo(13, 14); path.lineTo(19, 6);
                c.drawPath(path, p); break;
            case COIN:
                c.drawCircle(12, 12, 8.5f, p);
                path.moveTo(14.8f, 9); path.cubicTo(14, 7.8f, 9.5f, 7.6f, 9.5f, 10); path.cubicTo(9.5f, 12.3f, 14.7f, 11.6f, 14.7f, 14); path.cubicTo(14.7f, 16.4f, 10, 16.3f, 9.2f, 15);
                path.moveTo(12, 6.5f); path.lineTo(12, 17.5f);
                c.drawPath(path, p); break;
            case CHECK:
                c.drawCircle(12, 12, 9, p);
                path.moveTo(8, 12.2f); path.lineTo(10.8f, 15); path.lineTo(16.2f, 9.2f); c.drawPath(path, p); break;
            case ALERT:
                path.moveTo(12, 3.5f); path.lineTo(21, 19.5f); path.lineTo(3, 19.5f); path.close(); c.drawPath(path, p);
                c.drawLine(12, 9.5f, 12, 13.5f, p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(12, 16.5f, 1.2f, p); break;
            case FAN:
                c.drawCircle(12, 12, 9, p);
                for (int i = 0; i < 3; i++) {
                    c.save(); c.rotate(i * 120, 12, 12);
                    c.drawOval(new RectF(10, 4.8f, 14, 11), p);
                    c.restore();
                }
                break;
            case CALENDAR:
                c.drawRoundRect(new RectF(3.5f, 5, 20.5f, 20.5f), 2.5f, 2.5f, p);
                c.drawLine(3.5f, 10, 20.5f, 10, p); c.drawLine(8, 3, 8, 7, p); c.drawLine(16, 3, 16, 7, p);
                break;
        }
        c.restore();
    }
}
