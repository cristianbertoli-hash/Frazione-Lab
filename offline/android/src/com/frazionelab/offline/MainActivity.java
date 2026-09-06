package com.frazionelab.offline;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int OPEN_ORIGINAL = 10;
    private static final int SAVE_CONTAINER = 11;
    private static final int OPEN_CONTAINER = 12;
    private static final int SAVE_ORIGINAL = 13;
    private static final int MAX_FILE_BYTES = 100 * 1024 * 1024;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private byte[] originalBytes;
    private String originalName;
    private String originalMime;
    private byte[] pendingContainer;
    private String pendingContainerName;

    private byte[] loadedContainer;
    private Fra1Codec.ContainerInfo loadedInfo;
    private Fra1Codec.OriginalFile restored;

    private TextView originalInfo;
    private TextView originalStatus;
    private TextView restoreInfo;
    private TextView restoreStatus;
    private Button makeFra1Button;
    private Button makeFra1e128Button;
    private Button makeFra1e256Button;
    private Button decryptButton;
    private Button saveOriginalButton;
    private EditText encryptPassword;
    private EditText decryptPassword;
    private EditText textBox;
    private EditText fractionBox;
    private TextView textStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Frazione Lab Offline");
        buildUi();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(36));
        scroll.addView(root);

        TextView title = text("Frazione Lab", 30, true);
        root.addView(title);
        TextView subtitle = text("Offline • FRA1 lossless • FRA1E AES-128/256", 14, false);
        subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle, marginBottom(18));

        sectionTitle(root, "Foto originale → FRA1 / FRA1E");
        root.addView(text("Il file viene letto byte-per-byte. Nessun ridimensionamento, ricampionamento o upload.", 14, false));
        Button chooseOriginal = button("SCEGLI FOTO ORIGINALE");
        chooseOriginal.setOnClickListener(v -> openOriginal());
        root.addView(chooseOriginal, marginTop(10));
        originalInfo = text("Nessuna foto scelta", 14, false);
        root.addView(originalInfo, marginTop(8));

        makeFra1Button = button("CREA E SALVA .FRA1");
        makeFra1Button.setEnabled(false);
        makeFra1Button.setOnClickListener(v -> createAndSaveContainer(false, 0));
        root.addView(makeFra1Button, marginTop(10));

        root.addView(text("Protezione con password (la password non viene salvata):", 14, true), marginTop(14));
        encryptPassword = passwordEdit("Password per FRA1E");
        root.addView(encryptPassword, marginTop(6));

        LinearLayout cryptoRow = new LinearLayout(this);
        cryptoRow.setOrientation(LinearLayout.HORIZONTAL);
        makeFra1e128Button = button("FRA1E AES-128");
        makeFra1e256Button = button("FRA1E AES-256");
        makeFra1e128Button.setEnabled(false);
        makeFra1e256Button.setEnabled(false);
        makeFra1e128Button.setOnClickListener(v -> createAndSaveContainer(true, 128));
        makeFra1e256Button.setOnClickListener(v -> createAndSaveContainer(true, 256));
        cryptoRow.addView(makeFra1e128Button, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams cryptoSecond = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        cryptoSecond.leftMargin = dp(8);
        cryptoRow.addView(makeFra1e256Button, cryptoSecond);
        root.addView(cryptoRow, marginTop(8));
        root.addView(text("FRA1E usa AES-GCM autenticato e PBKDF2-SHA-256 (600.000 iterazioni). AES-128 e AES-256 hanno lo stesso overhead: 64 byte.", 12, false), marginTop(6));

        originalStatus = statusView();
        root.addView(originalStatus, marginTop(8));

        divider(root);
        sectionTitle(root, "FRA1 / FRA1E → recupera originale");
        root.addView(text("L'app riconosce automaticamente FRA1 normale oppure FRA1E AES-128/AES-256 leggendo l'intestazione del file.", 14, false));
        Button chooseContainer = button("SCEGLI FILE .FRA1 / .FRA1E");
        chooseContainer.setOnClickListener(v -> openContainer());
        root.addView(chooseContainer, marginTop(10));
        restoreInfo = text("Nessun file scelto", 14, false);
        root.addView(restoreInfo, marginTop(8));

        decryptPassword = passwordEdit("Password (solo se FRA1E)");
        root.addView(decryptPassword, marginTop(8));
        decryptButton = button("DECIFRA FRA1E");
        decryptButton.setEnabled(false);
        decryptButton.setOnClickListener(v -> decryptLoadedContainer());
        root.addView(decryptButton, marginTop(8));

        saveOriginalButton = button("RECUPERA E SALVA ORIGINALE");
        saveOriginalButton.setEnabled(false);
        saveOriginalButton.setOnClickListener(v -> saveRestored());
        root.addView(saveOriginalButton, marginTop(8));
        restoreStatus = statusView();
        root.addView(restoreStatus, marginTop(8));

        divider(root);
        sectionTitle(root, "Testo ⇄ frazione");
        textBox = edit("Scrivi un testo…", 4);
        root.addView(textBox, marginTop(6));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button encodeText = button("CODIFICA");
        Button decodeText = button("DECODIFICA");
        row.addView(encodeText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        second.leftMargin = dp(8);
        row.addView(decodeText, second);
        root.addView(row, marginTop(10));
        fractionBox = edit("N / 2^K", 5);
        root.addView(fractionBox, marginTop(8));
        textStatus = statusView();
        root.addView(textStatus, marginTop(8));
        encodeText.setOnClickListener(v -> {
            try {
                fractionBox.setText(Fra1Codec.encodeTextFraction(textBox.getText().toString()));
                setStatus(textStatus, "Testo codificato.", false);
            } catch (Exception e) { setStatus(textStatus, message(e), true); }
        });
        decodeText.setOnClickListener(v -> {
            try {
                textBox.setText(Fra1Codec.decodeTextFraction(fractionBox.getText().toString()));
                setStatus(textStatus, "Frazione decodificata.", false);
            } catch (Exception e) { setStatus(textStatus, message(e), true); }
        });

        divider(root);
        TextView privacy = text("PRIVACY: questa app non dichiara il permesso INTERNET. Foto, FRA1, FRA1E e password restano sul dispositivo salvo quando scegli tu dove salvare o condividere i file.", 13, true);
        root.addView(privacy);
        setContentView(scroll);
    }

    private void openOriginal() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, OPEN_ORIGINAL);
    }

    private void openContainer() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, OPEN_CONTAINER);
    }

    private void createAndSaveContainer(boolean encrypted, int keyBits) {
        if (originalBytes == null) return;
        final String password = encryptPassword.getText().toString();
        if (encrypted && password.isEmpty()) {
            setStatus(originalStatus, "Inserisci una password prima di creare FRA1E.", true);
            return;
        }
        setCreateButtonsEnabled(false);
        setStatus(originalStatus, encrypted ? "Creo FRA1 e cifro con AES-" + keyBits + "…" : "Calcolo SHA-256 e creo FRA1…", false);
        worker.execute(() -> {
            try {
                byte[] fra1 = Fra1Codec.packOriginalFile(originalName, originalMime, originalBytes);
                byte[] packet = encrypted ? Fra1Codec.encryptFra1(fra1, password, keyBits) : fra1;
                pendingContainer = packet;
                pendingContainerName = originalName + (encrypted ? ".fra1e" : ".fra1");
                int overhead = packet.length - fra1.length;
                main.post(() -> {
                    setCreateButtonsEnabled(true);
                    if (encrypted) {
                        setStatus(originalStatus, "FRA1E AES-" + keyBits + " creato • " + sizeText(packet.length) + " • overhead cifratura " + overhead + " byte. Scegli dove salvarlo.", false);
                    } else {
                        setStatus(originalStatus, "FRA1 creato • " + sizeText(packet.length) + ". Scegli dove salvarlo.", false);
                    }
                    launchSaveContainer();
                });
            } catch (Exception e) {
                main.post(() -> {
                    setCreateButtonsEnabled(true);
                    setStatus(originalStatus, message(e), true);
                });
            }
        });
    }

    private void launchSaveContainer() {
        if (pendingContainer == null || pendingContainerName == null) return;
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("application/octet-stream");
        save.putExtra(Intent.EXTRA_TITLE, pendingContainerName);
        startActivityForResult(save, SAVE_CONTAINER);
    }

    private void saveRestored() {
        if (restored == null) return;
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType(restored.mime == null || restored.mime.isEmpty() ? "application/octet-stream" : restored.mime);
        save.putExtra(Intent.EXTRA_TITLE, restored.name);
        startActivityForResult(save, SAVE_ORIGINAL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == OPEN_ORIGINAL) loadOriginal(uri);
        else if (requestCode == SAVE_CONTAINER) writeBytes(uri, pendingContainer, originalStatus, pendingContainerName + " salvato.");
        else if (requestCode == OPEN_CONTAINER) loadContainer(uri);
        else if (requestCode == SAVE_ORIGINAL && restored != null) writeBytes(uri, restored.bytes, restoreStatus, "Originale salvato byte-per-byte ✓");
    }

    private void loadOriginal(Uri uri) {
        originalBytes = null;
        pendingContainer = null;
        pendingContainerName = null;
        setCreateButtonsEnabled(false);
        String name = displayName(uri);
        String mime = getContentResolver().getType(uri);
        originalName = (name == null || name.isEmpty()) ? "foto" : name;
        originalMime = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
        setStatus(originalStatus, "Leggo il file originale…", false);
        worker.execute(() -> {
            try {
                byte[] bytes = readBytes(uri, MAX_FILE_BYTES);
                originalBytes = bytes;
                String hash = Fra1Codec.sha256Hex(bytes);
                main.post(() -> {
                    originalInfo.setText(originalName + " • " + sizeText(bytes.length) + " • " + originalMime);
                    setCreateButtonsEnabled(true);
                    setStatus(originalStatus, "Pronta • SHA-256 " + shortHash(hash), false);
                });
            } catch (Exception e) {
                main.post(() -> setStatus(originalStatus, message(e), true));
            }
        });
    }

    private void loadContainer(Uri uri) {
        loadedContainer = null;
        loadedInfo = null;
        restored = null;
        decryptButton.setEnabled(false);
        saveOriginalButton.setEnabled(false);
        String selectedName = displayName(uri);
        restoreInfo.setText((selectedName == null ? "File" : selectedName) + " • analisi in corso…");
        setStatus(restoreStatus, "Riconosco automaticamente il formato…", false);
        worker.execute(() -> {
            try {
                byte[] packet = readBytes(uri, MAX_FILE_BYTES + 1024 * 1024);
                Fra1Codec.ContainerInfo info = Fra1Codec.inspectContainer(packet);
                if ("unknown".equals(info.kind)) throw new Fra1Codec.Fra1Exception("Il file non è FRA1/FRA1E riconosciuto");
                loadedContainer = packet;
                loadedInfo = info;
                if (info.encrypted) {
                    main.post(() -> {
                        restoreInfo.setText((selectedName == null ? "FRA1E" : selectedName) + " • " + sizeText(packet.length) + " • AES-" + info.keyBits);
                        decryptButton.setEnabled(true);
                        setStatus(restoreStatus, "FRA1E AES-" + info.keyBits + " riconosciuto automaticamente • PBKDF2 " + info.iterations + " iterazioni. Inserisci la password.", false);
                    });
                } else {
                    Fra1Codec.OriginalFile decoded = Fra1Codec.unpackOriginalFile(packet);
                    restored = decoded;
                    main.post(() -> showRestored(decoded, "FRA1 normale riconosciuto • SHA-256 verificato ✓"));
                }
            } catch (Exception e) {
                main.post(() -> setStatus(restoreStatus, message(e), true));
            }
        });
    }

    private void decryptLoadedContainer() {
        if (loadedContainer == null || loadedInfo == null || !loadedInfo.encrypted) return;
        final String password = decryptPassword.getText().toString();
        if (password.isEmpty()) {
            setStatus(restoreStatus, "Inserisci la password del FRA1E.", true);
            return;
        }
        decryptButton.setEnabled(false);
        saveOriginalButton.setEnabled(false);
        setStatus(restoreStatus, "Derivo la chiave e decifro AES-" + loadedInfo.keyBits + "…", false);
        worker.execute(() -> {
            try {
                byte[] fra1 = Fra1Codec.decryptFra1(loadedContainer, password);
                Fra1Codec.OriginalFile decoded = Fra1Codec.unpackOriginalFile(fra1);
                restored = decoded;
                main.post(() -> {
                    decryptButton.setEnabled(true);
                    showRestored(decoded, "Password corretta • AES-" + loadedInfo.keyBits + " decifrato • SHA-256 verificato ✓");
                });
            } catch (Exception e) {
                restored = null;
                main.post(() -> {
                    decryptButton.setEnabled(true);
                    saveOriginalButton.setEnabled(false);
                    setStatus(restoreStatus, message(e), true);
                });
            }
        });
    }

    private void showRestored(Fra1Codec.OriginalFile decoded, String status) {
        restoreInfo.setText(decoded.name + " • " + sizeText(decoded.bytes.length) + " • " + decoded.mime);
        saveOriginalButton.setEnabled(true);
        setStatus(restoreStatus, status + " • file identico byte-per-byte pronto da salvare.", false);
    }

    private void setCreateButtonsEnabled(boolean enabled) {
        boolean ready = enabled && originalBytes != null;
        makeFra1Button.setEnabled(ready);
        makeFra1e128Button.setEnabled(ready);
        makeFra1e256Button.setEnabled(ready);
    }

    private void writeBytes(Uri uri, byte[] bytes, TextView target, String success) {
        if (bytes == null) return;
        setStatus(target, "Salvataggio…", false);
        worker.execute(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IOException("Impossibile aprire il file di destinazione");
                out.write(bytes);
                out.flush();
                main.post(() -> setStatus(target, success, false));
            } catch (Exception e) {
                main.post(() -> setStatus(target, message(e), true));
            }
        });
    }

    private byte[] readBytes(Uri uri, int maxBytes) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("Impossibile aprire il file");
            byte[] buffer = new byte[64 * 1024];
            int total = 0, n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > maxBytes) throw new IOException("File oltre il limite di " + sizeText(maxBytes));
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private String displayName(Uri uri) {
        ContentResolver r = getContentResolver();
        try (Cursor c = r.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return c.getString(index);
            }
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }

    private static String sizeText(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(java.util.Locale.ITALY, "%.1f KB", n / 1024.0);
        return String.format(java.util.Locale.ITALY, "%.2f MB", n / 1024.0 / 1024.0);
    }

    private static String shortHash(String h) { return h.length() <= 20 ? h : h.substring(0, 20) + "…"; }
    private static String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }

    private void setStatus(TextView v, String msg, boolean error) {
        v.setText(msg == null ? "" : msg);
        v.setTextColor(error ? Color.rgb(190, 30, 45) : Color.rgb(20, 110, 65));
    }

    private TextView statusView() { return text("", 14, true); }
    private void sectionTitle(LinearLayout root, String s) { root.addView(text(s, 21, true), marginBottom(8)); }
    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(28, 28, 30));
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }
    private EditText edit(String hint, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setMinLines(lines);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setTextSize(14);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }
    private EditText passwordEdit(String hint) {
        EditText e = edit(hint, 1);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }
    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }
    private void divider(LinearLayout root) {
        View v = new View(this);
        v.setBackgroundColor(Color.LTGRAY);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        p.topMargin = dp(22);
        p.bottomMargin = dp(22);
        root.addView(v, p);
    }
    private LinearLayout.LayoutParams marginTop(int value) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(value);
        return p;
    }
    private LinearLayout.LayoutParams marginBottom(int value) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(value);
        return p;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
