package pl.mojdom.energia;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class MainActivity extends Activity {
    // Paleta inspirowana aplikacjami producentów kotłów: jasne tło, białe karty, morski akcent
    private static final int BG=Color.rgb(242,245,247), CARD=Color.WHITE, TEXT=Color.rgb(28,43,51), MUTED=Color.rgb(110,124,135),
        GREEN=Color.rgb(0,145,126), GREEN_SOFT=Color.rgb(217,240,236), LINE=Color.rgb(226,232,236),
        ORANGE=Color.rgb(232,119,46), BLUE=Color.rgb(47,111,222), PURPLE=Color.rgb(124,77,190), RED=Color.rgb(214,64,69);
    // Default IDs follow the user's supplied HA configuration; all can be edited.
    // UWAGA: kolejność musi zostać zachowana (ustawienia zapisane są jako entity_<indeks>).
    private final String[][] metrics={
        {"Gaz", "Gaz chwilowo", "sensor.ebus_zuzycie_gazu_chwilowe_szacowane"},
        {"Gaz", "Gaz dzisiaj", "sensor.ebus_szacowane_zuzycie_gazu_dzienne"},
        {"Gaz", "Gaz w miesiącu", "sensor.ebus_szacowane_zuzycie_gazu_miesieczne"},
        {"Gaz", "Koszt gazu w miesiącu", "sensor.ebus_szacowany_koszt_gazu_miesieczny"},
        {"Prąd", "Zużycie w miesiącu", "sensor.tauron_amiplus_590322426500205175_zuzycie_w_obecnym_miesiacu"},
        {"Prąd", "Cena energii", "sensor.cena_energii"},
        {"Prąd", "Koszt w miesiącu", "sensor.koszt_energii_miesieczny"},
        {"Prąd", "Koszt w roku", "sensor.koszt_energii_roczny"},
        {"Kocioł", "Palnik", "binary_sensor.palnik"},
        {"Kocioł", "Modulacja", "sensor.ebus_modulacja_kotla"},
        {"Kocioł", "Zasilanie", "sensor.ebusd_bai_status01_temp"},
        {"Kocioł", "Powrót", "sensor.ebusd_bai_status01_temp_1"},
        {"Kocioł", "Ciśnienie", "sensor.cisnienie_instalacji"},
        {"Kocioł", "Krzywa grzewcza", "sensor.krzywa_grzewcza"},
        {"Kocioł", "Temperatura w domu", "sensor.heating_ebusd_e7c_temperatura_na_korytarzu"},
        {"Kocioł", "Temperatura zadana", "input_number.e7c_temp_set"},
        {"Kocioł", "Temperatura zewnętrzna", "sensor.ebusd_broadcast_outsidetemp"},
        {"Kocioł", "Starty palnika / godz.", "sensor.ebus_starty_palnika_ostatnia_godzina"},
        {"Serwis", "Termin serwisu", "sensor.e7c_termin_serwisu"},
        {"Serwis", "Pozostało dni", "sensor.e7c_pozostalo_dni_do_serwisu"},
        {"Serwis", "Serwis wymagany", "binary_sensor.e7c_serwis_wymagany"},
        {"Serwis", "Stan techniczny — problem", "binary_sensor.ebus_stan_techniczny_kotla"},
        {"Kocioł", "Moc kotła", ""},
        {"Kocioł", "Pompa", ""},
        {"Kocioł", "Obroty wentylatora", ""},
        {"Ukryte", "Pompa — wysterowanie (scalone z „Pompa”)", ""},
        {"Kocioł", "Zawór 3-drogowy", ""},
        {"Kocioł", "Temperatura CWU (zasobnik)", ""}
    };
    private static final int M_GAS_TODAY=1,M_POWER=22,M_BURNER=8,M_FLOW=10,M_RETURN=11,M_HOUSE=14,M_SET=15,M_OUTSIDE=16,M_FAN=24,M_PUMP=23,M_PUMP_OLD=25,M_VALVE=26,M_DHW=27;
    // Metryki pokazane na schemacie — nie powtarzamy ich w kafelkach
    private static final Set<Integer> ON_DIAGRAM=new HashSet<>(Arrays.asList(M_BURNER,M_POWER,M_FLOW,M_FAN,M_PUMP,M_VALVE,M_RETURN,M_HOUSE,M_OUTSIDE,M_DHW));
    private static final String[] TABS={"Dom","Gaz","Prąd","Kocioł","Serwis"};
    private static final int[] TAB_ICONS={Icon.HOME,Icon.FLAME,Icon.BOLT,Icon.BOILER,Icon.WRENCH};
    private BoilerDiagram diagram;private TextView gasToday;private Ring ring;private TextView heroBurner,heroOutside;private Icon heroBurnerIcon;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Map<Integer,TextView> values=new HashMap<>(), details=new HashMap<>();
    private final Map<String,JSONObject> states=new HashMap<>();
    private android.content.SharedPreferences prefs;
    private TokenStore secrets;
    private String base="",token="",tab="Dom";
    private boolean active=false,busy=false,configured=false,settingsOpen=false;
    private int generation=0;
    private TextView connection;private View statusDot;
    private final Map<String,TextView[]> roomViews=new LinkedHashMap<>();
    private final Map<String,View[]> tabButtons=new HashMap<>();
    private LinearLayout root,body;private ScrollView scroll;
    private String lastSuccess="";
    private final Runnable ticker=new Runnable(){public void run(){if(active){refresh();handler.postDelayed(this,5000);}}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(CARD);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        prefs=getSharedPreferences("settings",0);secrets=new TokenStore(this);
        migrate();
        base=prefs.getString("base","");
        tab=prefs.getString("tab","Dom");if(b!=null)tab=b.getString("tab",tab);
        if(!Arrays.asList(TABS).contains(tab))tab="Dom";
        try {token=secrets.read();}catch(Exception e){token="";}
        configured=!base.isEmpty()&&!token.isEmpty();
        dashboard();if(!configured)handler.post(this::settings);
    }
    /** Jednorazowe przeniesienie ustawień ze starszych wersji (bez utraty danych). */
    private void migrate(){
        if(prefs.getInt("schema",1)>=2)return;
        android.content.SharedPreferences.Editor e=prefs.edit();
        String oldPump=prefs.getString("entity_"+M_PUMP_OLD,"").trim();
        if(!oldPump.isEmpty()&&prefs.getString("entity_"+M_PUMP,"").trim().isEmpty())e.putString("entity_"+M_PUMP,oldPump);
        e.putInt("schema",2).apply();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("tab",tab);}
    @Override public void onResume(){super.onResume();active=true;handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override public void onPause(){active=false;handler.removeCallbacks(ticker);super.onPause();}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String t,int size,int color){TextView v=new TextView(this);v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));v.setText(t);v.setTextSize(size);v.setTextColor(color);return v;}
    private TextView bold(String t,int size,int color){TextView v=text(t,size,color);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable shape(int color){return shape(color,20);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int alpha(int color,int a){return Color.argb(a,Color.red(color),Color.green(color),Color.blue(color));}
    private Button button(String title,Runnable r){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(TEXT);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CARD));b.setOnClickListener(v->r.run());return b;}
    private String entity(int i){return prefs.getString("entity_"+i,metrics[i][2]).trim();}
    /** Kółko z ikoną w kolorze akcentu. */
    private View badge(int icon,int color,int size){
        FrameLayout f=new FrameLayout(this);f.setBackground(shape(alpha(color,30),size/2));
        Icon i=new Icon(this,icon,color);int pad=dp(size*0.22f);f.setPadding(pad,pad,pad,pad);f.addView(i,new FrameLayout.LayoutParams(-1,-1));
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return f;
    }
    private LinearLayout card(){LinearLayout c=column();c.setBackground(shape(CARD));c.setElevation(dp(1.5f));c.setPadding(dp(16),dp(16),dp(16),dp(16));return c;}
    private void addFull(LinearLayout parent,View v,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(bottom);parent.addView(v,lp);}
    private TextView section(String title){TextView t=bold(title,17,TEXT);t.setPadding(dp(4),dp(10),0,dp(10));return t;}

    private void dashboard(){
        settingsOpen=false;values.clear();details.clear();tabButtons.clear();
        root=column();root.setBackgroundColor(BG);
        LinearLayout top=column();top.setPadding(dp(20),dp(12),dp(20),dp(6));
        LinearLayout nav=row();nav.setBackgroundColor(CARD);nav.setElevation(dp(10));nav.setPadding(dp(6),dp(8),dp(6),dp(8));
        root.setOnApplyWindowInsetsListener((v,insets)->{top.setPadding(dp(20),dp(12)+insets.getSystemWindowInsetTop(),dp(20),dp(6));nav.setPadding(dp(6),dp(8),dp(6),dp(8)+insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);root.requestApplyInsets();
        // Nagłówek
        LinearLayout header=row();
        LinearLayout titles=column();
        String day=LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM",new Locale("pl","PL")));
        titles.addView(text(day.substring(0,1).toUpperCase(new Locale("pl","PL"))+day.substring(1),13,MUTED));
        TextView title=text("Mój Dom",28,TEXT);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));titles.addView(title);
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout gear=new FrameLayout(this);gear.setBackground(shape(CARD,22));gear.setElevation(dp(1.5f));int gp=dp(10);gear.setPadding(gp,gp,gp,gp);
        gear.addView(new Icon(this,Icon.GEAR,TEXT),new FrameLayout.LayoutParams(-1,-1));gear.setOnClickListener(v->settings());gear.setContentDescription("Ustawienia");
        header.addView(gear,new LinearLayout.LayoutParams(dp(44),dp(44)));
        top.addView(header);
        // Status połączenia
        LinearLayout chip=row();chip.setBackground(shape(CARD,16));chip.setPadding(dp(12),dp(6),dp(14),dp(6));
        statusDot=new View(this);statusDot.setBackground(shape(MUTED,4));chip.addView(statusDot,new LinearLayout.LayoutParams(dp(8),dp(8)));
        connection=text(configured?"Łączenie…":"Ustaw połączenie z Home Assistant",12,MUTED);connection.setPadding(dp(8),0,0,0);chip.addView(connection);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.topMargin=dp(10);top.addView(chip,cp);
        root.addView(top);
        scroll=new ScrollView(this);scroll.setClipToPadding(false);body=column();body.setPadding(dp(16),dp(8),dp(16),dp(20));scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        // Dolna nawigacja
        for(int k=0;k<TABS.length;k++){
            String name=TABS[k];LinearLayout item=column();item.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout pill=new FrameLayout(this);pill.setPadding(dp(18),dp(4),dp(18),dp(4));
            Icon icon=new Icon(this,TAB_ICONS[k],MUTED);pill.addView(icon,new FrameLayout.LayoutParams(dp(22),dp(22)));
            item.addView(pill,new LinearLayout.LayoutParams(-2,-2));
            TextView label=text(name,11,MUTED);label.setPadding(0,dp(3),0,0);item.addView(label);
            item.setOnClickListener(v->{if(name.equals(tab))return;tab=name;prefs.edit().putString("tab",name).apply();render();scroll.scrollTo(0,0);});
            tabButtons.put(name,new View[]{pill,icon,label});
            nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        render();
    }
    private int accentFor(String t){return t.equals("Gaz")?ORANGE:t.equals("Prąd")?BLUE:t.equals("Serwis")?PURPLE:GREEN;}
    private int iconFor(int i){
        String n=metrics[i][1].toLowerCase(Locale.ROOT);
        if(n.contains("koszt")||n.contains("cena"))return Icon.COIN;
        if(n.contains("temperatura")||n.contains("zasilanie")||n.contains("powrót")||n.contains("krzywa"))return Icon.THERMO;
        if(n.contains("ciśnienie")||n.contains("modulacja"))return Icon.GAUGE;
        if(n.contains("starty"))return Icon.FLAME;
        if(n.contains("termin"))return Icon.CALENDAR;
        if(n.contains("dni"))return Icon.CLOCK;
        if(n.contains("problem"))return Icon.ALERT;
        if(n.contains("serwis"))return Icon.WRENCH;
        if(metrics[i][0].equals("Gaz"))return Icon.FLAME;
        if(metrics[i][0].equals("Prąd"))return n.contains("zużycie")?Icon.CHART:Icon.BOLT;
        return Icon.CHART;
    }
    private void render(){
        if(body==null)return;body.removeAllViews();values.clear();details.clear();roomViews.clear();diagram=null;gasToday=null;ring=null;heroBurner=null;heroOutside=null;heroBurnerIcon=null;
        for(String name:tabButtons.keySet()){View[] v=tabButtons.get(name);boolean sel=name.equals(tab);
            v[0].setBackground(sel?shape(GREEN_SOFT,16):null);((Icon)v[1]).set(TAB_ICONS[Arrays.asList(TABS).indexOf(name)],sel?GREEN:MUTED);
            ((TextView)v[2]).setTextColor(sel?GREEN:MUTED);((TextView)v[2]).setTypeface(Typeface.create(sel?"sans-serif-medium":"sans-serif",Typeface.NORMAL));}
        if(tab.equals("Dom")){renderRooms();updateCards();return;}
        int accent=accentFor(tab);
        ArrayList<Integer> list=new ArrayList<>();
        for(int i=0;i<metrics.length;i++)if(metrics[i][0].equals(tab)&&!(tab.equals("Kocioł")&&ON_DIAGRAM.contains(i)))list.add(i);
        if(tab.equals("Kocioł")){
            renderDiagram();
            body.addView(section("Parametry"));
        }else{
            int hero=tab.equals("Gaz")?M_GAS_TODAY:tab.equals("Prąd")?4:19;
            list.remove(Integer.valueOf(hero));
            heroCard(hero,accent);
            body.addView(section(tab.equals("Serwis")?"Stan urządzenia":"Szczegóły"));
        }
        grid(list,accent);
        String note=tab.equals("Gaz")?"Zużycie szacowane z pracy kotła.":tab.equals("Prąd")?"Dane Taurona mogą być opóźnione.":tab.equals("Serwis")?"Ta wersja nie wysyła powiadomień w tle.":"Aplikacja tylko odczytuje dane — nie zmienia nastaw kotła.";
        TextView n=text(note+" Dotknij kafelka, aby zobaczyć historię 24 h; przytrzymaj, aby wybrać czujnik.",12,MUTED);n.setPadding(dp(4),dp(4),dp(4),0);body.addView(n);
        updateCards();
    }
    private void bindMetric(View v,int index){
        v.setOnClickListener(x->{if(entity(index).isEmpty())chooseMetric(index);else history(index);});
        v.setOnLongClickListener(x->{chooseMetric(index);return true;});
    }
    private void heroCard(int i,int accent){
        LinearLayout c=card();c.setPadding(dp(20),dp(20),dp(20),dp(20));
        LinearLayout head=row();head.addView(badge(iconFor(i),accent,40));
        TextView l=bold(metrics[i][1],15,TEXT);l.setPadding(dp(12),0,0,0);head.addView(l);c.addView(head);
        TextView val=text("—",40,TEXT);val.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));val.setPadding(0,dp(14),0,dp(2));c.addView(val);values.put(i,val);
        TextView sub=text("Oczekiwanie na dane",12,MUTED);c.addView(sub);details.put(i,sub);
        bindMetric(c,i);addFull(body,c,6);
    }
    private void grid(ArrayList<Integer> list,int accent){
        LinearLayout line=null;
        for(int k=0;k<list.size();k++){
            if(k%2==0){line=row();line.setGravity(Gravity.TOP);addFull(body,line,12);}
            int i=list.get(k);
            LinearLayout c=card();c.setMinimumHeight(dp(132));
            c.addView(badge(iconFor(i),accent,34));
            TextView l=text(metrics[i][1],13,MUTED);l.setPadding(0,dp(10),0,0);l.setMaxLines(2);c.addView(l);
            TextView val=bold("—",22,TEXT);val.setPadding(0,dp(2),0,0);val.setMaxLines(2);c.addView(val);values.put(i,val);
            TextView sub=text("",11,MUTED);sub.setMaxLines(2);c.addView(sub);details.put(i,sub);
            bindMetric(c,i);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(k%2==0)lp.rightMargin=dp(6);else lp.leftMargin=dp(6);line.addView(c,lp);
        }
        if(list.size()%2==1&&line!=null)line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
    }
    private void renderDiagram(){
        LinearLayout frame=card();frame.setPadding(dp(10),dp(14),dp(10),dp(12));
        LinearLayout head=row();head.setPadding(dp(8),0,dp(8),dp(6));
        TextView t=bold("Instalacja",17,TEXT);head.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        head.addView(text("Dotknij wartości",12,MUTED));
        frame.addView(head);
        diagram=new BoilerDiagram(this,M_BURNER,M_POWER,M_FLOW,M_FAN,M_PUMP,M_VALVE,M_RETURN,M_HOUSE,M_OUTSIDE,M_DHW);
        diagram.setListener(new BoilerDiagram.Listener(){
            public void onTap(int i){if(entity(i).isEmpty())chooseMetric(i);else history(i);}
            public void onLongPress(int i){chooseMetric(i);}
        });
        frame.addView(diagram,new LinearLayout.LayoutParams(-1,-2));
        addFull(body,frame,12);
        // Zużycie gazu dziś pod schematem
        LinearLayout gas=card();gas.setOrientation(LinearLayout.HORIZONTAL);gas.setGravity(Gravity.CENTER_VERTICAL);
        gas.addView(badge(Icon.FLAME,ORANGE,40));
        LinearLayout gt=column();gt.setPadding(dp(12),0,0,0);gt.addView(bold("Zużycie gazu dziś",15,TEXT));gt.addView(text("Szacowane z pracy kotła",12,MUTED));
        gas.addView(gt,new LinearLayout.LayoutParams(0,-2,1));
        gasToday=text("—",24,ORANGE);gasToday.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));gas.addView(gasToday);
        bindMetric(gas,M_GAS_TODAY);
        addFull(body,gas,6);
    }
    /** Krótki tekst wartości do etykiety na schemacie. */
    private String shortValue(int i){return value(i,true);}
    private String value(int i,boolean compact){
        if(entity(i).isEmpty())return compact?"wybierz":"Wybierz czujnik";
        JSONObject s=states.get(entity(i));if(s==null)return "—";
        String state=s.optString("state","");
        if(state.equals("unknown")||state.equals("unavailable")||state.isEmpty())return "—";
        if(i==M_BURNER)return state.equals("on")?"działa":state.equals("off")?"postój":state;
        if(state.equals("on"))return compact?"wł.":"Tak";if(state.equals("off"))return compact?"wył.":"Nie";
        JSONObject attrs=s.optJSONObject("attributes");String unit=attrs==null?"":attrs.optString("unit_of_measurement","");
        try{double v=Double.parseDouble(state);if(unit.contains("m³")||unit.contains("m3"))return String.format(new Locale("pl","PL"),"%.2f",v)+" "+unit;
            boolean integer=unit.equals("%")||unit.equalsIgnoreCase("rpm")||unit.equalsIgnoreCase("obr/min")||unit.equalsIgnoreCase("d")||Math.abs(v)>=1000||(unit.isEmpty()&&v==Math.rint(v));
            boolean money=unit.contains("zł")||unit.contains("PLN");
            String num=String.format(new Locale("pl","PL"),money?"%.2f":integer?"%.0f":"%.1f",v);
            return num+(unit.isEmpty()?"":(unit.equals("%")?" %":" "+unit));
        }catch(NumberFormatException e){
            try{return OffsetDateTime.parse(state).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d.MM.yyyy"));}catch(Exception ignored){}
            try{return LocalDate.parse(state).format(DateTimeFormatter.ofPattern("d.MM.yyyy"));}catch(Exception ignored){}
            return compact&&state.length()>12?state.substring(0,12)+"…":state;}
    }
    private double number(int i){
        JSONObject s=entity(i).isEmpty()?null:states.get(entity(i));
        try{return s==null?0:Double.parseDouble(s.optString("state",""));}catch(Exception e){return 0;}
    }
    private boolean burnerOn(){JSONObject b=entity(M_BURNER).isEmpty()?null:states.get(entity(M_BURNER));return b!=null&&b.optString("state").equals("on");}
    private void updateDiagram(){
        if(diagram==null)return;
        if(gasToday!=null)gasToday.setText(shortValue(M_GAS_TODAY));
        for(int i:ON_DIAGRAM)diagram.setValue(i,shortValue(i));
        boolean burner=burnerOn();
        diagram.setState(burner,burner||number(M_PUMP)>0||number(M_FAN)>0);
    }
    private void updateCards(){
        updateRooms();updateDiagram();updateHero();
        for(Integer i:values.keySet()){
            TextView val=values.get(i),sub=details.get(i);val.setTextColor(TEXT);
            if(entity(i).isEmpty()){val.setText("Wybierz czujnik");val.setTextColor(GREEN);sub.setText("Dotknij, aby połączyć");continue;}
            JSONObject s=states.get(entity(i));String state=s==null?"":s.optString("state","");
            if(s==null||state.equals("unknown")||state.equals("unavailable")){val.setText("—");sub.setText(s==null?(states.isEmpty()?"Oczekiwanie na dane":"Brak encji — sprawdź identyfikator"):"Czujnik niedostępny");continue;}
            val.setText(value(i,false));
            // Statusy serwisowe: „on” oznacza problem lub potrzebę serwisu
            if(entity(i).startsWith("binary_sensor.")&&metrics[i][0].equals("Serwis"))val.setTextColor(state.equals("on")?RED:GREEN);
            String date=s.optString("last_updated","");
            try{date=OffsetDateTime.parse(date).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM, HH:mm"));}catch(Exception ignored){}
            sub.setText("Aktualizacja "+date);
        }
    }

    private void chooseMetric(int index){
        if(states.isEmpty()){Toast.makeText(this,"Poczekaj na połączenie z Home Assistant",Toast.LENGTH_LONG).show();return;}
        LinearLayout form=column();form.setPadding(dp(16),dp(8),dp(16),0);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Szukaj: moc, power, pompa…");form.addView(search);
        ListView list=new ListView(this);form.addView(list,new LinearLayout.LayoutParams(-1,dp(320)));
        ArrayList<String> ids=new ArrayList<>();for(String id:states.keySet())if(id.startsWith("sensor.")||id.startsWith("binary_sensor.")||id.startsWith("number.")||id.startsWith("input_number.")||id.startsWith("input_boolean.")||id.startsWith("switch."))ids.add(id);
        Collections.sort(ids);ArrayList<String> shown=new ArrayList<>();
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>());list.setAdapter(adapter);
        Runnable filter=()->{String q=search.getText().toString().toLowerCase(Locale.ROOT);shown.clear();adapter.clear();
            for(String id:ids){JSONObject state=states.get(id),attrs=state.optJSONObject("attributes");String label=(attrs==null?id:attrs.optString("friendly_name",id))+" • "+state.optString("state")+" "+(attrs==null?"":attrs.optString("unit_of_measurement",""))+" — "+id;
                if(label.toLowerCase(Locale.ROOT).contains(q)){shown.add(id);adapter.add(label);}}
        };filter.run();
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(android.text.Editable e){}});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(metrics[index][1]+" — wybierz czujnik").setView(form).setNegativeButton("Anuluj",null).create();
        list.setOnItemClickListener((parent,view,position,id)->{prefs.edit().putString("entity_"+index,shown.get(position)).apply();dialog.dismiss();render();});dialog.show();
    }

    private Set<String> selectedRooms(){
        if(prefs.contains("rooms"))return new TreeSet<>(prefs.getStringSet("rooms",Collections.emptySet()));
        return new TreeSet<>(Collections.singleton(entity(14)));
    }
    private String roomName(String id){
        String custom=prefs.getString("room_name_"+id,"").trim();
        if(!custom.isEmpty())return custom;
        if(id.equals("sensor.heating_ebusd_e7c_temperatura_na_korytarzu"))return "Korytarz";
        JSONObject state=states.get(id), attrs=state==null?null:state.optJSONObject("attributes");
        return attrs==null?id:attrs.optString("friendly_name",id);
    }
    private void renderRooms(){
        // Główny wskaźnik: temperatura w domu, zadana i zewnętrzna
        LinearLayout hero=card();hero.setPadding(dp(16),dp(18),dp(16),dp(18));hero.setGravity(Gravity.CENTER_HORIZONTAL);
        ring=new Ring(this,GREEN,Color.rgb(232,238,241),TEXT,MUTED);hero.addView(ring,new LinearLayout.LayoutParams(-1,-2));
        ring.setOnClickListener(v->{if(entity(M_HOUSE).isEmpty())chooseMetric(M_HOUSE);else history(M_HOUSE);});
        LinearLayout chips=row();chips.setGravity(Gravity.CENTER);chips.setPadding(0,dp(6),0,0);
        LinearLayout c1=row();c1.setBackground(shape(BG,14));c1.setPadding(dp(10),dp(6),dp(12),dp(6));
        c1.addView(new Icon(this,Icon.THERMO,BLUE),new LinearLayout.LayoutParams(dp(16),dp(16)));
        heroOutside=text("Na zewnątrz —",13,TEXT);heroOutside.setPadding(dp(6),0,0,0);c1.addView(heroOutside);
        c1.setOnClickListener(v->{if(entity(M_OUTSIDE).isEmpty())chooseMetric(M_OUTSIDE);else history(M_OUTSIDE);});
        chips.addView(c1);
        LinearLayout c2=row();c2.setBackground(shape(BG,14));c2.setPadding(dp(10),dp(6),dp(12),dp(6));
        heroBurnerIcon=new Icon(this,Icon.FLAME,MUTED);c2.addView(heroBurnerIcon,new LinearLayout.LayoutParams(dp(16),dp(16)));
        heroBurner=text("Palnik —",13,TEXT);heroBurner.setPadding(dp(6),0,0,0);c2.addView(heroBurner);
        c2.setOnClickListener(v->{tab="Kocioł";prefs.edit().putString("tab",tab).apply();render();});
        LinearLayout.LayoutParams c2p=new LinearLayout.LayoutParams(-2,-2);c2p.leftMargin=dp(8);chips.addView(c2,c2p);
        hero.addView(chips);
        addFull(body,hero,6);
        // Pomieszczenia
        LinearLayout sh=row();TextView st=section("Pomieszczenia");sh.addView(st,new LinearLayout.LayoutParams(0,-2,1));
        TextView edit=bold("Edytuj",14,GREEN);edit.setPadding(dp(12),dp(10),dp(6),dp(10));edit.setOnClickListener(v->chooseRooms());sh.addView(edit);
        body.addView(sh);
        LinearLayout line=null;int k=0;
        for(String id:selectedRooms()){
            if(k%2==0){line=row();line.setGravity(Gravity.TOP);addFull(body,line,12);}
            LinearLayout c=card();c.setMinimumHeight(dp(124));
            c.addView(badge(id.startsWith("climate.")?Icon.GAUGE:Icon.HOME,GREEN,34));
            TextView name=text(roomName(id),13,MUTED);name.setPadding(0,dp(10),0,0);name.setMaxLines(2);c.addView(name);
            TextView value=text("—",26,TEXT);value.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));c.addView(value);
            TextView updated=text("Oczekiwanie na dane",11,MUTED);c.addView(updated);
            roomViews.put(id,new TextView[]{name,value,updated});
            c.setOnClickListener(v->history(id,roomName(id)));
            c.setOnLongClickListener(v->{EditText label=new EditText(this);label.setText(roomName(id));label.setSingleLine(true);
                new AlertDialog.Builder(this).setTitle("Nazwa pomieszczenia").setView(label).setPositiveButton("Zapisz",(d,w)->{prefs.edit().putString("room_name_"+id,label.getText().toString().trim()).apply();render();}).setNegativeButton("Anuluj",null).show();return true;});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(k%2==0)lp.rightMargin=dp(6);else lp.leftMargin=dp(6);line.addView(c,lp);k++;
        }
        if(k%2==1&&line!=null)line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        if(roomViews.isEmpty())body.addView(text("Dodaj czujniki przyciskiem „Edytuj”.",15,MUTED));
        TextView help=text("Dotknij kafelka, aby zobaczyć historię 24 h. Przytrzymaj, aby zmienić nazwę.",12,MUTED);help.setPadding(dp(4),dp(4),dp(4),0);body.addView(help);
    }
    private void updateHero(){
        if(ring==null)return;
        Locale pl=new Locale("pl","PL");
        String house=getTemperature(entity(M_HOUSE));double t=Double.NaN,set=Double.NaN;
        try{t=Double.parseDouble(house);}catch(Exception ignored){}
        try{set=Double.parseDouble(entity(M_SET).isEmpty()||states.get(entity(M_SET))==null?"":states.get(entity(M_SET)).optString("state"));}catch(Exception ignored){}
        String value=Double.isFinite(t)?String.format(pl,"%.1f°",t):"—";
        String bottom=Double.isFinite(set)?String.format(pl,"Zadana %.1f°C",set):"Temperatura zadana —";
        ring.set(value,"W domu",bottom,Double.isFinite(t)?(float)((t-10)/20):0,Double.isFinite(set)?(float)((set-10)/20):-1);
        heroOutside.setText("Na zewnątrz "+shortValue(M_OUTSIDE));
        boolean burner=burnerOn();heroBurner.setText(burner?"Grzanie":"Palnik w spoczynku");heroBurnerIcon.set(Icon.FLAME,burner?ORANGE:MUTED);
    }
    private void updateRooms(){
        for(String id:roomViews.keySet()){
            TextView[] views=roomViews.get(id);views[0].setText(roomName(id));
            String raw = getTemperature(id);
            double value;try{value=Double.parseDouble(raw);if(!Double.isFinite(value))throw new NumberFormatException();}catch(Exception e){views[1].setText("—");views[2].setText(states.isEmpty()?"Oczekiwanie na dane":states.get(id)==null?"Brak encji w Home Assistant":"Czujnik niedostępny");continue;}
            JSONObject state = states.get(id);
            JSONObject attrs=state.optJSONObject("attributes");String unit=attrs==null?"":attrs.optString("unit_of_measurement","");
            if(unit.isEmpty())unit="°C";
            views[1].setText(String.format(new Locale("pl","PL"),"%.1f",value)+" "+unit);
            String date=state.optString("last_updated","");
            try{date=OffsetDateTime.parse(date).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));}catch(Exception ignored){}
            views[2].setText("Aktualizacja "+date);
        }
    }

    // Nowa metoda pomocnicza do wyciągania temperatury z sensorów i termostatów (climate)
    private String getTemperature(String id) {
        JSONObject s = states.get(id);
        if (s == null) return null;
        if (id.startsWith("sensor.")) {
            return s.optString("state", "");
        }
        if (id.startsWith("climate.")) {
            JSONObject attrs = s.optJSONObject("attributes");
            if (attrs != null && attrs.has("current_temperature")) {
                return attrs.optString("current_temperature");
            }
            return null;
        }
        return s.optString("state", "");
    }

    private void chooseRooms(){
        if(states.isEmpty()){new AlertDialog.Builder(this).setMessage("Najpierw połącz aplikację z Home Assistant i poczekaj na odczyt czujników.").setPositiveButton("OK",null).show();return;}
        Set<String> chosen=selectedRooms();ArrayList<String> ids=new ArrayList<>();
        for(String id:states.keySet()){
            JSONObject state = states.get(id);
            JSONObject attrs = state.optJSONObject("attributes");
            String unit = attrs==null?"":attrs.optString("unit_of_measurement","");
            
            // Zwykłe sensory temperatury
            boolean isTempSensor = id.startsWith("sensor.") && attrs != null && 
                ("temperature".equals(attrs.optString("device_class")) || "°C".equals(unit) || "°F".equals(unit));
            
            // Termostaty (climate), które mają aktualną temperaturę
            boolean isClimate = id.startsWith("climate.") && attrs != null && attrs.has("current_temperature");
            
            if (isTempSensor || isClimate) ids.add(id);
        }
        for(String id:chosen)if(!ids.contains(id))ids.add(id);
        ids.sort((a,b)->roomName(a).compareToIgnoreCase(roomName(b)));
        CharSequence[] labels=new CharSequence[ids.size()];boolean[] checks=new boolean[ids.size()];
        for(int i=0;i<ids.size();i++){String id=ids.get(i);labels[i]=roomName(id)+"\n"+id;checks[i]=chosen.contains(id);}
        new AlertDialog.Builder(this).setTitle("Wybierz temperatury pomieszczeń")
            .setMultiChoiceItems(labels,checks,(d,index,checked)->{if(checked)chosen.add(ids.get(index));else chosen.remove(ids.get(index));})
            .setPositiveButton("Zapisz",(d,w)->{prefs.edit().putStringSet("rooms",new HashSet<>(chosen)).apply();render();})
            .setNegativeButton("Anuluj",null).show();
    }

    private void refresh(){
        if(!configured||busy||settingsOpen)return;busy=true;final int g=generation;final String url=base,t=token;
        worker.execute(()->{
            try{
                JSONArray array=new JSONArray(HaClient.get(url,t,"/api/states"));Map<String,JSONObject> fresh=new HashMap<>();
                for(int i=0;i<array.length();i++){JSONObject s=array.getJSONObject(i);fresh.put(s.optString("entity_id"),s);}
                handler.post(()->{busy=false;if(g!=generation||isDestroyed())return;states.clear();states.putAll(fresh);lastSuccess=java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));connection.setText("Połączono • odczyt "+lastSuccess);connection.setTextColor(TEXT);statusDot.setBackground(shape(GREEN,4));updateCards();});
            }catch(Exception e){final String msg=e.getMessage();handler.post(()->{busy=false;if(g!=generation||isDestroyed())return;connection.setText("Brak nowych danych • "+(lastSuccess.isEmpty()?"":("ostatni odczyt "+lastSuccess+"\n"))+(msg==null?"Sprawdź połączenie":msg));connection.setTextColor(RED);statusDot.setBackground(shape(RED,4));});}
        });
    }
    private EditText field(LinearLayout form,String label,String value,int type){
        TextView l=text(label,14,Color.DKGRAY);l.setPadding(0,dp(10),0,0);form.addView(l);
        EditText e=new EditText(this);e.setText(value);e.setTextColor(Color.BLACK);e.setTextSize(14);e.setSingleLine(true);e.setInputType(type);form.addView(e);return e;
    }
    private void settings(){
        if(settingsOpen||isDestroyed())return;settingsOpen=true;
        ScrollView scroll=new ScrollView(this);LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(12));scroll.addView(form);
        form.addView(text("Adres HA dostępny z telefonu. Poza domem użyj HTTPS lub VPN. Token wpisz wyłącznie tutaj.",14,Color.DKGRAY));
        EditText url=field(form,"Adres Home Assistant",base,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://twoj-adres-home-assistant");
        EditText secret=field(form,"Token długoterminowy — puste pole zachowuje zapisany", "",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox http=new CheckBox(this);http.setText("Zezwalam na HTTP w zaufanej sieci (token nie jest szyfrowany podczas przesyłania)");http.setTextColor(Color.DKGRAY);http.setChecked(prefs.getBoolean("http",false));form.addView(http);
        form.addView(text("Czujniki — popraw identyfikatory, jeśli Twoje encje mają inne nazwy.",14,Color.DKGRAY));
        Map<Integer,EditText> ids=new LinkedHashMap<>();for(int i=0;i<metrics.length;i++)if(i!=M_PUMP_OLD)ids.put(i,field(form,metrics[i][0]+" • "+metrics[i][1],entity(i),InputType.TYPE_CLASS_TEXT));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Połączenie i czujniki").setView(scroll).setPositiveButton("Zapisz",null).setNegativeButton("Anuluj",null).setNeutralButton("Wyloguj",null).create();
        dialog.setOnDismissListener(d->{settingsOpen=false;});dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            String newBase=HaClient.validate(url.getText().toString(),http.isChecked());
            String newToken=secret.getText().toString().trim();
            if(!newBase.equals(base)&&newToken.isEmpty()&&!token.isEmpty())throw new Exception("Przy zmianie adresu wpisz ponownie token dla tego serwera.");
            if(newToken.isEmpty())newToken=token;if(newToken.isEmpty())throw new Exception("Wpisz token dostępu.");
            for(EditText id:ids.values())if(!id.getText().toString().trim().isEmpty()&&!id.getText().toString().trim().matches("[a-z0-9_]+\\.[a-z0-9_]+"))throw new Exception("Identyfikatory muszą mieć postać domena.nazwa, np. sensor.nazwa lub binary_sensor.nazwa.");
            secrets.save(newToken);android.content.SharedPreferences.Editor edit=prefs.edit().putString("base",newBase).putBoolean("http",http.isChecked());
            for(int i:ids.keySet())edit.putString("entity_"+i,ids.get(i).getText().toString().trim());edit.apply();
            base=newBase;token=newToken;configured=true;generation++;states.clear();lastSuccess="";dialog.dismiss();dashboard();refresh();
        }catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("OK",null).show();}});
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Wylogować?")
            .setMessage("Zapisany token zostanie usunięty z telefonu. Adres i wybrane czujniki pozostaną. Aby ponownie się połączyć, trzeba będzie wpisać token.")
            .setPositiveButton("Wyloguj",(d,w)->{secrets.clear();token="";configured=false;generation++;states.clear();lastSuccess="";dialog.dismiss();dashboard();connection.setText("Wylogowano");})
            .setNegativeButton("Anuluj",null).show());
    }
    private void history(int i){
        if(!configured){settings();return;}
        history(entity(i),metrics[i][1]);
    }
    private void history(String id,String label){
        if(!configured){settings();return;}
        final String url=base,t=token;final int g=generation;
        LinearLayout content=column();content.setPadding(dp(16),dp(12),dp(16),dp(16));
        TextView info=text("Pobieranie historii 24 h…",14,Color.DKGRAY);content.addView(info);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(label+" • 24 h").setView(content).setPositiveButton("Zamknij",null).create();dialog.show();
        worker.execute(()->{try{
            Instant from=Instant.now().minusSeconds(86400);
            boolean climate=id.startsWith("climate.");
            // Termostat: temperatura jest w atrybucie current_temperature, więc atrybuty muszą być pobrane
            String path="/api/history/period/"+from.toString()+"?filter_entity_id="+java.net.URLEncoder.encode(id,"UTF-8")+(climate?"&significant_changes_only=0":"&minimal_response&no_attributes");
            JSONArray groups=new JSONArray(HaClient.get(url,t,path));JSONArray rows=groups.length()==0?new JSONArray():groups.getJSONArray(0);
            ArrayList<float[]> points=new ArrayList<>();
            for(int j=0;j<rows.length();j++){JSONObject s=rows.getJSONObject(j);String value=s.optString("state","");
                if(climate){JSONObject a=s.optJSONObject("attributes");value=a==null||a.isNull("current_temperature")?"":a.optString("current_temperature","");}
                try{
                double val=value.equals("on")?1:value.equals("off")?0:Double.parseDouble(value);
                double timestamp=OffsetDateTime.parse(climate?s.optString("last_updated",s.optString("last_changed","")):s.optString("last_changed",s.optString("last_updated",""))).toInstant().toEpochMilli()/1000.;
                if(Double.isFinite(val))points.add(new float[]{(float)(timestamp-from.getEpochSecond()),(float)val});
            }catch(Exception ignored){}}
            handler.post(()->{if(g!=generation||isDestroyed()||!dialog.isShowing())return;
                info.setText(points.isEmpty()?"Brak liczbowej historii. Sprawdź Recorder i identyfikator encji.":"Historia wartości encji. Liczniki dzienne mogą zerować się o północy. on = 1, off = 0.");
                if(!points.isEmpty())content.addView(new HistoryChart(this,points),new LinearLayout.LayoutParams(-1,dp(230)));
            });
        }catch(Exception e){handler.post(()->{if(g==generation&&!isDestroyed()&&dialog.isShowing())info.setText("Nie udało się pobrać historii. "+e.getMessage());});}});
    }
    private static class HistoryChart extends View {
        private final ArrayList<float[]> points;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        HistoryChart(Context c,ArrayList<float[]> p){super(c);points=p;setContentDescription("Wykres historii wartości z ostatnich 24 godzin");}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
            for(float[] p:points){min=Math.min(min,p[1]);max=Math.max(max,p[1]);}if(max==min){max+=1;min-=1;}
            float left=65,top=25,right=getWidth()-15,bottom=getHeight()-40;if(right<=left)return;
            paint.setColor(Color.GRAY);paint.setStrokeWidth(1);canvas.drawLine(left,top,left,bottom,paint);canvas.drawLine(left,bottom,right,bottom,paint);
            paint.setTextSize(24);canvas.drawText(String.format(Locale.getDefault(),"%.1f",max),0,top+8,paint);canvas.drawText(String.format(Locale.getDefault(),"%.1f",min),0,bottom,paint);
            canvas.drawText("−24 h",left,bottom+30,paint);canvas.drawText("teraz",right-65,bottom+30,paint);
            paint.setColor(Color.rgb(0,145,112));paint.setStrokeWidth(4);
            float px=0,py=0;boolean first=true;
            for(float[] p:points){float x=left+Math.max(0,Math.min(86400,p[0]))/86400*(right-left);float y=bottom-(p[1]-min)/(max-min)*(bottom-top);
                if(!first){canvas.drawLine(px,py,x,py,paint);canvas.drawLine(x,py,x,y,paint);}canvas.drawCircle(x,y,3,paint);first=false;px=x;py=y;}
        }
    }
}