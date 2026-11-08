package pl.mojdom.energia;

import android.content.Context;
import android.graphics.*;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Schemat instalacji jako przekrój domu z dachem na krokwiach, niebem i gruntem:
 * piwnica (kocioł, zawór, pompa, zasobnik CWU), piętro 1 z podłogówką, piętro 2 z grzejnikami,
 * klatka schodowa z piętra 1 na piętro 2. Wygląd dzienny / nocny zmienia się automatycznie wg godziny.
 * Przy pomieszczeniach para wartości: termometr = temperatura teraz, niebieska ikona = temperatura zadana.
 */
final class BoilerDiagram extends View {
    interface Listener {
        void onTap(int metric); void onLongPress(int metric); void onHealthTap();
        void onRoomTap(String key); void onRoomLongPress(String key);
    }
    static final int HEALTH_UNKNOWN = -1, HEALTH_OK = 0, HEALTH_WARN = 1, HEALTH_ALARM = 2;
    static final int HOT = 0, COLD = 1, NEUTRAL = 2, STATUS = 3, PAIR_NOW = 4, PAIR_SET = 5;

    /** Pomieszczenia: klucz, {środek X, góra (linia nazwy)}, czy z temperaturą zadaną (dotknij → −/+). */
    static final String[] ROOM_KEYS = {"p2_kor", "p2_laz", "p2_mik", "p2_adr", "p2_cio", "p1_syp", "p1_laz", "p1_sal", "p1_kor", "klatka"};
    private static final float[][] ROOM_POS = {{55, 128}, {105, 128}, {155, 128}, {205, 128}, {255, 128},
        {61, 240}, {124, 240}, {186, 240}, {249, 240}, {305, 128}};
    static final boolean[] ROOM_EDITABLE = {false, true, true, true, true, false, false, false, true, true};
    private static final float[] P2_X = {55, 105, 155, 205, 255}; // grzejniki piętra 2
    private static final float SUP_X = 276, RET_X = 272;          // pion zasilania/powrotu przy ścianie klatki
    private static final int DAY_FROM = 7, DAY_TO = 19;           // wygląd dzienny 7:00–18:59

    private static final float W = 358, H = 470;
    private static final int RED = Color.rgb(229, 57, 53), BLUE = Color.rgb(59, 130, 222);
    private static final int ORANGE = Color.rgb(245, 158, 11), SKY = Color.rgb(96, 180, 240), GOLD = Color.rgb(245, 184, 46);
    private static final Locale PL = new Locale("pl", "PL");

    /** Paleta bieżącej pory dnia. */
    private int skyTop, skyBottom, grass, earth, wall, back, slab, slabTop, base, gable, stud, text, muted, line, setColor;
    private boolean night;

    private static final class Label {
        final int metric, kind; final float cx, cy; String text = "—"; final RectF hit = new RectF();
        Label(int metric, float cx, float cy, int kind) { this.metric = metric; this.cx = cx; this.cy = cy; this.kind = kind; }
    }
    private static final class Room {
        final String key; final float cx, top; boolean gold; String name = "", temp = "—", set = null;
        final RectF hit = new RectF();
        Room(String key, float cx, float top, boolean gold) { this.key = key; this.cx = cx; this.top = top; this.gold = gold; }
    }

    private final ArrayList<Label> labels = new ArrayList<>();
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Odcinki rur: wspólne przy kotle oraz osobne dla obiegu CO (dom) i CWU (wężownica zasobnika)
    private final Path supply = new Path(), ret = new Path(), ufh = new Path(), tmp = new Path();
    private final Path supBoiler = new Path(), supCo = new Path(), supDhw = new Path(), retCo = new Path(), retDhw = new Path(), retBoiler = new Path();
    private final Path gablePath = new Path();
    private final GestureDetector gestures;
    private Listener listener;
    private boolean burnerOn = false, flowing = false, dhwMode = false;
    private float phase = 0, fanAngle = 0, scale = 1;
    private float flowSpeed = 0, fanSpeed = 0; // 0..1
    private float smokeT = 0;
    private int health = HEALTH_UNKNOWN;
    private static final float HX = 96, HY = 356, HR = 8; // ikona stanu kotła

