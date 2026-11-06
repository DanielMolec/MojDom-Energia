package pl.mojdom.energia;

import android.content.Context;
import android.graphics.*;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

/** Schemat instalacji: kocioł, zasobnik CWU, pompa, zawór 3-drogowy i dom z grzejnikami. */
final class BoilerDiagram extends View {
    interface Listener { void onTap(int metric); void onLongPress(int metric); void onHealthTap(); }
    static final int HEALTH_UNKNOWN = -1, HEALTH_OK = 0, HEALTH_WARN = 1, HEALTH_ALARM = 2;

    static final int HOT = 0, COLD = 1, NEUTRAL = 2, STATUS = 3;
    private static final float W = 500, H = 285;
    private static final int RED = Color.rgb(234, 88, 52), BLUE = Color.rgb(59, 130, 222);
    private static final int ORANGE = Color.rgb(245, 158, 11), SKY = Color.rgb(96, 180, 240);
    private static final int LABEL_BG = Color.rgb(24, 34, 54), OUTLINE = Color.argb(200, 245, 184, 46); // dom w złotym konturze (styl „Złota noc”)

    private static final class Label {
        final int metric, kind; final float cx, cy; String text = "—"; final RectF hit = new RectF();
        Label(int metric, float cx, float cy, int kind) { this.metric = metric; this.cx = cx; this.cy = cy; this.kind = kind; }
    }

    private final ArrayList<Label> labels = new ArrayList<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path supply = new Path(), ret = new Path(), tmp = new Path();
    private final GestureDetector gestures;
    private Listener listener;
    private boolean burnerOn = false, flowing = false;
    private float phase = 0, fanAngle = 0, scale = 1;
    private float flowSpeed = 0, fanSpeed = 0; // 0..1
    private float smokeT = 0;
    private int health = HEALTH_UNKNOWN;
    private static final float HX = 102, HY = 80, HR = 10; // ikona stanu kotła

