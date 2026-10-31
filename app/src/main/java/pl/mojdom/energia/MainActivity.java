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
    private static final int BG=Color.rgb(243,246,250), CARD=Color.WHITE, TEXT=Color.rgb(24,39,58), MUTED=Color.rgb(100,116,139), GREEN=Color.rgb(13,139,118);
    // Default IDs follow the user's supplied HA configuration; all can be edited.
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
        {"Serwis", "Stan techniczny — problem", "binary_sensor.ebus_stan_techniczny_kotla"}
    };
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Map<Integer,TextView> values=new HashMap<>(), details=new HashMap<>();
    private final Map<String,JSONObject> states=new HashMap<>();
    private android.content.SharedPreferences prefs;
    private TokenStore secrets;
    private String base="",token="",tab="Dom";
    private boolean active=false,busy=false,configured=false,settingsOpen=false;
    private int generation=0;
    private TextView connection;
    private final Map<String,TextView[]> roomViews=new LinkedHashMap<>();
    private final Map<String,Button> tabButtons=new HashMap<>();
    private LinearLayout root,body;
    private String lastSuccess="";
    private final Runnable ticker=new Runnable(){public void run(){if(active){refresh();handler.postDelayed(this,5000);}}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("settings",0);secrets=new TokenStore(this);
        base=prefs.getString("base","");
        try {token=secrets.read();}catch(Exception e){token="";}
        configured=!base.isEmpty()&&!token.isEmpty();
        dashboard();if(!configured)handler.post(this::settings);
    }
    @Override public void onResume(){super.onResume();active=true;handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override public void onPause(){active=false;handler.removeCallbacks(ticker);super.onPause();}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String t,int size,int color){TextView v=new TextView(this);v.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));v.setText(t);v.setTextSize(size);v.setTextColor(color);return v;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
    private Button button(String title,Runnable r){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(TEXT);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CARD));b.setOnClickListener(v->r.run());return b;}
    private String entity(int i){return prefs.getString("entity_"+i,metrics[i][2]).trim();}
    private void dashboard(){
        settingsOpen=false;values.clear();details.clear();tabButtons.clear();
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),dp(12),dp(18),dp(8));
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(18),dp(12)+insets.getSystemWindowInsetTop(),dp(18),dp(8)+insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);root.requestApplyInsets();
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Mój Dom",25,TEXT);title.setTypeface(null,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(button("Ustawienia",this::settings));root.addView(header);
        connection=text(configured?"Łączenie…":"Ustaw połączenie z Home Assistant",13,MUTED);connection.setPadding(0,dp(10),0,dp(12));root.addView(connection);
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);
        for(String name:new String[]{"Dom","Gaz","Prąd","Kocioł","Serwis"}){Button b=button(name,()->{tab=name;render();});tabButtons.put(name,b);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(48));bp.rightMargin=dp(6);row.addView(b,bp);}
        tabs.addView(row);root.addView(tabs);
        ScrollView scroll=new ScrollView(this);body=column();scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(text("Podgląd • odświeżanie co 5 s przy otwartej aplikacji",11,MUTED));render();
    }
    private void render(){
        if(body==null)return;body.removeAllViews();values.clear();details.clear();roomViews.clear();
        for(String name:tabButtons.keySet()){Button b=tabButtons.get(name);boolean selected=name.equals(tab);b.setTextColor(selected?Color.WHITE:TEXT);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected?accent():CARD));}
        if(tab.equals("Dom")){renderRooms();return;}
        TextView title=text(tab,28,TEXT);title.setPadding(0,dp(14),0,dp(8));body.addView(title);
        String note=tab.equals("Gaz")?"Zużycie szacowane z pracy kotła. Dotknij wartości, aby zobaczyć historię 24 h.":tab.equals("Prąd")?"Dane Taurona mogą być opóźnione. Koszty pobieramy z Twoich sensorów HA.":tab.equals("Serwis")?"Statusy z Home Assistant. Ta wersja nie wysyła powiadomień w tle.":"Odczyty z Home Assistant. Aplikacja nie zmienia nastaw kotła.";
        TextView n=text(note,13,MUTED);n.setPadding(0,0,0,dp(12));body.addView(n);
        for(int i=0;i<metrics.length;i++)if(metrics[i][0].equals(tab)){
            final int index=i;LinearLayout c=column();c.setPadding(dp(18),dp(14),dp(18),dp(14));c.setBackground(shape(CARD));c.setElevation(dp(1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);body.addView(c,lp);
            c.addView(text(metrics[i][1],14,MUTED));TextView val=text("—",32,accent());c.addView(val);values.put(i,val);
            TextView sub=text("Oczekiwanie na dane",11,MUTED);c.addView(sub);details.put(i,sub);c.setOnClickListener(v->history(index));
        }
        updateCards();
    }
    private void updateCards(){
        updateRooms();
        for(Integer i:values.keySet()){
            JSONObject s=states.get(entity(i));String state=s==null?"":s.optString("state","");
            if(s==null||state.equals("unknown")||state.equals("unavailable")){
                values.get(i).setText("—");details.get(i).setText(s==null?"Brak encji — sprawdź identyfikator w ustawieniach":"Czujnik niedostępny");continue;
            }
            String display=state.equals("on")?"Tak / włączony":state.equals("off")?"Nie / wyłączony":state;
            JSONObject attrs=s.optJSONObject("attributes");String unit=attrs==null?"":attrs.optString("unit_of_measurement","");
            values.get(i).setText(display+(unit.isEmpty()?"":" "+unit));
            String date=s.optString("last_updated","");
            try{date=OffsetDateTime.parse(date).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm:ss"));}catch(Exception ignored){}
            details.get(i).setText("Aktualizacja encji: "+date);
        }
    }

    private int accent(){
        return tab.equals("Gaz")?Color.rgb(184,94,28):tab.equals("Prąd")?Color.rgb(65,99,215):tab.equals("Kocioł")?Color.rgb(143,77,190):GREEN;
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
        LinearLayout hero=column();hero.setPadding(dp(22),dp(22),dp(22),dp(22));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(18,91,92),Color.rgb(21,133,114)});
        gradient.setCornerRadius(dp(24));hero.setBackground(gradient);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(18);hp.bottomMargin=dp(18);body.addView(hero,hp);
        hero.addView(text("TWÓJ DOM",12,Color.rgb(182,231,221)));
        TextView heading=text("Temperatury w domu",27,Color.WHITE);heading.setTypeface(Typeface.create("sans-serif-medium",0));heading.setPadding(0,dp(8),0,dp(8));hero.addView(heading);
        hero.addView(text("Odczyty z wybranych pomieszczeń • co 5 s",13,Color.WHITE));
        body.addView(button("Wybierz pomieszczenia",this::chooseRooms));
        TextView help=text("Dotknij karty, aby zobaczyć historię. Przytrzymaj, aby nadać nazwę pomieszczenia.",13,MUTED);help.setPadding(0,dp(10),0,dp(16));body.addView(help);
        for(String id:selectedRooms()){
            LinearLayout card=column();card.setPadding(dp(20),dp(18),dp(20),dp(18));card.setBackground(shape(CARD));card.setElevation(dp(1));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(12);body.addView(card,cp);
            TextView name=text(roomName(id),16,TEXT);card.addView(name);
            TextView value=text("—",36,GREEN);value.setTypeface(Typeface.create("sans-serif-medium",0));card.addView(value);
            TextView updated=text("Oczekiwanie na dane",12,MUTED);card.addView(updated);
            roomViews.put(id,new TextView[]{name,value,updated});
            card.setOnClickListener(v->history(id,roomName(id)));
            card.setOnLongClickListener(v->{EditText label=new EditText(this);label.setText(roomName(id));label.setSingleLine(true);
                new AlertDialog.Builder(this).setTitle("Nazwa pomieszczenia").setView(label).setPositiveButton("Zapisz",(d,w)->{prefs.edit().putString("room_name_"+id,label.getText().toString().trim()).apply();render();}).setNegativeButton("Anuluj",null).show();return true;});
        }
        if(roomViews.isEmpty())body.addView(text("Dodaj czujniki przyciskiem „Wybierz pomieszczenia”.",16,MUTED));
        updateRooms();
    }
    private void updateRooms(){
        for(String id:roomViews.keySet()){
            TextView[] views=roomViews.get(id);views[0].setText(roomName(id));
            JSONObject state=states.get(id);String raw=state==null?"":state.optString("state","");
            double value;try{value=Double.parseDouble(raw);if(!Double.isFinite(value))throw new NumberFormatException();}catch(Exception e){views[1].setText("—");views[2].setText(state==null?"Brak encji w Home Assistant":"Czujnik niedostępny");continue;}
            JSONObject attrs=state.optJSONObject("attributes");String unit=attrs==null?"":attrs.optString("unit_of_measurement","");
            views[1].setText(String.format(new Locale("pl","PL"),"%.1f",value)+" "+unit);
            String date=state.optString("last_updated","");
            try{date=OffsetDateTime.parse(date).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM • HH:mm"));}catch(Exception ignored){}
            views[2].setText("Aktualizacja czujnika: "+date);
        }
    }
    private void chooseRooms(){
        if(states.isEmpty()){new AlertDialog.Builder(this).setMessage("Najpierw połącz aplikację z Home Assistant i poczekaj na odczyt czujników.").setPositiveButton("OK",null).show();return;}
        Set<String> chosen=selectedRooms();ArrayList<String> ids=new ArrayList<>();
        for(String id:states.keySet()){
            JSONObject attrs=states.get(id).optJSONObject("attributes");
            String unit=attrs==null?"":attrs.optString("unit_of_measurement","");
            if(id.startsWith("sensor.")&&attrs!=null&&("temperature".equals(attrs.optString("device_class"))||"°C".equals(unit)||"°F".equals(unit)))ids.add(id);
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
                handler.post(()->{busy=false;if(g!=generation||isDestroyed())return;states.clear();states.putAll(fresh);lastSuccess=java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));connection.setText("● Połączono • odczyt "+lastSuccess);connection.setTextColor(GREEN);updateCards();});
            }catch(Exception e){final String msg=e.getMessage();handler.post(()->{busy=false;if(g!=generation||isDestroyed())return;connection.setText("Brak nowych danych • "+(lastSuccess.isEmpty()?"":("ostatni odczyt "+lastSuccess+"\n"))+(msg==null?"Sprawdź połączenie":msg));connection.setTextColor(Color.rgb(255,186,112));});}
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
        ArrayList<EditText> ids=new ArrayList<>();for(int i=0;i<metrics.length;i++)ids.add(field(form,metrics[i][0]+" • "+metrics[i][1],entity(i),InputType.TYPE_CLASS_TEXT));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Połączenie i czujniki").setView(scroll).setPositiveButton("Zapisz",null).setNegativeButton("Anuluj",null).setNeutralButton("Wyloguj",null).create();
        dialog.setOnDismissListener(d->{settingsOpen=false;});dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            String newBase=HaClient.validate(url.getText().toString(),http.isChecked());
            String newToken=secret.getText().toString().trim();
            if(!newBase.equals(base)&&newToken.isEmpty()&&!token.isEmpty())throw new Exception("Przy zmianie adresu wpisz ponownie token dla tego serwera.");
            if(newToken.isEmpty())newToken=token;if(newToken.isEmpty())throw new Exception("Wpisz token dostępu.");
            for(EditText id:ids)if(!id.getText().toString().trim().matches("[a-z0-9_]+\\.[a-z0-9_]+"))throw new Exception("Identyfikatory muszą mieć postać sensor.nazwa lub binary_sensor.nazwa.");
            secrets.save(newToken);android.content.SharedPreferences.Editor edit=prefs.edit().putString("base",newBase).putBoolean("http",http.isChecked());
            for(int i=0;i<ids.size();i++)edit.putString("entity_"+i,ids.get(i).getText().toString().trim());edit.apply();
            base=newBase;token=newToken;configured=true;generation++;states.clear();lastSuccess="";dialog.dismiss();dashboard();refresh();
        }catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("OK",null).show();}});
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{secrets.clear();token="";configured=false;generation++;states.clear();lastSuccess="";dialog.dismiss();dashboard();connection.setText("Wylogowano");});
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
            String path="/api/history/period/"+from.toString()+"?filter_entity_id="+id+"&minimal_response&no_attributes";
            JSONArray groups=new JSONArray(HaClient.get(url,t,path));JSONArray rows=groups.length()==0?new JSONArray():groups.getJSONArray(0);
            ArrayList<float[]> points=new ArrayList<>();
            for(int j=0;j<rows.length();j++){JSONObject s=rows.getJSONObject(j);String value=s.optString("state","");try{
                double val=value.equals("on")?1:value.equals("off")?0:Double.parseDouble(value);
                double timestamp=OffsetDateTime.parse(s.optString("last_changed",s.optString("last_updated",""))).toInstant().toEpochMilli()/1000.;
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
