package pl.mojdom.energia;

import android.content.Context;
import android.graphics.*;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Schemat instalacji jako przekrój domu: piwnica (kocioł, CWU, pompa, zawór), piętro 1 z podłogówką,
 * piętro 2 z grzejnikami i klatka schodowa przechodząca przez oba piętra.
 */
final class BoilerDiagram extends View {
    interface Listener {
        void onTap(int metric); void onLongPress(int metric); void onHealthTap();
        void onRoomTap(String key); void onRoomLongPress(String key);
    }
    static final int HEALTH_UNKNOWN = -1, HEALTH_OK = 0, HEALTH_WARN = 1, HEALTH_ALARM = 2;
    static final int HOT = 0, COLD = 1, NEUTRAL = 2, STATUS = 3;

    /** Pomieszczenia na schemacie: klucz, {środek X, góra Y, szerokość}, czy z temperaturą zadaną (dotknij → −/+). */
    static final String[] ROOM_KEYS = {"p2_kor", "p2_laz", "p2_mik", "p2_adr", "p2_cio", "p1_syp", "p1_laz", "p1_sal", "p1_kor", "klatka"};
    private static final float[][] ROOM_POS = {{55, 128, 46}, {105, 122, 46}, {155, 122, 46}, {205, 122, 46}, {255, 122, 46},
        {61, 244, 54}, {124, 244, 54}, {186, 244, 54}, {249, 238, 54}, {304, 122, 46}};
    static final boolean[] ROOM_EDITABLE = {false, true, true, true, true, false, false, false, true, true};
    private static final float[] P2_X = {55, 105, 155, 205, 255}; // grzejniki piętra 2
    private static final float SUP_X = 276, RET_X = 272, STAIR_X = 280; // pion zasilania/powrotu przy ścianie klatki

    private static final float W = 358, H = 470;
    private static final int RED = Color.rgb(234, 88, 52), BLUE = Color.rgb(59, 130, 222);
    private static final int ORANGE = Color.rgb(245, 158, 11), SKY = Color.rgb(96, 180, 240);
    private static final int GOLD = Color.rgb(245, 184, 46), MUTED = Color.rgb(154, 164, 181), TEXT = Color.rgb(243, 244, 246);
    private static final int LABEL_BG = Color.rgb(24, 34, 54), CHIP_LINE = Color.rgb(59, 69, 88);

    private static final class Label {
        final int metric, kind; final float cx, cy; String text = "—"; final RectF hit = new RectF();
        Label(int metric, float cx, float cy, int kind) { this.metric = metric; this.cx = cx; this.cy = cy; this.kind = kind; }
    }
    private static final class Room {
        final String key; final float cx, top, w; boolean gold; String name = "", temp = "—", sub = null; int subColor = GOLD;
        final RectF hit = new RectF();
        Room(String key, float cx, float top, float w, boolean gold) { this.key = key; this.cx = cx; this.top = top; this.w = w; this.gold = gold; }
    }

    private final ArrayList<Label> labels = new ArrayList<>();
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Odcinki rur: wspólne przy kotle oraz osobne dla obiegu CO (dom) i CWU (wężownica zasobnika)
    private final Path supply = new Path(), ret = new Path(), ufh = new Path(), tmp = new Path();
    private final Path supBoiler = new Path(), supCo = new Path(), supDhw = new Path(), retCo = new Path(), retDhw = new Path(), retBoiler = new Path();
    private final GestureDetector gestures;
    private Listener listener;
    private boolean burnerOn = false, flowing = false, dhwMode = false;
    private float phase = 0, fanAngle = 0, scale = 1;
    private float flowSpeed = 0, fanSpeed = 0; // 0..1
    private float smokeT = 0;
    private int health = HEALTH_UNKNOWN;
    private static final float HX = 96, HY = 360, HR = 9; // ikona stanu kotła