    BoilerDiagram(Context c, int burner, int power, int flow, int fan, int pump, int valve, int ret, int house, int outside, int dhw) {
        super(c);
        labels.add(new Label(burner, 50, 30, STATUS));
        labels.add(new Label(power, 120, 30, HOT));
        labels.add(new Label(flow, 57, 110, HOT));
        labels.add(new Label(fan, 46, 172, NEUTRAL));
        labels.add(new Label(pump, 124, 170, NEUTRAL));
        labels.add(new Label(valve, 182, 170, HOT));
        labels.add(new Label(ret, 76, 244, COLD));
        labels.add(new Label(dhw, 199, 32, HOT));
        labels.add(new Label(house, 360, 100, NEUTRAL));
        labels.add(new Label(outside, 455, 22, COLD));
        txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        txt.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        buildPipes();
        setContentDescription("Schemat instalacji grzewczej z odczytami z Home Assistant");
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (onHealth(e)) { if (listener != null) listener.onHealthTap(); return true; }
                Label l = find(e); if (l != null && listener != null) listener.onTap(l.metric); return l != null; }
            @Override public void onLongPress(MotionEvent e) { Label l = find(e); if (l != null && listener != null) listener.onLongPress(l.metric); }
        });
    }

    void setListener(Listener l) { listener = l; }

    /** Stan kotła dla ikony na schemacie: HEALTH_OK / HEALTH_WARN / HEALTH_ALARM / HEALTH_UNKNOWN. */
    void setHealth(int h) { if (h != health) { health = h; invalidate(); } }

    private boolean onHealth(MotionEvent e) {
        float x = e.getX() / scale - HX, y = e.getY() / scale - HY;
        return x * x + y * y <= (HR + 10) * (HR + 10);
    }

    /** Ustawia tekst etykiety danej metryki. */
    void setValue(int metric, String text) { for (Label l : labels) if (l.metric == metric) l.text = text; }

    /**
     * @param flowSpeed prędkość przepływu wody 0..1 (wg wysterowania/mocy pompy), 0 = brak przepływu
     * @param fanSpeed  prędkość wentylatora 0..1 (wg obrotów), używana gdy palnik pracuje
     */
    void setState(boolean burnerOn, float flowSpeed, float fanSpeed) {
        this.burnerOn = burnerOn;
        this.flowSpeed = Math.max(0, Math.min(1, flowSpeed));
        this.fanSpeed = Math.max(0, Math.min(1, fanSpeed));
        this.flowing = this.flowSpeed > 0.001f;
        invalidate();
    }

    private void buildPipes() {
        // Zasilanie (czerwone): kocioł → pompa → zawór → grzejniki, odgałęzienie do zasobnika
        supply.moveTo(64, 196); supply.lineTo(64, 204); supply.lineTo(430, 204);
        supply.moveTo(182, 204); supply.lineTo(182, 150);
        for (float x : new float[]{282, 352, 426}) { supply.moveTo(x, 204); supply.lineTo(x, 168); }
        // Powrót (niebieski): grzejniki → kocioł, powrót z wężownicy zasobnika
        for (float x : new float[]{318, 388, 448}) { ret.moveTo(x, 168); ret.lineTo(x, 226); }
        ret.moveTo(448, 226); ret.lineTo(40, 226); ret.lineTo(40, 196);
        ret.moveTo(205, 120); ret.lineTo(236, 120); ret.lineTo(236, 226);
    }

    @Override protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, Math.round(width * H / W));
    }

    private Label find(MotionEvent e) {
        float x = e.getX() / scale, y = e.getY() / scale;
        Label best = null;
        for (Label l : labels) {
            RectF r = new RectF(l.hit); r.inset(-8, -8);
            if (r.contains(x, y)) best = l;
        }
        return best;
    }

    @Override public boolean onTouchEvent(MotionEvent e) { return gestures.onTouchEvent(e) || super.onTouchEvent(e); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        scale = getWidth() / W;
        canvas.save();
        canvas.scale(scale, scale);
        drawHouse(canvas);
        drawPipes(canvas);
        drawTank(canvas);
        drawBoiler(canvas);
        if (burnerOn) drawSmoke(canvas);
        drawHealth(canvas);
        drawPump(canvas, 116, 204);
        drawValve(canvas, 182, 204);
        drawOutsideSensor(canvas, 470, 56);
        for (Label l : labels) drawLabel(canvas, l);
        canvas.restore();
        boolean fanOn = fanSpeed > 0.001f;
        if ((flowing || fanOn || burnerOn) && isShown()) {
            if (burnerOn) smokeT = (smokeT + 0.006f) % 1;
            // 0,04 … 0,4 jednostki na klatkę: wolna pompa = wolny przepływ, 100 % = najszybszy
            if (flowing) phase = (phase + 0.04f + 0.36f * flowSpeed) % 24;
            // 0,2 … 5° na klatkę — wolne obroty = wolny wentylator
            if (fanOn) fanAngle = (fanAngle + 0.2f + 4.8f * fanSpeed) % 360;
            postInvalidateDelayed(40);
        }
    }

    private void drawHouse(Canvas c) {
        stroke.setColor(OUTLINE); stroke.setStrokeWidth(3.5f); stroke.setPathEffect(null);
        tmp.reset();
        tmp.moveTo(250, 258); tmp.lineTo(250, 118); tmp.lineTo(358, 24); tmp.lineTo(472, 118); tmp.lineTo(472, 258); tmp.close();
        c.drawPath(tmp, stroke);
        tmp.reset(); tmp.moveTo(420, 72); tmp.lineTo(420, 44); tmp.lineTo(436, 44); tmp.lineTo(436, 86);
        c.drawPath(tmp, stroke);
        drawRadiator(c, 276, 136, 48);
        drawRadiator(c, 346, 136, 48);
        drawRadiator(c, 420, 140, 34);
    }

    private void drawRadiator(Canvas c, float x, float y, float w) {
        float h = 32;
        fill.setShader(new LinearGradient(0, y, 0, y + h, Color.rgb(248, 250, 252), Color.rgb(203, 213, 225), Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 3, 3, fill);
        fill.setShader(null);
        stroke.setColor(Color.rgb(148, 163, 184)); stroke.setStrokeWidth(1);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 3, 3, stroke);
        for (float fx = x + 6; fx < x + w - 3; fx += 6) c.drawLine(fx, y + 4, fx, y + h - 4, stroke);
    }

    private void drawPipes(Canvas c) {
        stroke.setPathEffect(null); stroke.setStrokeWidth(4);
        stroke.setColor(BLUE); c.drawPath(ret, stroke);
        stroke.setColor(RED); c.drawPath(supply, stroke);
        if (flowing) {
            stroke.setStrokeWidth(2);
            stroke.setPathEffect(new DashPathEffect(new float[]{6, 18}, 24 - phase));
            stroke.setColor(Color.argb(170, 255, 220, 200)); c.drawPath(supply, stroke);
            stroke.setPathEffect(new DashPathEffect(new float[]{6, 18}, 24 - phase));
            stroke.setColor(Color.argb(170, 210, 235, 255)); c.drawPath(ret, stroke);
            stroke.setPathEffect(null);
        }
    }

    private void drawTank(Canvas c) {
        RectF body = new RectF(162, 52, 208, 150);
        fill.setShader(new LinearGradient(body.left, 0, body.right, 0,
            new int[]{Color.rgb(120, 128, 138), Color.rgb(214, 219, 225), Color.rgb(110, 118, 128)}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(body, 18, 12, fill);
        fill.setShader(null);
        fill.setColor(Color.rgb(90, 98, 108));
        c.drawRect(170, 150, 174, 160, fill); c.drawRect(196, 150, 200, 160, fill);
        c.drawCircle(178, 132, 3, fill);
        stroke.setColor(Color.rgb(90, 98, 108)); stroke.setStrokeWidth(1);
        c.drawLine(164, 70, 206, 70, stroke);
    }

    private void drawBoiler(Canvas c) {
        // komin
        fill.setColor(Color.rgb(156, 163, 175));
        c.drawRect(48, 66, 66, 90, fill);
        c.drawRoundRect(new RectF(4, 62, 66, 74), 5, 5, fill);
        c.drawRect(4, 48, 16, 74, fill);
        fill.setColor(Color.rgb(120, 128, 140)); c.drawRect(2, 46, 18, 50, fill);
        RectF box = new RectF(22, 88, 94, 198);
        fill.setShader(new LinearGradient(box.left, 0, box.right, 0,
            new int[]{Color.rgb(176, 183, 192), Color.rgb(229, 232, 236), Color.rgb(170, 177, 186)}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(box, 6, 6, fill);
        fill.setShader(null);
        // wentylator
        float fx = 58, fy = 146;
        fill.setColor(Color.rgb(51, 65, 85)); c.drawCircle(fx, fy, 13, fill);
        fill.setColor(Color.WHITE);
        float rot = fanAngle;
        for (int i = 0; i < 3; i++) {
            c.save(); c.rotate(rot + i * 120, fx, fy);
            c.drawOval(new RectF(fx - 3.5f, fy - 11, fx + 3.5f, fy - 2), fill);
            c.restore();
        }
        fill.setColor(Color.rgb(51, 65, 85)); c.drawCircle(fx, fy, 2.5f, fill);
        if (burnerOn) { fill.setColor(Color.rgb(249, 115, 22)); c.drawCircle(86, 96, 3, fill); }
    }

    /** Dym z komina, gdy palnik pracuje: kłęby unoszą się, rosną i znikają. */
    private void drawSmoke(Canvas c) {
        for (int k = 0; k < 4; k++) {
            float f = (smokeT + k / 4f) % 1;
            float y = 44 - f * 36;
            float x = 10 + (float) Math.sin(f * 6 + k) * 2.5f - f * 3;
            float r = 2.5f + f * 5.5f;
            float a = Math.min(1, f / 0.12f) * (1 - f);
            fill.setColor(Color.argb(Math.round(170 * a), 150, 158, 168));
            c.drawCircle(x, y, r, fill);
        }
    }

    /** Ikona stanu kotła: zielony znaczek = wszystko OK, żółty/czerwony wykrzyknik = ostrzeżenie/alarm. */
    private void drawHealth(Canvas c) {
        int col = health == HEALTH_OK ? Color.rgb(34, 197, 94) : health == HEALTH_WARN ? Color.rgb(245, 158, 11)
            : health == HEALTH_ALARM ? Color.rgb(214, 64, 69) : Color.rgb(148, 163, 184);
        fill.setShader(null);
        fill.setColor(Color.argb(40, 0, 0, 0)); c.drawCircle(HX, HY + 1.5f, HR, fill);
        fill.setColor(col); c.drawCircle(HX, HY, HR, fill);
        stroke.setPathEffect(null); stroke.setColor(Color.WHITE); stroke.setStrokeWidth(2.4f);
        if (health == HEALTH_OK) {
            tmp.reset(); tmp.moveTo(HX - 4.5f, HY + 0.3f); tmp.lineTo(HX - 1.3f, HY + 3.5f); tmp.lineTo(HX + 4.8f, HY - 3.5f);
            c.drawPath(tmp, stroke);
        } else if (health == HEALTH_UNKNOWN) {
            fill.setColor(Color.WHITE); c.drawCircle(HX, HY, 2, fill);
        } else {
            c.drawLine(HX, HY - 5, HX, HY + 1.2f, stroke);
            fill.setColor(Color.WHITE); c.drawCircle(HX, HY + 4.6f, 1.5f, fill);
        }
    }

    private void drawPump(Canvas c, float x, float y) {
        fill.setColor(Color.WHITE); c.drawCircle(x, y, 9, fill);
        stroke.setColor(Color.rgb(51, 65, 85)); stroke.setStrokeWidth(2); c.drawCircle(x, y, 9, stroke);
        tmp.reset(); tmp.moveTo(x - 3, y - 5); tmp.lineTo(x + 5, y); tmp.lineTo(x - 3, y + 5); tmp.close();
        fill.setColor(Color.rgb(51, 65, 85)); c.drawPath(tmp, fill);
    }

    private void drawValve(Canvas c, float x, float y) {
        fill.setColor(Color.rgb(71, 85, 105));
        tmp.reset();
        tmp.moveTo(x - 10, y - 7); tmp.lineTo(x, y); tmp.lineTo(x - 10, y + 7); tmp.close();
        tmp.moveTo(x + 10, y - 7); tmp.lineTo(x, y); tmp.lineTo(x + 10, y + 7); tmp.close();
        tmp.moveTo(x - 7, y + 10); tmp.lineTo(x, y); tmp.lineTo(x + 7, y + 10); tmp.close();
        c.drawPath(tmp, fill);
        c.drawCircle(x, y, 4, fill);
        fill.setColor(Color.rgb(203, 213, 225)); c.drawCircle(x, y, 1.8f, fill);
    }

    private void drawOutsideSensor(Canvas c, float x, float y) {
        fill.setColor(Color.rgb(100, 116, 139));
        c.drawRect(x - 2, y - 8, x + 2, y + 2, fill);
        for (int i = 0; i < 4; i++) c.drawRoundRect(new RectF(x - 9 + i, y + 2 + i * 4, x + 9 - i, y + 4.5f + i * 4), 1, 1, fill);
    }

    private void drawLabel(Canvas c, Label l) {
        txt.setTextSize(15);
        float w = Math.max(46, txt.measureText(l.text) + 18), h = 28;
        RectF r = new RectF(l.cx - w / 2, l.cy - h / 2, l.cx + w / 2, l.cy + h / 2);
        if (r.left < 2) r.offset(2 - r.left, 0);
        if (r.right > W - 2) r.offset(W - 2 - r.right, 0);
        l.hit.set(r);
        int border, text, bg = LABEL_BG;
        switch (l.kind) {
            case HOT: border = ORANGE; text = Color.rgb(253, 186, 116); break;
            case COLD: border = SKY; text = Color.rgb(147, 207, 250); break;
            case STATUS:
                boolean on = burnerOn;
                bg = on ? Color.rgb(154, 63, 20) : Color.rgb(71, 85, 105);
                border = on ? ORANGE : Color.rgb(148, 163, 184);
                text = on ? Color.rgb(254, 215, 170) : Color.rgb(226, 232, 240); break;
            default: border = SKY; text = Color.WHITE;
        }
        fill.setShader(null); fill.setColor(Color.argb(40, 0, 0, 0));
        c.drawRoundRect(new RectF(r.left, r.top + 2, r.right, r.bottom + 2), 7, 7, fill);
        fill.setColor(bg); c.drawRoundRect(r, 7, 7, fill);
        stroke.setPathEffect(null); stroke.setColor(border); stroke.setStrokeWidth(1.5f);
        c.drawRoundRect(r, 7, 7, stroke);
        txt.setColor(text);
        Paint.FontMetrics fm = txt.getFontMetrics();
        c.drawText(l.text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, txt);
    }
}
