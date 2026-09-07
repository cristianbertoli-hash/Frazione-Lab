package io.braincore.medlabcare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivityV3 extends Activity implements TextToSpeech.OnInitListener {
    private static final String PREFS = "medlab_care_v02"; // mantiene i dati della v0.2
    private static final String DISCLAIMER =
            "Bozza non validata clinicamente. Non fa diagnosi e non sostituisce medico, CAU, 112/118 o Pronto Soccorso.";

    private SharedPreferences prefs;
    private TextToSpeech tts;
    private Boolean breathingOk, chestPressure, swallowOk;
    private TriageEngine.Result lastResult;
    private Integer systolic, diastolic, heartRate, spo2;
    private String note = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        restoreLocalValues();
        tts = new TextToSpeech(this, this);
        showHome();
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.ITALIAN);
    }

    private void showHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(20);
        scroll.addView(box);
        TextView t = title("MEDLAB Care"); t.setGravity(Gravity.CENTER); box.addView(t);
        box.addView(subtitle("V0.3 — servizi territoriali e scelta medico semplificata."));

        Button unwell = bigButton("NON STO BENE", v -> startTriage()); styleDanger(unwell); box.addView(unwell);
        Button well = bigButton("STO BENE", v -> markWell()); styleSafe(well); box.addView(well);
        box.addView(bigButton("TROVA CAU / PS / MEDICO", v -> showTerritory()));
        box.addView(bigButton("CHIAMA", v -> showContacts()));
        box.addView(bigButton("MESSAGGIO", v -> showMessage()));
        box.addView(bigButton("VALORI", v -> showVitalsDialog(false)));
        box.addView(bigButton("IMPOSTA CONTATTI", v -> showSettings()));
        Button emergency = bigButton("112 — EMERGENZA", v -> dial("112")); styleDanger(emergency); box.addView(emergency);

        box.addView(subtitle("Privacy: nessun account e nessun caricamento automatico. Gli elenchi incorporati provengono da fonti sanitarie pubbliche; i dati scelti restano sul telefono."));
        box.addView(subtitle(DISCLAIMER));
        setContentView(scroll);
    }

    private void markWell() {
        breathingOk = true; chestPressure = false; swallowOk = true;
        lastResult = TriageEngine.assess(true, false, true, false);
        showResult();
    }

    private void startTriage() {
        speak("Siediti e resta ferma. Non alzarti in fretta.");
        new AlertDialog.Builder(this).setTitle("Prima di iniziare")
                .setMessage("Siediti e resta ferma. Non alzarti in fretta. Poi rispondi a tre domande semplici.")
                .setPositiveButton("SONO SEDUTA — CONTINUA", (d,w) -> askBreathing())
                .setNegativeButton("ANNULLA", null).show();
    }

    private interface AnswerHandler { void onAnswer(boolean yes); }
    private void askYesNo(String title, String q, AnswerHandler h) {
        speak(q);
        new AlertDialog.Builder(this).setTitle(title).setMessage(q)
                .setPositiveButton("SÌ", (d,w) -> h.onAnswer(true))
                .setNegativeButton("NO", (d,w) -> h.onAnswer(false))
                .setCancelable(false).show();
    }
    private void askBreathing() {
        askYesNo("Domanda 1 di 3", "Respiri bene e riesci a parlare normalmente?", yes -> {
            breathingOk = yes;
            if (!yes) { chestPressure = null; swallowOk = null; lastResult = TriageEngine.assess(false,false,true,true); showResult(); }
            else askChest();
        });
    }
    private void askChest() {
        askYesNo("Domanda 2 di 3", "Hai dolore o pressione al petto?", yes -> {
            chestPressure = yes;
            if (yes) { swallowOk = null; lastResult = TriageEngine.assess(true,true,true,true); showResult(); }
            else askSwallow();
        });
    }
    private void askSwallow() {
        askYesNo("Domanda 3 di 3", "Riesci a mandare giù acqua e saliva?", yes -> {
            swallowOk = yes; lastResult = TriageEngine.assess(true,false,yes,true); showResult();
        });
    }

    private void showResult() {
        String heading = lastResult.level == TriageEngine.Level.RED ? "ROSSO — CHIEDI AIUTO" :
                lastResult.level == TriageEngine.Level.YELLOW ? "GIALLO — AVVISA QUALCUNO" : "VERDE — STO BENE";
        speak(heading + ". " + lastResult.guidance);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(heading)
                .setMessage(lastResult.guidance + "\n\n" + DISCLAIMER)
                .setPositiveButton("MESSAGGIO", (d,w) -> showMessage())
                .setNeutralButton("AGGIUNGI VALORI", (d,w) -> showVitalsDialog(true));
        if (lastResult.level == TriageEngine.Level.RED) b.setNegativeButton("CHIAMA 112", (d,w) -> dial("112"));
        else b.setNegativeButton("HOME", (d,w) -> showHome());
        b.show();
    }

    private void showVitalsDialog(boolean returnToMessage) {
        LinearLayout box = column(8);
        EditText sys = numberField("Pressione massima, es. 120", systolic);
        EditText dia = numberField("Pressione minima, es. 75", diastolic);
        EditText hr = numberField("Battiti/min, es. 65", heartRate);
        EditText sat = numberField("Saturazione %, es. 98", spo2);
        EditText n = textField("Come ti senti?", note); n.setMinLines(3);
        box.addView(sys); box.addView(dia); box.addView(hr); box.addView(sat); box.addView(n);
        new AlertDialog.Builder(this).setTitle("VALORI — inserisci solo ciò che hai misurato").setView(box)
                .setPositiveButton("SALVA SUL TELEFONO", (d,w) -> {
                    systolic = parse(sys,40,300); diastolic = parse(dia,20,200);
                    heartRate = parse(hr,20,250); spo2 = parse(sat,40,100); note = n.getText().toString().trim();
                    persistLocalValues(); toast("Valori salvati sul telefono");
                    if (returnToMessage) showMessage(); else showHome();
                }).setNegativeButton("ANNULLA", null).show();
    }

    private void showTerritory() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(18); scroll.addView(box);
        box.addView(title("Servizi vicino a te"));
        box.addView(subtitle("Scegli Regione, Provincia e Comune. Per Carpi è già disponibile anche l'elenco medico direttamente nell'app."));

        box.addView(label("REGIONE"));
        Spinner region = new Spinner(this);
        region.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{TerritoryData.REGION_EMILIA_ROMAGNA, "Altra regione — PS nazionale"}));
        box.addView(region);

        box.addView(label("PROVINCIA"));
        Spinner province = new Spinner(this); box.addView(province);
        List<String> provinces = TerritoryData.provinces();
        province.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, provinces));
        int pIdx = provinces.indexOf(prefs.getString("territory_province", "Modena")); if (pIdx >= 0) province.setSelection(pIdx);

        box.addView(label("COMUNE / ZONA"));
        Spinner city = new Spinner(this); box.addView(city);
        LinearLayout results = column(4); box.addView(results);

        Runnable refreshCities = () -> {
            String p = String.valueOf(province.getSelectedItem());
            List<String> cities = TerritoryData.citiesForProvince(p);
            city.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cities));
            int cIdx = cities.indexOf(prefs.getString("territory_city", "Tutti / altro comune"));
            if (cIdx >= 0) city.setSelection(cIdx);
        };
        Runnable refresh = () -> {
            results.removeAllViews();
            if (region.getSelectedItemPosition() != 0) {
                results.addView(subtitle("Questa regione non è ancora integrata localmente. Puoi aprire l'elenco nazionale dei Pronto Soccorso."));
                results.addView(bigButton("PRONTO SOCCORSO ITALIA", v -> openWeb(TerritoryData.OFFICIAL_PS_DIRECTORY)));
                results.addView(bigButton("SALVA STRUTTURA MANUALMENTE", v -> askManualService("ps")));
                return;
            }
            String p = String.valueOf(province.getSelectedItem());
            String c = String.valueOf(city.getSelectedItem());
            prefs.edit().putString("territory_region", TerritoryData.REGION_EMILIA_ROMAGNA)
                    .putString("territory_province", p).putString("territory_city", c).apply();
            for (TerritoryData.Facility f : TerritoryData.cauFor(p,c)) results.addView(territoryCard(f));

            if ("Modena".equals(p) && "Carpi".equals(c)) {
                Button doctors = bigButton("SCEGLI IL MEDICO — ELENCO CARPI", v -> showDoctorPicker("Emilia-Romagna","Modena","Carpi"));
                doctors.setTextSize(20); results.addView(doctors);
                String saved = prefs.getString("doctor_name", "");
                if (!saved.isEmpty() && !"Medico".equals(saved)) results.addView(subtitle("Medico salvato: " + saved));
            } else if ("Modena".equals(p)) {
                results.addView(bigButton("ELENCO MEDICI AUSL MODENA", v -> openWeb(DoctorDirectory.SOURCE_MODENA)));
            }
            results.addView(bigButton("IMPOSTA MEDICO MANUALMENTE", v -> showSettings()));
            results.addView(bigButton("ELENCO UFFICIALE CAU EMILIA-ROMAGNA", v -> openWeb(TerritoryData.OFFICIAL_CAU_DIRECTORY)));
            results.addView(bigButton("PRONTO SOCCORSO ITALIA", v -> openWeb(TerritoryData.OFFICIAL_PS_DIRECTORY)));
        };
        province.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { refreshCities.run(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        city.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { refresh.run(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        region.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { refresh.run(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        box.addView(bigButton("HOME", v -> showHome()));
        setContentView(scroll);
    }

    private void showDoctorPicker(String region, String province, String city) {
        List<DoctorDirectory.Doctor> doctors = DoctorDirectory.forLocation(region, province, city);
        if (doctors.isEmpty()) { toast("Elenco locale non ancora disponibile per questo comune"); openWeb(DoctorDirectory.SOURCE_MODENA); return; }

        ScrollView scroll = new ScrollView(this); LinearLayout box = column(18); scroll.addView(box);
        box.addView(title("Scegli il medico"));
        box.addView(subtitle(city + " — elenco derivato dalla directory pubblica AUSL Modena. Tocca il menu qui sotto."));
        Spinner picker = new Spinner(this);
        picker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, doctors));
        box.addView(picker);
        TextView detail = subtitle(""); detail.setTextSize(18); detail.setTextColor(Color.BLACK); box.addView(detail);
        Button save = bigButton("IMPOSTA COME MIO MEDICO", v -> {
            DoctorDirectory.Doctor d = (DoctorDirectory.Doctor) picker.getSelectedItem();
            saveDoctor(d);
        });
        box.addView(save);
        box.addView(bigButton("APRI ELENCO UFFICIALE AUSL MODENA", v -> openWeb(DoctorDirectory.SOURCE_MODENA)));
        box.addView(bigButton("AGGIUNGI / CORREGGI TELEFONO MANUALMENTE", v -> showSettings()));
        box.addView(bigButton("INDIETRO AI SERVIZI", v -> showTerritory()));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                DoctorDirectory.Doctor d = doctors.get(pos);
                String phone = d.phone.isEmpty() ? "Telefono non incorporato: puoi aggiungerlo quando salvi." : "Telefono: " + d.phone;
                detail.setText(d.name + "\n" + d.address + "\n" + phone);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        setContentView(scroll);
    }

    private void saveDoctor(DoctorDirectory.Doctor d) {
        if (!d.phone.isEmpty()) {
            prefs.edit().putString("doctor_name", d.name).putString("doctor_phone", d.phone)
                    .putString("doctor_address", d.address).apply();
            toast("Medico salvato sul telefono");
            showContacts();
            return;
        }
        EditText phone = phoneField("Telefono del medico — facoltativo", prefs.getString("doctor_phone", ""));
        new AlertDialog.Builder(this).setTitle("Salva " + d.name)
                .setMessage(d.address + "\n\nIl nome e l'indirizzo provengono dall'elenco pubblico. Se hai il numero diretto, inseriscilo; puoi anche lasciarlo vuoto.")
                .setView(phone)
                .setPositiveButton("SALVA", (x,w) -> {
                    prefs.edit().putString("doctor_name", d.name).putString("doctor_phone", phone.getText().toString().trim())
                            .putString("doctor_address", d.address).apply();
                    toast("Medico salvato sul telefono"); showContacts();
                }).setNegativeButton("ANNULLA", null).show();
    }

    private View territoryCard(TerritoryData.Facility f) {
        LinearLayout card = column(12); card.setBackgroundColor(Color.WHITE);
        TextView n = title(f.name); n.setTextSize(24); n.setGravity(Gravity.START); card.addView(n);
        card.addView(subtitle(f.city + " — " + f.province));
        if (!f.address.isEmpty()) card.addView(subtitle(f.address));
        card.addView(subtitle(f.phone.isEmpty() ? "Telefono non incorporato" : "Telefono: " + f.phone));
        if (!f.phone.isEmpty()) card.addView(bigButton("CHIAMA", v -> dial(f.phone)));
        card.addView(bigButton("SALVA COME MIO CAU", v -> saveCau(f)));
        card.addView(bigButton("FONTE UFFICIALE", v -> openWeb(f.officialUrl)));
        return card;
    }

    private void saveCau(TerritoryData.Facility f) {
        if (!f.phone.isEmpty()) {
            prefs.edit().putString("cau_name", f.name + " – " + f.city).putString("cau_phone", f.phone).apply();
            toast("CAU salvato sul telefono"); return;
        }
        EditText phone = phoneField("Telefono CAU — facoltativo", prefs.getString("cau_phone", ""));
        new AlertDialog.Builder(this).setTitle("Salva " + f.city).setView(phone)
                .setPositiveButton("SALVA", (d,w) -> {
                    prefs.edit().putString("cau_name", f.name + " – " + f.city)
                            .putString("cau_phone", phone.getText().toString().trim()).apply(); toast("CAU salvato");
                }).setNegativeButton("ANNULLA", null).show();
    }

    private void askManualService(String kind) {
        LinearLayout box = column(8); EditText name=textField("Nome struttura",""); EditText phone=phoneField("Telefono","");
        box.addView(name); box.addView(phone);
        new AlertDialog.Builder(this).setTitle("Aggiungi struttura").setView(box)
                .setPositiveButton("SALVA", (d,w) -> { prefs.edit().putString(kind+"_name",name.getText().toString().trim())
                        .putString(kind+"_phone",phone.getText().toString().trim()).apply(); toast("Struttura salvata"); })
                .setNegativeButton("ANNULLA", null).show();
    }

    private void showContacts() {
        ScrollView scroll = new ScrollView(this); LinearLayout box = column(18); scroll.addView(box);
        box.addView(title("Chi vuoi contattare?"));
        box.addView(contactCard(prefs.getString("family_name","Familiare"), prefs.getString("family_phone",""), ""));
        box.addView(contactCard(prefs.getString("doctor_name","Medico"), prefs.getString("doctor_phone",""), prefs.getString("doctor_address","")));
        box.addView(contactCard(prefs.getString("cau_name","CAU"), prefs.getString("cau_phone",""), ""));
        box.addView(contactCard(prefs.getString("ps_name","Pronto Soccorso"), prefs.getString("ps_phone",""), ""));
        box.addView(bigButton("TROVA SERVIZI", v -> showTerritory()));
        Button e=bigButton("112 — EMERGENZA",v->dial("112"));styleDanger(e);box.addView(e);
        box.addView(bigButton("HOME",v->showHome())); setContentView(scroll);
    }

    private View contactCard(String name, String phone, String address) {
        LinearLayout card=column(12); card.setBackgroundColor(Color.WHITE);
        TextView n=title(name); n.setTextSize(26); n.setGravity(Gravity.START); card.addView(n);
        if (!address.isEmpty()) card.addView(subtitle(address));
        card.addView(subtitle(phone.isEmpty()?"Numero non impostato":phone));
        card.addView(bigButton("CHIAMA",v->dial(phone))); card.addView(bigButton("SMS",v->sendSms(phone,composeMessage())));
        card.addView(bigButton("WHATSAPP",v->openWhatsApp(phone,composeMessage()))); return card;
    }

    private void showMessage() {
        String msg=composeMessage(); ScrollView scroll=new ScrollView(this); LinearLayout box=column(18); scroll.addView(box);
        box.addView(title("Messaggio pronto")); TextView p=subtitle(msg); p.setTextSize(19); p.setTextIsSelectable(true); p.setBackgroundColor(Color.rgb(245,245,245)); p.setPadding(dp(12),dp(12),dp(12),dp(12)); box.addView(p);
        box.addView(bigButton("COPIA TESTO",v->copy(msg))); String family=prefs.getString("family_phone","");
        box.addView(bigButton("APRI SMS AL FAMILIARE",v->sendSms(family,msg))); box.addView(bigButton("APRI WHATSAPP AL FAMILIARE",v->openWhatsApp(family,msg)));
        box.addView(bigButton("CONTATTI",v->showContacts())); box.addView(bigButton("HOME",v->showHome())); setContentView(scroll);
    }

    private void showSettings() {
        LinearLayout box=column(8);
        EditText person=textField("Nome della persona",prefs.getString("person_name","Persona"));
        EditText familyName=textField("Nome familiare",prefs.getString("family_name","Familiare"));
        EditText familyPhone=phoneField("Cellulare familiare",prefs.getString("family_phone",""));
        EditText doctorName=textField("Nome medico",prefs.getString("doctor_name","Medico"));
        EditText doctorPhone=phoneField("Telefono medico",prefs.getString("doctor_phone",""));
        EditText cauName=textField("Nome CAU",prefs.getString("cau_name","CAU"));
        EditText cauPhone=phoneField("Telefono CAU",prefs.getString("cau_phone",""));
        CheckBox voice=new CheckBox(this);voice.setText("Leggi le domande ad alta voce");voice.setTextSize(18);voice.setChecked(prefs.getBoolean("voice",true));
        box.addView(person);box.addView(familyName);box.addView(familyPhone);box.addView(doctorName);box.addView(doctorPhone);box.addView(cauName);box.addView(cauPhone);box.addView(voice);
        new AlertDialog.Builder(this).setTitle("IMPOSTAZIONI LOCALI").setMessage("Nessun account: i dati restano sul telefono.").setView(box)
                .setPositiveButton("SALVA",(d,w)->{prefs.edit().putString("person_name",person.getText().toString().trim()).putString("family_name",familyName.getText().toString().trim())
                        .putString("family_phone",familyPhone.getText().toString().trim()).putString("doctor_name",doctorName.getText().toString().trim()).putString("doctor_phone",doctorPhone.getText().toString().trim())
                        .putString("cau_name",cauName.getText().toString().trim()).putString("cau_phone",cauPhone.getText().toString().trim()).putBoolean("voice",voice.isChecked()).apply();showHome();})
                .setNeutralButton("CANCELLA DATI LOCALI",(d,w)->confirmClearLocalData()).setNegativeButton("ANNULLA",null).show();
    }

    private void confirmClearLocalData() {
        new AlertDialog.Builder(this).setTitle("Cancellare i dati locali?").setMessage("Verranno cancellati contatti, valori e servizi salvati.")
                .setPositiveButton("SÌ, CANCELLA",(d,w)->{prefs.edit().clear().apply();systolic=diastolic=heartRate=spo2=null;note="";breathingOk=chestPressure=swallowOk=null;lastResult=null;toast("Dati cancellati");showHome();})
                .setNegativeButton("NO",null).show();
    }

    private String composeMessage() { return MessageComposer.compose(prefs.getString("person_name","Persona"),now(),lastResult,breathingOk,chestPressure,swallowOk,systolic,diastolic,heartRate,spo2,note); }
    private void persistLocalValues(){SharedPreferences.Editor e=prefs.edit();putNullableInt(e,"systolic",systolic);putNullableInt(e,"diastolic",diastolic);putNullableInt(e,"heart_rate",heartRate);putNullableInt(e,"spo2",spo2);e.putString("note",note==null?"":note).apply();}
    private void restoreLocalValues(){systolic=getNullableInt("systolic");diastolic=getNullableInt("diastolic");heartRate=getNullableInt("heart_rate");spo2=getNullableInt("spo2");note=prefs.getString("note","");}
    private void putNullableInt(SharedPreferences.Editor e,String key,Integer v){if(v==null)e.remove(key);else e.putInt(key,v);} private Integer getNullableInt(String key){return prefs.contains(key)?prefs.getInt(key,0):null;}

    private void dial(String phone){if(phone==null||phone.trim().isEmpty()){toast("Numero non impostato");return;}try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+phone.trim().replace(" ",""))));}catch(ActivityNotFoundException e){toast("Nessuna app per chiamare");}}
    private void sendSms(String phone,String msg){if(phone==null||phone.trim().isEmpty()){toast("Numero non impostato");return;}try{Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(phone.trim())));i.putExtra("sms_body",msg);startActivity(i);}catch(ActivityNotFoundException e){toast("Nessuna app SMS");}}
    private void openWhatsApp(String phone,String msg){if(phone==null||phone.trim().isEmpty()){toast("Numero non impostato");return;}String digits=phone.replaceAll("[^0-9]","");if(!digits.startsWith("39")&&digits.length()<=10)digits="39"+digits;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+digits+"?text="+Uri.encode(msg))));}catch(ActivityNotFoundException e){toast("WhatsApp/browser non disponibile");}}
    private void openWeb(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast("Browser non disponibile");}}
    private void copy(String msg){ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("MEDLAB Care",msg));toast("Messaggio copiato");}
    private void speak(String text){if(tts!=null&&prefs.getBoolean("voice",true))tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"medlab-care");}

    private LinearLayout column(int p){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(p),dp(p),dp(p),dp(p));return l;}
    private TextView title(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(30);t.setTextColor(Color.BLACK);t.setPadding(0,0,0,dp(12));return t;}
    private TextView subtitle(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(16);t.setTextColor(Color.rgb(70,70,70));t.setPadding(0,dp(4),0,dp(10));return t;}
    private TextView label(String s){TextView t=subtitle(s);t.setTextSize(18);t.setTextColor(Color.BLACK);return t;}
    private Button bigButton(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setTextSize(23);b.setMinHeight(dp(68));b.setOnClickListener(l);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));b.setLayoutParams(p);return b;}
    private void styleDanger(Button b){b.setTextColor(Color.WHITE);b.setBackgroundColor(Color.rgb(180,30,30));} private void styleSafe(Button b){b.setTextColor(Color.rgb(10,75,30));b.setBackgroundColor(Color.rgb(220,245,226));}
    private EditText textField(String h,String v){EditText e=new EditText(this);e.setHint(h);e.setText(v);e.setTextSize(20);e.setMinHeight(dp(58));return e;}
    private EditText phoneField(String h,String v){EditText e=textField(h,v);e.setInputType(InputType.TYPE_CLASS_PHONE);return e;}
    private EditText numberField(String h,Integer v){EditText e=new EditText(this);e.setHint(h);e.setText(v==null?"":String.valueOf(v));e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setTextSize(20);e.setMinHeight(dp(58));return e;}
    private Integer parse(EditText e,int min,int max){try{String s=e.getText().toString().trim();if(s.isEmpty())return null;int v=Integer.parseInt(s);return v>=min&&v<=max?v:null;}catch(Exception x){return null;}}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);} private String now(){return new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.ITALIAN).format(new Date());} private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
