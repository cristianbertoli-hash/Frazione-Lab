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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final String PREFS = "medlab_care_v01";
    private static final String DISCLAIMER =
            "Bozza non validata clinicamente. Non fa diagnosi e non sostituisce medico, CAU, 112/118 o Pronto Soccorso.";

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
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.ITALIAN);
        }
    }

    private void showHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(20);
        scroll.addView(box);

        TextView appTitle = title("MEDLAB Care");
        appTitle.setGravity(Gravity.CENTER);
        box.addView(appTitle);
        box.addView(subtitle("Nessun account. Nessun Google. Nessun cloud. I dati restano su questo telefono."));

        Button unwell = bigButton("NON STO BENE", v -> startTriage());
        styleDanger(unwell);
        box.addView(unwell);

        Button well = bigButton("STO BENE", v -> markWell());
        styleSafe(well);
        box.addView(well);

        box.addView(bigButton("CHIAMA", v -> showContacts()));
        box.addView(bigButton("MESSAGGIO", v -> showMessage()));
        box.addView(bigButton("VALORI", v -> showVitalsDialog(false)));
        box.addView(bigButton("IMPOSTA CONTATTI", v -> showSettings()));

        Button emergency = bigButton("112 — EMERGENZA", v -> dial("112"));
        styleDanger(emergency);
        box.addView(emergency);

        TextView privacy = subtitle(
                "Privacy V0.1: nessuna registrazione, nessun invio automatico, nessun permesso Internet. " +
                "SMS, WhatsApp e chiamate si aprono solo quando premi tu il relativo pulsante."
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
            } else {
                askChest();
            }
        });
    }

    private void askChest() {
        askYesNo("Domanda 2 di 3", "Hai dolore o pressione al petto?", yes -> {
            chestPressure = yes;
            if (yes) {
                swallowOk = null;
                lastResult = TriageEngine.assess(true, true, true, true);
                showResult();
            } else {
                askSwallow();
            }
        });
    }

    private void askSwallow() {
        askYesNo("Domanda 3 di 3", "Riesci a mandare giù acqua e saliva?", yes -> {
            swallowOk = yes;
            lastResult = TriageEngine.assess(true, false, yes, true);
            showResult();
        });
    }

    private interface AnswerHandler {
        void onAnswer(boolean yes);
    }

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
        if (lastResult.level == TriageEngine.Level.RED) {
            heading = "ROSSO — CHIEDI AIUTO";
        } else if (lastResult.level == TriageEngine.Level.YELLOW) {
            heading = "GIALLO — AVVISA QUALCUNO";
        } else {
            heading = "VERDE — STO BENE";
        }

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

        box.addView(sys);
        box.addView(dia);
        box.addView(hr);
        box.addView(sat);
        box.addView(n);

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
                    Toast.makeText(this, "Valori salvati solo su questo telefono", Toast.LENGTH_LONG).show();
                    if (returnToMessage) showMessage(); else showHome();
                })
                .setNegativeButton("ANNULLA", null)
                .show();
    }

    private void showContacts() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column(18);
        scroll.addView(box);
        box.addView(title("Chi vuoi contattare?"));
        box.addView(contactCard(prefs.getString("family_name", "Familiare"), prefs.getString("family_phone", "")));
        box.addView(contactCard("Medico", prefs.getString("doctor_phone", "")));
        box.addView(contactCard("CAU", prefs.getString("cau_phone", "")));

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
        EditText doctorPhone = phoneField("Telefono medico", prefs.getString("doctor_phone", ""));
        EditText cauPhone = phoneField("Telefono CAU", prefs.getString("cau_phone", ""));
        CheckBox voice = new CheckBox(this);
        voice.setText("Leggi le domande ad alta voce");
        voice.setTextSize(18);
        voice.setChecked(prefs.getBoolean("voice", true));

        box.addView(person);
        box.addView(familyName);
        box.addView(familyPhone);
        box.addView(doctorPhone);
        box.addView(cauPhone);
        box.addView(voice);

        new AlertDialog.Builder(this)
                .setTitle("IMPOSTAZIONI LOCALI")
                .setMessage("Nessun account: questi dati restano sul telefono.")
                .setView(box)
                .setPositiveButton("SALVA", (d, w) -> {
                    prefs.edit()
                            .putString("person_name", person.getText().toString().trim())
                            .putString("family_name", familyName.getText().toString().trim())
                            .putString("family_phone", familyPhone.getText().toString().trim())
                            .putString("doctor_phone", doctorPhone.getText().toString().trim())
                            .putString("cau_phone", cauPhone.getText().toString().trim())
                            .putBoolean("voice", voice.isChecked())
                            .apply();
                    showHome();
                })
                .setNeutralButton("CANCELLA DATI LOCALI", (d, w) -> confirmClearLocalData())
                .setNegativeButton("ANNULLA", null)
                .show();
    }

    private void confirmClearLocalData() {
        new AlertDialog.Builder(this)
                .setTitle("Cancellare i dati locali?")
                .setMessage("Verranno cancellati contatti, valori e note salvati da MEDLAB Care su questo telefono.")
                .setPositiveButton("SÌ, CANCELLA", (d, w) -> {
                    prefs.edit().clear().apply();
                    systolic = diastolic = heartRate = spo2 = null;
                    note = "";
                    breathingOk = chestPressure = swallowOk = null;
                    lastResult = null;
                    Toast.makeText(this, "Dati locali cancellati", Toast.LENGTH_LONG).show();
                    showHome();
                })
                .setNegativeButton("NO", null)
                .show();
    }

    private String composeMessage() {
        return MessageComposer.compose(
                prefs.getString("person_name", "Persona"),
                now(),
                lastResult,
                breathingOk,
                chestPressure,
                swallowOk,
                systolic,
                diastolic,
                heartRate,
                spo2,
                note
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
        if (phone == null || phone.trim().isEmpty()) {
            toast("Numero non impostato");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.trim().replace(" ", ""))));
        } catch (ActivityNotFoundException e) {
            toast("Nessuna app disponibile per la chiamata");
        }
    }

    private void sendSms(String phone, String msg) {
        if (phone == null || phone.trim().isEmpty()) {
            toast("Numero non impostato");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone.trim().replace(" ", "")));
            intent.putExtra("sms_body", msg);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast("Nessuna app SMS disponibile");
        }
    }

    private void openWhatsApp(String phone, String msg) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            toast("Numero non impostato");
            return;
        }
        if (digits.length() <= 10 && !digits.startsWith("39")) digits = "39" + digits;
        try {
            Uri uri = Uri.parse("https://wa.me/" + digits + "?text=" + Uri.encode(msg));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            toast("WhatsApp o un browser non sono disponibili");
        }
    }

    private void copy(String msg) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("MEDLAB Care", msg));
        toast("Messaggio copiato");
    }

    private void speak(String text) {
        if (!prefs.getBoolean("voice", true)) return;
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "medlab-care");
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private LinearLayout column(int paddingDp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return layout;
    }

    private TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.BLACK);
        view.setTextSize(32);
        view.setGravity(Gravity.START);
        view.setPadding(0, 0, 0, dp(14));
        return view;
    }

    private TextView subtitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.DKGRAY);
        view.setTextSize(17);
        view.setPadding(0, dp(4), 0, dp(12));
        return view;
    }

    private Button bigButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(23);
        button.setAllCaps(false);
        button.setMinHeight(dp(78));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(6));
        button.setLayoutParams(params);
        return button;
    }

    private void styleDanger(Button button) {
        button.setBackgroundColor(Color.rgb(179, 38, 30));
        button.setTextColor(Color.WHITE);
    }

    private void styleSafe(Button button) {
        button.setBackgroundColor(Color.rgb(220, 245, 228));
        button.setTextColor(Color.rgb(15, 80, 35));
    }

    private EditText numberField(String hint, Integer value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextSize(20);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (value != null) field.setText(String.valueOf(value));
        return field;
    }

    private EditText textField(String hint, String value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value == null ? "" : value);
        field.setTextSize(20);
        return field;
    }

    private EditText phoneField(String hint, String value) {
        EditText field = textField(hint, value);
        field.setInputType(InputType.TYPE_CLASS_PHONE);
        return field;
    }

    private Integer parse(EditText field, int min, int max) {
        try {
            String raw = field.getText().toString().trim();
            if (raw.isEmpty()) return null;
            int value = Integer.parseInt(raw);
            return value >= min && value <= max ? value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String now() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN).format(new Date());
    }
}