    BoilerDiagram(Context c, int burner, int power, int flow, int fan, int pump, int valve, int ret, int outside, int dhw, int ufhFlow, int flowSet, int dhwSet) {
        super(c);
        labels.add(new Label(burner, 62, 444, STATUS));
        labels.add(new Label(power, 108, 444, HOT));
        labels.add(new Label(fan, 160, 444, NEUTRAL));
        labels.add(new Label(ret, 222, 444, COLD));
        labels.add(new Label(valve, 180, 352, HOT));
        labels.add(new Label(pump, 222, 352, NEUTRAL));
        labels.add(new Label(ufhFlow, 92, 296, HOT));
        labels.add(new Label(flow, 108, 382, PAIR_NOW));
        labels.add(new Label(flowSet, 108, 398, PAIR_SET));
        labels.add(new Label(dhw, 190, 404, PAIR_NOW));
        labels.add(new Label(dhwSet, 190, 420, PAIR_SET));
        labels.add(new Label(outside, 318, 64, PAIR_NOW));
        for (int i = 0; i < ROOM_KEYS.length; i++) rooms.put(ROOM_KEYS[i], new Room(ROOM_KEYS[i], ROOM_POS[i][0], ROOM_POS[i][1], ROOM_EDITABLE[i]));
        txt.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        gablePath.moveTo(179, 22); gablePath.lineTo(330, 100); gablePath.lineTo(28, 100); gablePath.close();
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

    /**
     * Pomieszczenie: nazwa, temperatura teraz, temperatura zadana (null = bez wiersza zadanej),
     * gold = złota ramka (dotknij, aby zmienić).
     */
    void setRoom(String key, String name, String temp, String set, boolean gold) {
        Room r = rooms.get(key); if (r == null) return;
        r.name = name; r.temp = temp; r.set = set; r.gold = gold; invalidate();
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
        supCo.moveTo(182, 372); supCo.lineTo(SUP_X, 372); supCo.lineTo(SUP_X, 198); supCo.lineTo(40, 198);
        for (float x : P2_X) { supCo.moveTo(x - 9, 198); supCo.lineTo(x - 9, 194); }
        supCo.moveTo(SUP_X, 304); supCo.lineTo(286, 304);
        // CWU: zawór → wężownica zasobnika
        supDhw.moveTo(182, 372); supDhw.lineTo(182, 388); supDhw.lineTo(232, 388);
        // Powrót CO: grzejniki, klatka → pion → do kotła
        for (float x : P2_X) { retCo.moveTo(x + 9, 194); retCo.lineTo(x + 9, 203); }
        retCo.moveTo(40, 203); retCo.lineTo(RET_X, 203); retCo.lineTo(RET_X, 432);
        retCo.moveTo(286, 312); retCo.lineTo(RET_X, 312);
        // Powrót CWU: z wężownicy do pionu powrotu
        retDhw.moveTo(266, 426); retDhw.lineTo(RET_X, 426); retDhw.lineTo(RET_X, 432);
        // Powrót wspólny do kotła
        retBoiler.moveTo(RET_X, 432); retBoiler.lineTo(96, 432);
        supply.addPath(supBoiler); supply.addPath(supCo); supply.addPath(supDhw);
        ret.addPath(retCo); ret.addPath(retDhw); ret.addPath(retBoiler);
        // Podłogówka piętra 1: wężownica w posadzce
        ufh.moveTo(SUP_X, 309); ufh.lineTo(40, 309); ufh.lineTo(40, 313); ufh.lineTo(262, 313); ufh.lineTo(262, 317); ufh.lineTo(RET_X, 317);
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
        for (Label l : labels) { RectF r = new RectF(l.hit); r.inset(-5, -5); if (r.contains(x, y)) best = l; }
        return best;
    }

    private Room findRoom(MotionEvent e) {
        float x = e.getX() / scale, y = e.getY() / scale;
        for (Room r : rooms.values()) { RectF h = new RectF(r.hit); h.inset(-2, -4); if (h.contains(x, y)) return r; }
        return null;
    }

    @Override public boolean onTouchEvent(MotionEvent e) { return gestures.onTouchEvent(e) || super.onTouchEvent(e); }

    /** Dobiera paletę do pory dnia. */
    private void palette(int hour) {
        night = hour < DAY_FROM || hour >= DAY_TO;
        skyTop = night ? Color.rgb(11, 18, 32) : Color.rgb(124, 196, 240);
        skyBottom = night ? Color.rgb(28, 44, 76) : Color.rgb(223, 241, 252);
        grass = night ? Color.rgb(31, 81, 48) : Color.rgb(88, 185, 71);
        earth = night ? Color.rgb(36, 27, 20) : Color.rgb(122, 90, 63);
        wall = night ? Color.rgb(22, 33, 58) : Color.rgb(238, 242, 246);
        back = night ? Color.rgb(27, 40, 68) : Color.rgb(246, 248, 250);
        slab = night ? Color.rgb(42, 56, 88) : Color.rgb(207, 216, 226);
        slabTop = night ? Color.rgb(58, 74, 110) : Color.rgb(227, 233, 239);
        base = night ? Color.rgb(17, 26, 44) : Color.rgb(217, 224, 232);
        gable = night ? Color.rgb(43, 42, 51) : Color.rgb(239, 226, 201);
        stud = night ? Color.rgb(61, 58, 68) : Color.rgb(214, 195, 160);
        text = night ? Color.rgb(243, 244, 246) : Color.rgb(28, 36, 48);
        muted = night ? Color.rgb(154, 164, 181) : Color.rgb(91, 102, 117);
        line = night ? Color.rgb(51, 64, 94) : Color.rgb(195, 204, 214);
        setColor = night ? Color.rgb(125, 211, 252) : Color.rgb(47, 143, 224);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        LocalDateTime now = LocalDateTime.now();
        palette(now.getHour());
        scale = getWidth() / W;
        canvas.save();
        canvas.scale(scale, scale);
        drawSky(canvas);
        drawRoof(canvas, now);
        drawHouse(canvas);
        drawPipes(canvas);
        drawRadiators(canvas);
        drawBoiler(canvas);
        drawTank(canvas);
        if (burnerOn) drawSmoke(canvas);
        drawHealth(canvas);
        drawValve(canvas, 182, 372);
        drawPump(canvas, 206, 372);
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

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold, Paint.Align align) {
        txt.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        txt.setTextSize(size); txt.setColor(color); txt.setTextAlign(align);
        c.drawText(s, x, y, txt);
    }

    private void drawSky(Canvas c) {
        fill.setShader(new LinearGradient(0, 0, 0, 330, skyTop, skyBottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, 330, fill); fill.setShader(null);
        if (night) {
            fill.setColor(Color.argb(180, 255, 255, 255));
            float[][] stars = {{30, 20}, {120, 30}, {250, 18}, {300, 46}, {205, 58}, {60, 74}, {335, 22}, {150, 12}};
            for (float[] s : stars) c.drawCircle(s[0], s[1], 1.2f, fill);
            fill.setColor(Color.rgb(245, 230, 184)); c.drawCircle(34, 40, 11, fill);
            fill.setColor(skyTop); c.drawCircle(39, 36, 10, fill);
        } else {
            fill.setColor(Color.argb(90, 255, 246, 200)); c.drawCircle(34, 38, 17, fill);
            fill.setColor(Color.rgb(255, 246, 200)); c.drawCircle(34, 38, 12, fill);
        }
        // trawa i grunt
        fill.setColor(grass); c.drawRect(0, 322, W, 332, fill);
        fill.setColor(earth); c.drawRect(0, 332, W, H, fill);
    }

    private void drawRoof(Canvas c, LocalDateTime now) {
        // komin
        fill.setColor(Color.rgb(168, 71, 58)); c.drawRect(78, 30, 94, 66, fill);
        stroke.setPathEffect(null); stroke.setColor(Color.rgb(125, 51, 41)); stroke.setStrokeWidth(1);
        for (float y = 38; y < 66; y += 8) c.drawLine(78, y, 94, y, stroke);
        // szczyt z krokwiami
        fill.setColor(gable); c.drawPath(gablePath, fill);
        c.save(); c.clipPath(gablePath);
        stroke.setColor(stud); stroke.setStrokeWidth(5); stroke.setStrokeCap(Paint.Cap.BUTT);
        for (float x = 52; x < 320; x += 20) c.drawLine(x, 100, x, 20, stroke);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        c.restore();
        stroke.setColor(Color.rgb(138, 90, 50)); stroke.setStrokeWidth(4); c.drawLine(60, 96, 298, 96, stroke);
        // połać dachu z deskami
        fill.setColor(Color.rgb(196, 138, 82));
        tmp.reset(); tmp.moveTo(179, 10); tmp.lineTo(352, 104); tmp.lineTo(340, 110); tmp.lineTo(179, 24); tmp.lineTo(18, 110); tmp.lineTo(6, 104); tmp.close();
        c.drawPath(tmp, fill);
        stroke.setColor(Color.rgb(138, 90, 50)); stroke.setStrokeWidth(1);
        for (int i = 1; i < 4; i++) { c.drawLine(179, 10 + i * 3.5f, 352 - i * 3, 104 + i * 1.5f, stroke); c.drawLine(179, 10 + i * 3.5f, 6 + i * 3, 104 + i * 1.5f, stroke); }
        // zegar w szczycie
        fill.setColor(night ? Color.argb(230, 27, 40, 68) : Color.argb(230, 246, 248, 250));
        c.drawRoundRect(new RectF(146, 58, 212, 92), 4, 4, fill);
        text(c, now.format(DateTimeFormatter.ofPattern("HH:mm")), 179, 73, 12, text, true, Paint.Align.CENTER);
        text(c, now.format(DateTimeFormatter.ofPattern("EEEE", PL)), 179, 87, 10, muted, false, Paint.Align.CENTER);
    }

    private void drawHouse(Canvas c) {
        fill.setShader(null);
        fill.setColor(wall); c.drawRect(26, 104, 332, 330, fill);
        fill.setColor(back); tmp.reset(); tmp.moveTo(26, 104); tmp.lineTo(332, 104); tmp.lineTo(322, 112); tmp.lineTo(36, 112); tmp.close(); c.drawPath(tmp, fill);
        slab(c, 212); slab(c, 323);
        stroke.setPathEffect(null); stroke.setColor(line); stroke.setStrokeWidth(2); c.drawRect(26, 104, 332, 330, stroke);
        // piwnica
        fill.setColor(base); c.drawRect(26, 332, 332, 454, fill);
        fill.setColor(slabTop); tmp.reset(); tmp.moveTo(26, 454); tmp.lineTo(332, 454); tmp.lineTo(322, 446); tmp.lineTo(36, 446); tmp.close(); c.drawPath(tmp, fill);
        c.drawRect(26, 332, 332, 454, stroke);
        // balkon
        stroke.setColor(muted); stroke.setStrokeWidth(1); c.drawRect(6, 186, 26, 212, stroke);
        for (float x = 11; x < 26; x += 5) c.drawLine(x, 186, x, 212, stroke);
        // klatka i ścianki działowe
        stroke.setColor(line); stroke.setStrokeWidth(1.5f); c.drawLine(282, 112, 282, 323, stroke);
        stroke.setStrokeWidth(1); stroke.setPathEffect(new DashPathEffect(new float[]{3, 3}, 0));
        for (float x : new float[]{80, 130, 180, 230}) c.drawLine(x, 116, x, 205, stroke);
        for (float x : new float[]{92.5f, 155, 217.5f}) c.drawLine(x, 220, x, 300, stroke);
        stroke.setPathEffect(null);
        // schody: jeden bieg z piętra 1 (z prawej, na dole) na podest piętra 2
        stroke.setColor(muted); stroke.setStrokeWidth(2);
        tmp.reset(); tmp.moveTo(326, 323);
        for (int i = 0; i < 8; i++) { float y = 323 - (i + 1) * 13.9f; tmp.lineTo(326 - i * 5.25f, y); tmp.lineTo(326 - (i + 1) * 5.25f, y); }
        c.drawPath(tmp, stroke);
        // podpisy kondygnacji
        txt.setLetterSpacing(0.12f);
        text(c, "PIĘTRO 2", 179, 116, 8, muted, false, Paint.Align.CENTER);
        text(c, "PIĘTRO 1 • PODŁOGÓWKA", 34, 229, 8, muted, false, Paint.Align.LEFT);
        text(c, "PIWNICA", 34, 346, 8, muted, false, Paint.Align.LEFT);
        txt.setLetterSpacing(0);
    }

    /** Strop w lekkiej perspektywie. */
    private void slab(Canvas c, float y) {
        fill.setColor(slabTop); tmp.reset(); tmp.moveTo(26, y); tmp.lineTo(332, y); tmp.lineTo(322, y - 7); tmp.lineTo(36, y - 7); tmp.close(); c.drawPath(tmp, fill);
        fill.setColor(slab); c.drawRect(26, y, 332, y + 7, fill);
    }

    private void drawRadiator(Canvas c, float x, float y, float w, float h) {
        RectF r = new RectF(x, y, x + w, y + h);
        fill.setColor(Color.WHITE); c.drawRoundRect(r, 2, 2, fill);
        stroke.setPathEffect(null); stroke.setColor(Color.rgb(168, 196, 220)); stroke.setStrokeWidth(1);
        c.drawRoundRect(r, 2, 2, stroke);
        stroke.setColor(Color.rgb(188, 211, 230));
        for (float fx = x + 5; fx < x + w - 2; fx += 5) c.drawLine(fx, y + 3, fx, y + h - 3, stroke);
    }

    private void drawRadiators(Canvas c) {
        for (float x : P2_X) drawRadiator(c, x - 15, 176, 30, 18);
        drawRadiator(c, 286, 296, 20, 20); // klatka (piętro 1, pod biegiem schodów)
    }

    private void drawPipes(Canvas c) {
        stroke.setPathEffect(null);
        stroke.setStrokeWidth(2.2f); stroke.setColor(ORANGE); c.drawPath(ufh, stroke);
        stroke.setStrokeWidth(2.6f); stroke.setColor(BLUE); c.drawPath(ret, stroke);
        stroke.setStrokeWidth(3f); stroke.setColor(RED); c.drawPath(supply, stroke);
        if (flowing) {
            stroke.setStrokeWidth(1.5f);
            stroke.setPathEffect(new DashPathEffect(new float[]{5, 19}, 24 - phase));
            // Przepływ tylko w czynnym obiegu: CWU (kocioł → zasobnik → kocioł) albo CO (kocioł → pompa → dom → kocioł)
            stroke.setColor(Color.argb(200, 255, 225, 210)); c.drawPath(supBoiler, stroke);
            if (dhwMode) c.drawPath(supDhw, stroke); else { c.drawPath(supCo, stroke); c.drawPath(ufh, stroke); }
            stroke.setColor(Color.argb(200, 215, 235, 255)); c.drawPath(retBoiler, stroke);
            c.drawPath(dhwMode ? retDhw : retCo, stroke);
            stroke.setPathEffect(null);
        }
    }

    private void drawBoiler(Canvas c) {
        RectF box = new RectF(46, 356, 96, 432);
        fill.setShader(new LinearGradient(box.left, 0, box.right, 0,
            new int[]{Color.rgb(141, 150, 161), Color.rgb(201, 208, 216), Color.rgb(138, 147, 158)}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(box, 4, 4, fill);
        fill.setShader(null);
        fill.setColor(Color.rgb(75, 85, 99)); c.drawRoundRect(new RectF(54, 362, 88, 369), 2, 2, fill);
        // wentylator
        float fx = 71, fy = 386;
        fill.setColor(Color.rgb(51, 65, 85)); c.drawCircle(fx, fy, 9, fill);
        fill.setColor(Color.WHITE);
        for (int i = 0; i < 3; i++) {
            c.save(); c.rotate(fanAngle + i * 120, fx, fy);
            c.drawOval(new RectF(fx - 2.5f, fy - 8, fx + 2.5f, fy - 1.5f), fill);
            c.restore();
        }
        fill.setColor(Color.rgb(51, 65, 85)); c.drawCircle(fx, fy, 2, fill);
        // okienko palnika: płomień, gdy palnik pracuje
        fill.setColor(Color.rgb(31, 41, 55)); c.drawRoundRect(new RectF(58, 402, 84, 426), 2, 2, fill);
        if (burnerOn) {
            float flick = (float) Math.sin(smokeT * 40) * 1.2f;
            fill.setColor(ORANGE);
            tmp.reset(); tmp.moveTo(71, 424); tmp.cubicTo(62, 418, 68, 412, 71 + flick, 406); tmp.cubicTo(73, 412, 80, 416, 71, 424); tmp.close(); c.drawPath(tmp, fill);
            fill.setColor(Color.rgb(253, 224, 71));
            tmp.reset(); tmp.moveTo(71, 424); tmp.cubicTo(67, 420, 70, 417, 71, 414); tmp.cubicTo(72, 417, 75, 420, 71, 424); tmp.close(); c.drawPath(tmp, fill);
        }
    }

    private void drawTank(Canvas c) {
        RectF body = new RectF(232, 384, 266, 440);
        fill.setShader(new LinearGradient(body.left, 0, body.right, 0,
            new int[]{Color.rgb(207, 213, 220), Color.WHITE, Color.rgb(191, 198, 206)}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(body, 10, 10, fill);
        fill.setShader(null);
        stroke.setPathEffect(null); stroke.setColor(Color.rgb(170, 180, 192)); stroke.setStrokeWidth(1); c.drawRoundRect(body, 10, 10, stroke);
    }

    /** Dym z komina, gdy palnik pracuje: kłęby unoszą się, rosną i znikają. */
    private void drawSmoke(Canvas c) {
        for (int k = 0; k < 4; k++) {
            float f = (smokeT + k / 4f) % 1;
            float y = 28 - f * 26;
            float x = 86 + (float) Math.sin(f * 6 + k) * 2.5f + f * 6;
            float r = 2.5f + f * 5.5f;
            float a = Math.min(1, f / 0.12f) * (1 - f);
            fill.setColor(Color.argb(Math.round(170 * a), 160, 166, 176));
            c.drawCircle(x, y, r, fill);
        }
    }

    /** Ikona stanu kotła: zielony znaczek = wszystko OK, żółty/czerwony wykrzyknik = ostrzeżenie/alarm. */
    private void drawHealth(Canvas c) {
        int col = health == HEALTH_OK ? Color.rgb(34, 197, 94) : health == HEALTH_WARN ? ORANGE
            : health == HEALTH_ALARM ? Color.rgb(214, 64, 69) : Color.rgb(148, 163, 184);
        fill.setShader(null);
        fill.setColor(Color.argb(60, 0, 0, 0)); c.drawCircle(HX, HY + 1.5f, HR, fill);
        fill.setColor(col); c.drawCircle(HX, HY, HR, fill);
        stroke.setPathEffect(null); stroke.setColor(Color.WHITE); stroke.setStrokeWidth(2);
        if (health == HEALTH_OK) {
            tmp.reset(); tmp.moveTo(HX - 3.6f, HY + 0.3f); tmp.lineTo(HX - 1, HY + 2.9f); tmp.lineTo(HX + 3.9f, HY - 2.9f);
            c.drawPath(tmp, stroke);
        } else if (health == HEALTH_UNKNOWN) {
            fill.setColor(Color.WHITE); c.drawCircle(HX, HY, 1.8f, fill);
        } else {
            c.drawLine(HX, HY - 4, HX, HY + 1, stroke);
            fill.setColor(Color.WHITE); c.drawCircle(HX, HY + 3.8f, 1.3f, fill);
        }
    }

    private void drawPump(Canvas c, float x, float y) {
        fill.setColor(Color.WHITE); c.drawCircle(x, y, 8, fill);
        stroke.setColor(Color.rgb(17, 17, 17)); stroke.setStrokeWidth(1.5f); c.drawCircle(x, y, 8, stroke);
        tmp.reset(); tmp.moveTo(x - 3, y - 5); tmp.lineTo(x + 5, y); tmp.lineTo(x - 3, y + 5); tmp.close();
        fill.setColor(Color.rgb(17, 17, 17)); c.drawPath(tmp, fill);
    }

    private void drawValve(Canvas c, float x, float y) {
        fill.setColor(Color.rgb(71, 85, 105));
        tmp.reset();
        tmp.moveTo(x - 9, y - 6); tmp.lineTo(x, y); tmp.lineTo(x - 9, y + 6); tmp.close();
        tmp.moveTo(x + 9, y - 6); tmp.lineTo(x, y); tmp.lineTo(x + 9, y + 6); tmp.close();
        c.drawPath(tmp, fill);
    }

    /** Termometr (temperatura teraz). */
    private void thermo(Canvas c, float x, float y) {
        stroke.setPathEffect(null); stroke.setColor(RED); stroke.setStrokeWidth(2); c.drawLine(x, y - 8, x, y - 1, stroke);
        fill.setColor(RED); c.drawCircle(x, y, 2.6f, fill);
    }

    /** Ikona temperatury zadanej. */
    private void setIcon(Canvas c, float x, float y) {
        fill.setColor(setColor); c.drawRoundRect(new RectF(x - 4, y - 7.5f, x + 4.5f, y + 1), 2, 2, fill);
        stroke.setColor(Color.WHITE); stroke.setStrokeWidth(1.2f);
        c.drawArc(new RectF(x - 2.3f, y - 5.6f, x + 2.7f, y - 0.6f), -60, 300, false, stroke);
    }

    private void drawRoom(Canvas c, Room r) {
        float left = r.cx - 23, top = r.top - 10;
        boolean two = r.set != null;
        RectF frame = new RectF(left, top, r.cx + 23, top + (two ? 44 : 30));
        r.hit.set(frame);
        if (r.gold) { stroke.setPathEffect(null); stroke.setColor(GOLD); stroke.setStrokeWidth(1.2f); c.drawRoundRect(frame, 7, 7, stroke); }
        text(c, fit(r.name, 44, 8.5f), r.cx, r.top, 8.5f, muted, false, Paint.Align.CENTER);
        thermo(c, r.cx - 17, r.top + 13);
        text(c, r.temp, r.cx - 11, r.top + 14, 12, text, true, Paint.Align.LEFT);
        if (two) {
            setIcon(c, r.cx - 17, r.top + 28);
            text(c, r.set, r.cx - 11, r.top + 28, 12, setColor, true, Paint.Align.LEFT);
        }
    }

    /** Skraca tekst z „…”, aby zmieścił się w szerokości. */
    private String fit(String s, float max, float size) {
        txt.setTextSize(size);
        if (txt.measureText(s) <= max) return s;
        while (s.length() > 1 && txt.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private void drawLabel(Canvas c, Label l) {
        if (l.kind == PAIR_NOW || l.kind == PAIR_SET) {
            // para wartości bez ramki, jak przy pomieszczeniach
            String t = l.text.replace(" °C", "°");
            if (l.kind == PAIR_NOW) thermo(c, l.cx, l.cy); else setIcon(c, l.cx, l.cy);
            text(c, t, l.cx + 6, l.cy + 1, 12, l.kind == PAIR_NOW ? text : setColor, true, Paint.Align.LEFT);
            float w = txt.measureText(t);
            l.hit.set(l.cx - 5, l.cy - 11, l.cx + 8 + w, l.cy + 4);
            return;
        }
        txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        txt.setTextSize(10); txt.setTextAlign(Paint.Align.CENTER);
        float w = Math.max(34, txt.measureText(l.text) + 12), h = 18;
        RectF r = new RectF(l.cx - w / 2, l.cy - h / 2, l.cx + w / 2, l.cy + h / 2);
        if (r.left < 2) r.offset(2 - r.left, 0);
        if (r.right > W - 2) r.offset(W - 2 - r.right, 0);
        l.hit.set(r);
        int border, tc, bg = Color.rgb(24, 34, 54);
        switch (l.kind) {
            case HOT: border = ORANGE; tc = Color.rgb(253, 186, 116); break;
            case COLD: border = SKY; tc = Color.rgb(147, 207, 250); break;
            case STATUS:
                bg = burnerOn ? Color.rgb(154, 63, 20) : Color.rgb(71, 85, 105);
                border = burnerOn ? ORANGE : Color.rgb(148, 163, 184);
                tc = burnerOn ? Color.rgb(254, 215, 170) : Color.rgb(226, 232, 240); break;
            default: border = SKY; tc = Color.WHITE;
        }
        fill.setShader(null); fill.setColor(bg); c.drawRoundRect(r, 6, 6, fill);
        stroke.setPathEffect(null); stroke.setColor(border); stroke.setStrokeWidth(1.2f);
        c.drawRoundRect(r, 6, 6, stroke);
        txt.setColor(tc);
        Paint.FontMetrics fm = txt.getFontMetrics();
        c.drawText(l.text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, txt);
    }
}
