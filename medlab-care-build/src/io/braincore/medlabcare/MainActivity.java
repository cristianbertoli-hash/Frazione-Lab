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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final String PREFS = "medlab_care_v02";
    private static final String DISCLAIMER =
            "Bozza non validata clinicamente. Non fa diagnosi e non sostituisce medico, CAU, 112/118 o Pronto Soccorso.";
    private static final String MODENA_DOCTORS =
            "https://www.ausl.mo.it/strutture/medici-di-medicina-generale/elenco/";

    private SharedPreferences prefs;
    private TextToSpeech tts;

    private Boolean breathingOk = null;
    private Boolean chestPressure = null;
    private Boolean swallowOk = null;
    private TriageEngine.Result lastResult = null;

    private Integer systolic;
    private Integer diastolic;
    private Integer heartRate;
    private Integer spo2;
    private String note = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        restoreLocalValues();
        tts = new TextToSpeech(this, this);
        showHome();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.ITALIAN);
    }

    private void showHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(20);
        scroll.addView(box);

        TextView appTitle = title("MEDLAB Care");
        appTitle.setGravity(Gravity.CENTER);
        box.addView(appTitle);
        box.addView(subtitle("Locale, semplice, senza account. V0.2 territoriale."));

        Button unwell = bigButton("NON STO BENE", v -> startTriage());
        styleDanger(unwell);
        box.addView(unwell);

        Button well = bigButton("STO BENE", v -> markWell());
        styleSafe(well);
        box.addView(well);

        Button territory = bigButton("TROVA CAU / PS / MEDICO", v -> showTerritory());
        territory.setTextSize(21);
        box.addView(territory);

        box.addView(bigButton("CHIAMA", v -> showContacts()));
        box.addView(bigButton("MESSAGGIO", v -> showMessage()));
        box.addView(bigButton("VALORI", v -> showVitalsDialog(false)));
        box.addView(bigButton("IMPOSTA CONTATTI", v -> showSettings()));

        Button emergency = bigButton("112 — EMERGENZA", v -> dial("112"));
        styleDanger(emergency);
        box.addView(emergency);

        TextView privacy = subtitle(
                "Privacy V0.2: nessuna registrazione e nessun database remoto. La ricerca territoriale usa un archivio locale dei CAU Emilia-Romagna; le schede ufficiali e l'elenco PS si aprono nel browser solo quando lo scegli tu."
        );
        privacy.setPadding(0, dp(18), 0, dp(8));
        box.addView(privacy);
        box.addView(subtitle(DISCLAIMER));
        setContentView(scroll);
    }

    private void markWell() {
        breathingOk = true;
        chestPressure = false;
        swallowOk = true;
        lastResult = TriageEngine.assess(true, false, true, false);
        showResult();
    }

    private void startTriage() {
        speak("Siediti e resta ferma. Non alzarti in fretta.");
        new AlertDialog.Builder(this)
                .setTitle("Prima di iniziare")
                .setMessage("Siediti e resta ferma. Non alzarti in fretta. Poi rispondi a tre domande semplici.")
                .setPositiveButton("SONO SEDUTA — CONTINUA", (d, w) -> askBreathing())
                .setNegativeButton("ANNULLA", null)
                .show();
    }

    private void askBreathing() {
        askYesNo("Domanda 1 di 3", "Respiri bene e riesci a parlare normalmente?", yes -> {
            breathingOk = yes;
            if (!yes) {
                chestPressure = null;
                swallowOk = null;
                lastResult = TriageEngine.assess(false, false, true, true);
                showResult();
            } else askChest();
        });
    }

    private void askChest() {
        askYesNo("Domanda 2 di 3", "Hai dolore o pressione al petto?", yes -> {
            chestPressure = yes;
            if (yes) {
                swallowOk = null;
                lastResult = TriageEngine.assess(true, true, true, true);
                showResult();
            } else askSwallow();
        });
    }

    private void askSwallow() {
        askYesNo("Domanda 3 di 3", "Riesci a mandare giù acqua e saliva?", yes -> {
            swallowOk = yes;
            lastResult = TriageEngine.assess(true, false, yes, true);
            showResult();
        });
    }

    private interface AnswerHandler { void onAnswer(boolean yes); }

    private void askYesNo(String title, String question, AnswerHandler handler) {
        speak(question);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(question)
                .setPositiveButton("SÌ", (d, w) -> handler.onAnswer(true))
                .setNegativeButton("NO", (d, w) -> handler.onAnswer(false))
                .setCancelable(false)
                .show();
    }

    private void showResult() {
        String heading;
        if (lastResult.level == TriageEngine.Level.RED) heading = "ROSSO — CHIEDI AIUTO";
        else if (lastResult.level == TriageEngine.Level.YELLOW) heading = "GIALLO — AVVISA QUALCUNO";
        else heading = "VERDE — STO BENE";

        speak(heading + ". " + lastResult.guidance);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(heading)
                .setMessage(lastResult.guidance + "\n\n" + DISCLAIMER)
                .setPositiveButton("MESSAGGIO", (d, w) -> showMessage())
                .setNeutralButton("AGGIUNGI VALORI", (d, w) -> showVitalsDialog(true));

        if (lastResult.level == TriageEngine.Level.RED) {
            b.setNegativeButton("CHIAMA 112", (d, w) -> dial("112"));
        } else {
            b.setNegativeButton("HOME", (d, w) -> showHome());
        }
        b.show();
    }

    private void showVitalsDialog(boolean returnToMessage) {
        LinearLayout box = column(8);
        EditText sys = numberField("Pressione massima, es. 120", systolic);
        EditText dia = numberField("Pressione minima, es. 75", diastolic);
        EditText hr = numberField("Battiti/min, es. 65", heartRate);
        EditText sat = numberField("Saturazione %, es. 98", spo2);
        EditText n = new EditText(this);
        n.setHint("Come ti senti? Es. fastidio alla gola");
        n.setText(note);
        n.setMinLines(3);
        n.setTextSize(20);
        box.addView(sys); box.addView(dia); box.addView(hr); box.addView(sat); box.addView(n);

        new AlertDialog.Builder(this)
                .setTitle("VALORI — inserisci solo ciò che hai misurato")
                .setView(box)
                .setPositiveButton("SALVA SUL TELEFONO", (d, w) -> {
                    systolic = parse(sys, 40, 300);
                    diastolic = parse(dia, 20, 200);
                    heartRate = parse(hr, 20, 250);
                    spo2 = parse(sat, 40, 100);
                    note = n.getText().toString().trim();
                    persistLocalValues();
                    toast("Valori salvati solo su questo telefono");
                    if (returnToMessage) showMessage(); else showHome();
                })
                .setNegativeButton("ANNULLA", null)
                .show();
    }

    private void showTerritory() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(18);
        scroll.addView(box);
        box.addView(title("Servizi vicino a te"));
        box.addView(subtitle("Prima versione: CAU Emilia-Romagna offline + collegamento agli elenchi ufficiali. Nessuna posizione GPS viene raccolta."));

        TextView regionLabel = subtitle("REGIONE");
        regionLabel.setTextSize(18);
        regionLabel.setTextColor(Color.BLACK);
        box.addView(regionLabel);
        Spinner region = new Spinner(this);
        ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{TerritoryData.REGION_EMILIA_ROMAGNA, "Altra regione — elenco PS nazionale"});
        region.setAdapter(regionAdapter);
        box.addView(region);

        TextView provLabel = subtitle("PROVINCIA");
        provLabel.setTextSize(18);
        provLabel.setTextColor(Color.BLACK);
        box.addView(provLabel);
        Spinner province = new Spinner(this);
        box.addView(province);

        TextView cityLabel = subtitle("COMUNE / ZONA CAU");
        cityLabel.setTextSize(18);
        cityLabel.setTextColor(Color.BLACK);
        box.addView(cityLabel);
        Spinner city = new Spinner(this);
        box.addView(city);

        LinearLayout results = column(4);
        box.addView(results);

        List<String> provinces = TerritoryData.provinces();
        ArrayAdapter<String> provinceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, provinces);
        province.setAdapter(provinceAdapter);

        String savedProvince = prefs.getString("territory_province", "Modena");
        int pIndex = provinces.indexOf(savedProvince);
        if (pIndex >= 0) province.setSelection(pIndex);

        Runnable refreshCities = () -> {
            String p = String.valueOf(province.getSelectedItem());
            List<String> cities = TerritoryData.citiesForProvince(p);
            ArrayAdapter<String> cityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cities);
            city.setAdapter(cityAdapter);
            String savedCity = prefs.getString("territory_city", "Tutti / altro comune");
            int cIndex = cities.indexOf(savedCity);
            if (cIndex >= 0) city.setSelection(cIndex);
        };

        Runnable refreshResults = () -> {
            results.removeAllViews();
            if (region.getSelectedItemPosition() != 0) {
                results.addView(subtitle("Per questa regione la V0.2 apre l'elenco nazionale ufficiale dei Pronto Soccorso. I servizi territoriali regionali verranno aggiunti progressivamente."));
                Button ps = bigButton("PRONTO SOCCORSO ITALIA — ELENCO UFFICIALE", v -> openWeb(TerritoryData.OFFICIAL_PS_DIRECTORY));
                ps.setTextSize(19);
                results.addView(ps);
                results.addView(bigButton("SALVA PS MANUALMENTE", v -> askManualService("ps")));
                return;
            }
            String p = String.valueOf(province.getSelectedItem());
            String c = String.valueOf(city.getSelectedItem());
            prefs.edit().putString("territory_region", TerritoryData.REGION_EMILIA_ROMAGNA)
                    .putString("territory_province", p)
                    .putString("territory_city", c).apply();
            List<TerritoryData.Facility> list = TerritoryData.cauFor(p, c);
            if (list.isEmpty()) results.addView(subtitle("Nessun CAU trovato in questa selezione. Mostro la directory ufficiale regionale."));
            for (TerritoryData.Facility f : list) results.addView(territoryCard(f));
            Button directory = bigButton("ELENCO UFFICIALE CAU EMILIA-ROMAGNA", v -> openWeb(TerritoryData.OFFICIAL_CAU_DIRECTORY));
            directory.setTextSize(18);
            results.addView(directory);
            Button ps = bigButton("PRONTO SOCCORSO ITALIA — ELENCO UFFICIALE", v -> openWeb(TerritoryData.OFFICIAL_PS_DIRECTORY));
            ps.setTextSize(18);
            results.addView(ps);
            if ("Modena".equals(p)) {
                results.addView(bigButton("CERCA MEDICI DI FAMIGLIA — AUSL MODENA", v -> openWeb(MODENA_DOCTORS)));
            }
            results.addView(bigButton("IMPOSTA MEDICO MANUALMENTE", v -> showSettings()));
        };

        province.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshCities.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        city.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { refreshResults.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        region.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { refreshResults.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        box.addView(bigButton("HOME", v -> showHome()));
        setContentView(scroll);
    }

    private View territoryCard(TerritoryData.Facility f) {
        LinearLayout card = column(12);
        card.setBackgroundColor(Color.WHITE);
        TextView n = title(f.name);
        n.setTextSize(24);
        n.setGravity(Gravity.START);
        card.addView(n);
        card.addView(subtitle(f.city + " — " + f.province));
        if (!f.address.isEmpty()) card.addView(subtitle(f.address));
        if (!f.phone.isEmpty()) card.addView(subtitle("Telefono: " + f.phone));
        else card.addView(subtitle("Telefono non incluso nell'archivio locale: puoi aprire la scheda ufficiale o aggiungerlo manualmente."));

        if (!f.phone.isEmpty()) card.addView(bigButton("CHIAMA", v -> dial(f.phone)));
        card.addView(bigButton("SALVA COME MIO CAU", v -> saveCau(f)));
        card.addView(bigButton("SCHEDA / ELENCO UFFICIALE", v -> openWeb(f.officialUrl)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, dp(14));
        card.setLayoutParams(params);
        return card;
    }

    private void saveCau(TerritoryData.Facility f) {
        if (!f.phone.isEmpty()) {
            prefs.edit().putString("cau_name", f.name + " – " + f.city)
                    .putString("cau_phone", f.phone).apply();
            toast("CAU salvato sul telefono");
            return;
        }
        EditText phone = phoneField("Telefono CAU (facoltativo)", prefs.getString("cau_phone", ""));
        new AlertDialog.Builder(this)
                .setTitle("Salva " + f.city)
                .setMessage("Il CAU viene salvato localmente. Se conosci il numero, inseriscilo; altrimenti puoi lasciarlo vuoto e aggiungerlo dopo.")
                .setView(phone)
                .setPositiveButton("SALVA", (d, w) -> {
                    prefs.edit().putString("cau_name", f.name + " – " + f.city)
                            .putString("cau_phone", phone.getText().toString().trim()).apply();
                    toast("CAU salvato sul telefono");
                })
                .setNegativeButton("ANNULLA", null).show();
    }

    private void askManualService(String kind) {
        LinearLayout box = column(8);
        EditText name = textField("Nome struttura", "");
        EditText phone = phoneField("Telefono", "");
        box.addView(name); box.addView(phone);
        new AlertDialog.Builder(this)
                .setTitle("Aggiungi struttura")
                .setView(box)
                .setPositiveButton("SALVA", (d, w) -> {
                    prefs.edit().putString(kind + "_name", name.getText().toString().trim())
                            .putString(kind + "_phone", phone.getText().toString().trim()).apply();
                    toast("Struttura salvata localmente");
                })
                .setNegativeButton("ANNULLA", null).show();
    }

    private void showContacts() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(18);
        scroll.addView(box);
        box.addView(title("Chi vuoi contattare?"));
        box.addView(contactCard(prefs.getString("family_name", "Familiare"), prefs.getString("family_phone", "")));
        box.addView(contactCard(prefs.getString("doctor_name", "Medico"), prefs.getString("doctor_phone", "")));
        box.addView(contactCard(prefs.getString("cau_name", "CAU"), prefs.getString("cau_phone", "")));
        box.addView(contactCard(prefs.getString("ps_name", "Pronto Soccorso"), prefs.getString("ps_phone", "")));

        Button services = bigButton("TROVA SERVIZI", v -> showTerritory());
        box.addView(services);
        Button emergency = bigButton("112 — EMERGENZA", v -> dial("112"));
        styleDanger(emergency);
        box.addView(emergency);
        box.addView(bigButton("HOME", v -> showHome()));
        setContentView(scroll);
    }

    private View contactCard(String name, String phone) {
        LinearLayout card = column(12);
        card.setBackgroundColor(Color.WHITE);
        TextView nameView = title(name);
        nameView.setTextSize(26);
        nameView.setGravity(Gravity.START);
        card.addView(nameView);
        card.addView(subtitle(phone.isEmpty() ? "Numero non impostato" : phone));
        card.addView(bigButton("CHIAMA", v -> dial(phone)));
        card.addView(bigButton("SMS", v -> sendSms(phone, composeMessage())));
        card.addView(bigButton("WHATSAPP", v -> openWhatsApp(phone, composeMessage())));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private void showMessage() {
        String msg = composeMessage();
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(18);
        scroll.addView(box);
        box.addView(title("Messaggio pronto"));
        TextView preview = subtitle(msg);
        preview.setTextSize(19);
        preview.setTextIsSelectable(true);
        preview.setBackgroundColor(Color.rgb(245, 245, 245));
        preview.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.addView(preview);
        box.addView(bigButton("COPIA TESTO", v -> copy(msg)));
        String familyPhone = prefs.getString("family_phone", "");
        box.addView(bigButton("APRI SMS AL FAMILIARE", v -> sendSms(familyPhone, msg)));
        box.addView(bigButton("APRI WHATSAPP AL FAMILIARE", v -> openWhatsApp(familyPhone, msg)));
        box.addView(bigButton("CONTATTI", v -> showContacts()));
        box.addView(bigButton("HOME", v -> showHome()));
        setContentView(scroll);
    }

    private void showSettings() {
        LinearLayout box = column(8);
        EditText person = textField("Nome della persona, es. Mamma", prefs.getString("person_name", "Persona"));
        EditText familyName = textField("Nome familiare, es. Cris", prefs.getString("family_name", "Familiare"));
        EditText familyPhone = phoneField("Cellulare familiare", prefs.getString("family_phone", ""));
        EditText doctorName = textField("Nome medico", prefs.getString("doctor_name", "Medico"));
        EditText doctorPhone = phoneField("Telefono medico", prefs.getString("doctor_phone", ""));
        EditText cauName = textField("Nome CAU", prefs.getString("cau_name", "CAU"));
        EditText cauPhone = phoneField("Telefono CAU", prefs.getString("cau_phone", ""));
        CheckBox voice = new CheckBox(this);
        voice.setText("Leggi le domande ad alta voce");
        voice.setTextSize(18);
        voice.setChecked(prefs.getBoolean("voice", true));
        box.addView(person); box.addView(familyName); box.addView(familyPhone);
        box.addView(doctorName); box.addView(doctorPhone); box.addView(cauName); box.addView(cauPhone); box.addView(voice);

        new AlertDialog.Builder(this)
                .setTitle("IMPOSTAZIONI LOCALI")
                .setMessage("Nessun account: questi dati restano sul telefono.")
                .setView(box)
                .setPositiveButton("SALVA", (d, w) -> {
                    prefs.edit()
                            .putString("person_name", person.getText().toString().trim())
                            .putString("family_name", familyName.getText().toString().trim())
                            .putString("family_phone", familyPhone.getText().toString().trim())
                            .putString("doctor_name", doctorName.getText().toString().trim())
                            .putString("doctor_phone", doctorPhone.getText().toString().trim())
                            .putString("cau_name", cauName.getText().toString().trim())
                            .putString("cau_phone", cauPhone.getText().toString().trim())
                            .putBoolean("voice", voice.isChecked()).apply();
                    showHome();
                })
                .setNeutralButton("CANCELLA DATI LOCALI", (d, w) -> confirmClearLocalData())
                .setNegativeButton("ANNULLA", null).show();
    }

    private void confirmClearLocalData() {
        new AlertDialog.Builder(this)
                .setTitle("Cancellare i dati locali?")
                .setMessage("Verranno cancellati contatti, valori, servizi scelti e note salvati da MEDLAB Care su questo telefono.")
                .setPositiveButton("SÌ, CANCELLA", (d, w) -> {
                    prefs.edit().clear().apply();
                    systolic = diastolic = heartRate = spo2 = null;
                    note = "";
                    breathingOk = chestPressure = swallowOk = null;
                    lastResult = null;
                    toast("Dati locali cancellati");
                    showHome();
                })
                .setNegativeButton("NO", null).show();
    }

    private String composeMessage() {
        return MessageComposer.compose(
                prefs.getString("person_name", "Persona"), now(), lastResult,
                breathingOk, chestPressure, swallowOk,
                systolic, diastolic, heartRate, spo2, note
        );
    }

    private void persistLocalValues() {
        SharedPreferences.Editor e = prefs.edit();
        putNullableInt(e, "systolic", systolic);
        putNullableInt(e, "diastolic", diastolic);
        putNullableInt(e, "heart_rate", heartRate);
        putNullableInt(e, "spo2", spo2);
        e.putString("note", note == null ? "" : note).apply();
    }

    private void restoreLocalValues() {
        systolic = getNullableInt("systolic");
        diastolic = getNullableInt("diastolic");
        heartRate = getNullableInt("heart_rate");
        spo2 = getNullableInt("spo2");
        note = prefs.getString("note", "");
    }

    private void putNullableInt(SharedPreferences.Editor e, String key, Integer value) {
        if (value == null) e.remove(key); else e.putInt(key, value);
    }

    private Integer getNullableInt(String key) {
        return prefs.contains(key) ? prefs.getInt(key, 0) : null;
    }

    private void dial(String phone) {
        if (phone == null || phone.trim().isEmpty()) { toast("Numero non impostato"); return; }
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.trim().replace(" ", ""))));
        } catch (ActivityNotFoundException e) { toast("Nessuna app disponibile per la chiamata"); }
    }

    private void sendSms(String phone, String msg) {
        if (phone == null || phone.trim().isEmpty()) { toast("Numero non impostato"); return; }
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone.trim())));
            intent.putExtra("sms_body", msg);
            startActivity(intent);
        } catch (ActivityNotFoundException e) { toast("Nessuna app SMS disponibile"); }
    }

    private void openWhatsApp(String phone, String msg) {
        if (phone == null || phone.trim().isEmpty()) { toast("Numero non impostato"); return; }
        String digits = phone.replaceAll("[^0-9]", "");
        if (!digits.startsWith("39") && digits.length() <= 10) digits = "39" + digits;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/" + digits + "?text=" + Uri.encode(msg))));
        } catch (ActivityNotFoundException e) { toast("WhatsApp o browser non disponibile"); }
    }

    private void openWeb(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) { toast("Browser non disponibile"); }
    }

    private void copy(String msg) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("MEDLAB Care", msg));
        toast("Messaggio copiato");
    }

    private void speak(String text) {
        if (tts == null || !prefs.getBoolean("voice", true)) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "medlab-care");
    }

    private LinearLayout column(int paddingDp) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return l;
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(30);
        t.setTextColor(Color.BLACK);
        t.setPadding(0, 0, 0, dp(12));
        return t;
    }

    private TextView subtitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTextColor(Color.rgb(70, 70, 70));
        t.setPadding(0, dp(4), 0, dp(10));
        return t;
    }

    private Button bigButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(23);
        b.setMinHeight(dp(68));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(p);
        return b;
    }

    private void styleDanger(Button b) { b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.rgb(180, 30, 30)); }
    private void styleSafe(Button b) { b.setTextColor(Color.rgb(10, 75, 30)); b.setBackgroundColor(Color.rgb(220, 245, 226)); }

    private EditText textField(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value); e.setTextSize(20); e.setMinHeight(dp(58));
        return e;
    }

    private EditText phoneField(String hint, String value) {
        EditText e = textField(hint, value);
        e.setInputType(InputType.TYPE_CLASS_PHONE);
        return e;
    }

    private EditText numberField(String hint, Integer value) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value == null ? "" : String.valueOf(value));
        e.setInputType(InputType.TYPE_CLASS_NUMBER); e.setTextSize(20); e.setMinHeight(dp(58));
        return e;
    }

    private Integer parse(EditText e, int min, int max) {
        try {
            String s = e.getText().toString().trim();
            if (s.isEmpty()) return null;
            int v = Integer.parseInt(s);
            return v >= min && v <= max ? v : null;
        } catch (Exception ex) { return null; }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String now() { return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN).format(new Date()); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