    BoilerDiagram(Context c, int burner, int power, int flow, int fan, int pump, int valve, int ret, int outside, int dhw, int ufhFlow) {
        super(c);
        labels.add(new Label(burner, 72, 452, STATUS));
        labels.add(new Label(power, 145, 452, HOT));
        labels.add(new Label(fan, 73, 418, NEUTRAL));
        labels.add(new Label(dhw, 147, 404, HOT));
        labels.add(new Label(valve, 180, 352, HOT));
        labels.add(new Label(flow, 234, 352, HOT));
        labels.add(new Label(pump, 218, 398, NEUTRAL));
        labels.add(new Label(ret, 220, 452, COLD));
        labels.add(new Label(outside, 318, 30, COLD));
        labels.add(new Label(ufhFlow, 92, 299, HOT));
        for (int i = 0; i < ROOM_KEYS.length; i++) rooms.put(ROOM_KEYS[i], new Room(ROOM_KEYS[i], ROOM_POS[i][0], ROOM_POS[i][1], ROOM_POS[i][2], ROOM_EDITABLE[i]));
        txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        txt.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        buildPipes();
        setContentDescription("Przekrój domu z instalacją grzewczą i odczytami z Home Assistant");
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (listener == null) return false;
                if (onHealth(e)) { listener.onHealthTap(); return true; }
                Room r = findRoom(e); if (r != null) { listener.onRoomTap(r.key); return true; }
                Label l = find(e); if (l != null) listener.onTap(l.metric); return l != null; }
            @Override public void onLongPress(MotionEvent e) {
                if (listener == null) return;
                Room r = findRoom(e); if (r != null) { listener.onRoomLongPress(r.key); return; }
                Label l = find(e); if (l != null) listener.onLongPress(l.metric); }
        });
    }

    void setListener(Listener l) { listener = l; }

    /** Stan kotła dla ikony na schemacie: HEALTH_OK / HEALTH_WARN / HEALTH_ALARM / HEALTH_UNKNOWN. */
    void setHealth(int h) { if (h != health) { health = h; invalidate(); } }

    /** Ustawia tekst etykiety danej metryki. */
    void setValue(int metric, String text) { for (Label l : labels) if (l.metric == metric) l.text = text; }

    /** Pomieszczenie: nazwa, temperatura, dolny wiersz (np. „zadana 21°”), złota ramka = dotknij, aby zmienić. */
    void setRoom(String key, String name, String temp, String sub, int subColor, boolean gold) {
        Room r = rooms.get(key); if (r == null) return;
        r.name = name; r.temp = temp; r.sub = sub; r.subColor = subColor; r.gold = gold; invalidate();
    }

    /**
     * @param flowSpeed prędkość przepływu wody 0..1 (wg wysterowania/mocy pompy), 0 = brak przepływu
     * @param fanSpeed  prędkość wentylatora 0..1 (wg obrotów), używana gdy palnik pracuje
     * @param dhwMode   zawór 3-drogowy na CWU: woda krąży kocioł → zasobnik → kocioł; inaczej kocioł → pompa → dom → kocioł
     */
    void setState(boolean burnerOn, float flowSpeed, float fanSpeed, boolean dhwMode) {
        this.burnerOn = burnerOn; this.dhwMode = dhwMode;
        this.flowSpeed = Math.max(0, Math.min(1, flowSpeed));
        this.fanSpeed = Math.max(0, Math.min(1, fanSpeed));
        this.flowing = this.flowSpeed > 0.001f;
        invalidate();
    }

    private void buildPipes() {
        // Zasilanie od kotła do zaworu 3-drogowego (wspólne)
        supBoiler.moveTo(96, 372); supBoiler.lineTo(182, 372);
        // CO: zawór → pompa → pion przy klatce → piętro 2 i klatka
        supCo.moveTo(182, 372); supCo.lineTo(SUP_X, 372); supCo.lineTo(SUP_X, 203); supCo.lineTo(40, 203);
        for (float x : P2_X) { supCo.moveTo(x - 9, 203); supCo.lineTo(x - 9, 198); }
        supCo.moveTo(SUP_X, 306); supCo.lineTo(286, 306);
        // CWU: zawór → wężownica zasobnika
        supDhw.moveTo(182, 372); supDhw.lineTo(182, 382); supDhw.lineTo(162, 382);
        // Powrót CO: grzejniki, klatka → pion → do kotła
        for (float x : P2_X) { retCo.moveTo(x + 9, 198); retCo.lineTo(x + 9, 208); }
        retCo.moveTo(40, 208); retCo.lineTo(RET_X, 208); retCo.lineTo(RET_X, 434); retCo.lineTo(174, 434);
        retCo.moveTo(286, 318); retCo.lineTo(RET_X, 318);
        // Powrót CWU: z wężownicy do wspólnego powrotu
        retDhw.moveTo(162, 420); retDhw.lineTo(174, 420); retDhw.lineTo(174, 434);
        // Powrót wspólny do kotła
        retBoiler.moveTo(174, 434); retBoiler.lineTo(96, 434);
        supply.addPath(supBoiler); supply.addPath(supCo); supply.addPath(supDhw);
        ret.addPath(retCo); ret.addPath(retDhw); ret.addPath(retBoiler);
        // Podłogówka piętra 1: wężownica w posadzce, z pionu zasilania do pionu powrotu
        ufh.moveTo(SUP_X, 312); ufh.lineTo(36, 312); ufh.lineTo(36, 318); ufh.lineTo(260, 318); ufh.lineTo(260, 324); ufh.lineTo(40, 324); ufh.lineTo(40, 328); ufh.lineTo(RET_X, 328);
    }

    @Override protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, Math.round(width * H / W));
    }

    private boolean onHealth(MotionEvent e) {
        float x = e.getX() / scale - HX, y = e.getY() / scale - HY;
        return x * x + y * y <= (HR + 8) * (HR + 8);
    }

    private Label find(MotionEvent e) {
        float x = e.getX() / scale, y = e.getY() / scale;
        Label best = null;
        for (Label l : labels) { RectF r = new RectF(l.hit); r.inset(-6, -6); if (r.contains(x, y)) best = l; }
        return best;
    }

    private Room findRoom(MotionEvent e) {
        float x = e.getX() / scale, y = e.getY() / scale;
        for (Room r : rooms.values()) { RectF h = new RectF(r.hit); h.inset(-3, -6); if (h.contains(x, y)) return r; }
        return null;
    }

    @Override public boolean onTouchEvent(MotionEvent e) { return gestures.onTouchEvent(e) || super.onTouchEvent(e); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        scale = getWidth() / W;
        canvas.save();
        canvas.scale(scale, scale);
        drawBuilding(canvas);
        drawPipes(canvas);
        drawRadiators(canvas);
        drawTank(canvas);
        drawBoiler(canvas);
        if (burnerOn) drawSmoke(canvas);
        drawHealth(canvas);
        drawValve(canvas, 182, 372);
        drawPump(canvas, 218, 372);
        for (Room r : rooms.values()) drawRoom(canvas, r);
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

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold, float spacing) {
        txt.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        txt.setTextSize(size); txt.setColor(color); txt.setLetterSpacing(spacing);
        c.drawText(s, x, y, txt);
        txt.setLetterSpacing(0);
    }

    private void drawBuilding(Canvas c) {
        fill.setShader(null); stroke.setPathEffect(null);
        // grunt i piwnica
        fill.setColor(Color.rgb(14, 22, 38)); c.drawRect(0, 330, W, H, fill);
        stroke.setColor(Color.rgb(59, 69, 88)); stroke.setStrokeWidth(2); c.drawLine(0, 330, W, 330, stroke);
        fill.setColor(Color.rgb(16, 24, 40)); c.drawRect(30, 330, 328, 452, fill);
        stroke.setColor(Color.argb(115, 245, 184, 46)); c.drawRect(30, 330, 328, 452, stroke);
        // dom: dach, ściany, strop między piętrami, ściana klatki
        fill.setColor(Color.argb(8, 245, 184, 46)); c.drawRect(30, 94, 328, 330, fill);
        stroke.setColor(GOLD); stroke.setStrokeWidth(2.5f);
        tmp.reset(); tmp.moveTo(179, 16); tmp.lineTo(342, 94); tmp.lineTo(16, 94); tmp.close(); c.drawPath(tmp, stroke);
        c.drawRect(30, 94, 328, 330, stroke);
        stroke.setColor(Color.argb(140, 245, 184, 46)); stroke.setStrokeWidth(2); c.drawLine(30, 212, 302, 212, stroke); // strop piętra 2 z podestem klatki
        stroke.setColor(Color.argb(90, 245, 184, 46)); stroke.setStrokeWidth(1.5f); c.drawLine(STAIR_X, 94, STAIR_X, 330, stroke);
        // ścianki działowe
        stroke.setColor(Color.rgb(42, 53, 80)); stroke.setPathEffect(new DashPathEffect(new float[]{4, 4}, 0));
        for (float x : new float[]{80, 130, 180, 230}) c.drawLine(x, 114, x, 212, stroke);
        for (float x : new float[]{92.5f, 155, 217.5f}) c.drawLine(x, 232, x, 306, stroke);
        stroke.setPathEffect(null);
        // schody klatki: jeden bieg z piętra 1 (od prawej, na dole) na podest piętra 2 (po lewej, u góry)
        stroke.setColor(Color.rgb(86, 98, 122)); stroke.setStrokeWidth(2);
        tmp.reset(); tmp.moveTo(326, 330);
        for (int i = 0; i < 8; i++) { float y = 330 - (i + 1) * 14.75f; tmp.lineTo(326 - i * 5.25f, y); tmp.lineTo(326 - (i + 1) * 5.25f, y); }
        c.drawPath(tmp, stroke);
        // podpisy kondygnacji
        txt.setTextAlign(Paint.Align.LEFT);
        text(c, "PIĘTRO 2", 36, 108, 8.5f, MUTED, false, 0.18f);
        text(c, "PIĘTRO 1 • PODŁOGÓWKA", 36, 226, 8.5f, MUTED, false, 0.18f);
        text(c, "PIWNICA", 36, 346, 8.5f, MUTED, false, 0.18f);
        txt.setTextAlign(Paint.Align.CENTER);
        text(c, "KLATKA", 304, 108, 8f, MUTED, false, 0.08f);
    }

    private void drawRadiator(Canvas c, float x, float y, float w, float h, boolean dim) {
        RectF r = new RectF(x, y, x + w, y + h);
        if (dim) fill.setColor(Color.rgb(71, 85, 105));
        else fill.setShader(new LinearGradient(0, y, 0, y + h, Color.rgb(248, 250, 252), Color.rgb(203, 213, 225), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 3, 3, fill); fill.setShader(null);
        stroke.setPathEffect(null); stroke.setColor(Color.rgb(148, 163, 184)); stroke.setStrokeWidth(1);
        c.drawRoundRect(r, 3, 3, stroke);
        for (float fx = x + 5; fx < x + w - 2; fx += 5) c.drawLine(fx, y + 3, fx, y + h - 3, stroke);
    }

    private void drawRadiators(Canvas c) {
        for (int i = 0; i < P2_X.length; i++) drawRadiator(c, P2_X[i] - 14, 180, 28, 18, !rooms.get(ROOM_KEYS[i]).gold);
        drawRadiator(c, 286, 300, 22, 22, !rooms.get("klatka").gold); // grzejnik na klatce (piętro 1, pod biegiem schodów)
    }

    private void drawPipes(Canvas c) {
        stroke.setPathEffect(null);
        stroke.setStrokeWidth(2.5f); stroke.setColor(Color.argb(220, 245, 158, 11)); c.drawPath(ufh, stroke);
        stroke.setStrokeWidth(3.5f);
        stroke.setColor(BLUE); c.drawPath(ret, stroke);
        stroke.setColor(RED); c.drawPath(supply, stroke);
        if (flowing) {
            stroke.setStrokeWidth(1.6f);
            stroke.setPathEffect(new DashPathEffect(new float[]{5, 19}, 24 - phase));
            // Przepływ tylko w czynnym obiegu: CWU (kocioł → zasobnik → kocioł) albo CO (kocioł → pompa → dom → kocioł)
            stroke.setColor(Color.argb(170, 255, 220, 200)); c.drawPath(supBoiler, stroke);
            if (dhwMode) c.drawPath(supDhw, stroke); else { c.drawPath(supCo, stroke); c.drawPath(ufh, stroke); }
            stroke.setColor(Color.argb(170, 210, 235, 255)); c.drawPath(retBoiler, stroke);
            c.drawPath(dhwMode ? retDhw : retCo, stroke);
            stroke.setPathEffect(null);
        }
        // zawór mieszający podłogówki
        fill.setColor(Color.rgb(11, 18, 32)); c.drawCircle(266, 312, 5.5f, fill);
        stroke.setColor(GOLD); stroke.setStrokeWidth(1.5f); c.drawCircle(266, 312, 5.5f, stroke);
    }

    private void drawTank(Canvas c) {
        RectF body = new RectF(132, 350, 162, 436);
        fill.setShader(new LinearGradient(body.left, 0, body.right, 0,
            new int[]{Color.rgb(120, 128, 138), Color.rgb(214, 219, 225), Color.rgb(110, 118, 128)}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(body, 13, 10, fill);
        fill.setShader(null);
    }

    private void drawBoiler(Canvas c) {
        // przewód spalinowy: z boku kotła przez ścianę na zewnątrz, w górę ponad grunt
        fill.setColor(Color.rgb(156, 163, 175));
        c.drawRect(22, 366, 50, 374, fill);
        c.drawRect(18, 300, 26, 374, fill);
        fill.setColor(Color.rgb(120, 128, 140)); c.drawRect(16, 297, 28, 301, fill);
        RectF box = new RectF(50, 362, 96, 436);
        fill.setShader(new LinearGradient(box.left, 0, box.right, 0,
            new int[]{Color.rgb(176, 183, 192), Color.rgb(229, 232, 236), Color.rgb(170, 177, 186)}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(box, 6, 6, fill);
        fill.setShader(null);
        // wentylator
        float fx = 73, fy = 392;
        fill.setColor(Color.rgb(51, 65, 85)); c.drawCircle(fx, fy, 11, fill);
        fill.setColor(Color.WHITE);
        for (int i = 0; i < 3; i++) {
            c.save(); c.rotate(fanAngle + i * 120, fx, fy);
            c.drawOval(new RectF(fx - 3, fy - 9.5f, fx + 3, fy - 2), fill);
            c.restore();
        }
        fill.setColor(Color.rgb(51, 65, 85)); c.drawCircle(fx, fy, 2.2f, fill);
        if (burnerOn) { fill.setColor(Color.rgb(249, 115, 22)); c.drawCircle(86, 370, 3, fill); }
    }

    /** Dym z wylotu spalin, gdy palnik pracuje: kłęby unoszą się, rosną i znikają. */
    private void drawSmoke(Canvas c) {
        for (int k = 0; k < 4; k++) {
            float f = (smokeT + k / 4f) % 1;
            float y = 292 - f * 40;
            float x = 22 + (float) Math.sin(f * 6 + k) * 2.5f - f * 3;
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
        fill.setColor(Color.argb(60, 0, 0, 0)); c.drawCircle(HX, HY + 1.5f, HR, fill);
        fill.setColor(col); c.drawCircle(HX, HY, HR, fill);
        stroke.setPathEffect(null); stroke.setColor(Color.WHITE); stroke.setStrokeWidth(2.2f);
        if (health == HEALTH_OK) {
            tmp.reset(); tmp.moveTo(HX - 4, HY + 0.3f); tmp.lineTo(HX - 1.2f, HY + 3.2f); tmp.lineTo(HX + 4.3f, HY - 3.2f);
            c.drawPath(tmp, stroke);
        } else if (health == HEALTH_UNKNOWN) {
            fill.setColor(Color.WHITE); c.drawCircle(HX, HY, 2, fill);
        } else {
            c.drawLine(HX, HY - 4.5f, HX, HY + 1, stroke);
            fill.setColor(Color.WHITE); c.drawCircle(HX, HY + 4.2f, 1.4f, fill);
        }
    }

    private void drawPump(Canvas c, float x, float y) {
        fill.setColor(Color.WHITE); c.drawCircle(x, y, 8, fill);
        stroke.setColor(Color.rgb(51, 65, 85)); stroke.setStrokeWidth(2); c.drawCircle(x, y, 8, stroke);
        tmp.reset(); tmp.moveTo(x - 3, y - 4.5f); tmp.lineTo(x + 4.5f, y); tmp.lineTo(x - 3, y + 4.5f); tmp.close();
        fill.setColor(Color.rgb(51, 65, 85)); c.drawPath(tmp, fill);
    }

    private void drawValve(Canvas c, float x, float y) {
        fill.setColor(Color.rgb(100, 116, 139));
        tmp.reset();
        tmp.moveTo(x - 9, y - 6); tmp.lineTo(x, y); tmp.lineTo(x - 9, y + 6); tmp.close();
        tmp.moveTo(x + 9, y - 6); tmp.lineTo(x, y); tmp.lineTo(x + 9, y + 6); tmp.close();
        c.drawPath(tmp, fill);
        c.drawCircle(x, y, 3.5f, fill);
        fill.setColor(Color.rgb(203, 213, 225)); c.drawCircle(x, y, 1.6f, fill);
    }

    private void drawRoom(Canvas c, Room r) {
        float w = r.w, h = r.sub != null ? 50 : 38;
        RectF box = new RectF(r.cx - w / 2, r.top, r.cx + w / 2, r.top + h);
        r.hit.set(box);
        fill.setShader(null); fill.setColor(LABEL_BG); c.drawRoundRect(box, 8, 8, fill);
        stroke.setPathEffect(null); stroke.setColor(r.gold ? GOLD : CHIP_LINE); stroke.setStrokeWidth(r.gold ? 1.5f : 1);
        c.drawRoundRect(box, 8, 8, stroke);
        txt.setTextAlign(Paint.Align.CENTER);
        text(c, fit(r.name, w - 6, 9), r.cx, r.top + 14, 9, MUTED, false, 0);
        text(c, r.temp, r.cx, r.top + 29, 12, TEXT, true, 0);
        if (r.sub != null) text(c, fit(r.sub, w - 4, 8.5f), r.cx, r.top + 44, 8.5f, r.subColor, true, 0);
    }

    /** Skraca tekst z „…”, aby zmieścił się w szerokości. */
    private String fit(String s, float max, float size) {
        txt.setTextSize(size);
        if (txt.measureText(s) <= max) return s;
        while (s.length() > 1 && txt.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private void drawLabel(Canvas c, Label l) {
        txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        txt.setTextSize(10.5f); txt.setTextAlign(Paint.Align.CENTER);
        float w = Math.max(36, txt.measureText(l.text) + 12), h = 19;
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
        fill.setShader(null); fill.setColor(bg); c.drawRoundRect(r, 6, 6, fill);
        stroke.setPathEffect(null); stroke.setColor(border); stroke.setStrokeWidth(1.2f);
        c.drawRoundRect(r, 6, 6, stroke);
        txt.setColor(text);
        Paint.FontMetrics fm = txt.getFontMetrics();
        c.drawText(l.text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, txt);
    }
}
