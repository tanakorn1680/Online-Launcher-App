package com.gtasan.online;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxAdViewAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.joom.paranoid.Obfuscate;

import java.util.concurrent.TimeUnit;

import com.gtasan.online.ui.fragment.SettingsPageFragment;
import com.gtasan.online.utils.SampQuery;
import com.gtasan.online.utils.Utils;

/**
 * Tela principal (novo visual: uma unica tela com imagem de fundo, sem abas
 * embaixo). Tem o botao "Configuracoes" no topo direito, os links sociais na
 * lateral esquerda, e embaixo a direita o card do servidor escolhido (que
 * abre o painel de troca de servidor) ao lado do botao de conectar. Tanto as
 * Configuracoes quanto a escolha de servidor abrem como um painel por cima
 * dessa tela.
 */
@Obfuscate
public class MainActivity extends AppCompatActivity implements MaxAdListener, MaxAdViewAdListener {
    private MaxInterstitialAd interstitialAd = null;

    private int retryAttempt;

    private static final String SERVER1_NAME = "Gta San Online";
    private static final String DEFAULT_PORT = "7777";

    // TODO: preencha com os links reais das redes sociais.
    private static final String DISCORD_URL = "";
    private static final String YOUTUBE_URL = "";
    private static final String INSTAGRAM_URL = "";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler playersRefreshHandler = new Handler(Looper.getMainLooper());
    private static final int PLAYERS_REFRESH_INTERVAL_MS = 15000;

    private View settingsContainer;
    private ImageButton btnCloseSettings;
    private TextView playersOnlineText;
    private boolean settingsOpen = false;

    private android.widget.EditText nicknameField;
    private android.widget.EditText serverField;

    private View chooseServerContainer;
    private TextView server1PlayersText;
    private android.widget.ProgressBar server1Progress;
    private boolean chooseServerOpen = false;

    private final Runnable addressChangedRunnable = this::refreshPlayerCount;
    private final Runnable playersRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshPlayerCount();
            playersRefreshHandler.postDelayed(this, PLAYERS_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        if (!getIntent().getStringExtra("extra_check").equals("nativorpg1337")) {
            Log.e("MainActivity", "Not joined from launcher!");
            finish();
            return;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideSystemUI();

        try {
            if (interstitialAd == null && !Utils.isTester(this)) {
                createInterstitialAd();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error creating interstitialAd: " + e.getMessage());
        }

        ((MaterialTextView) findViewById(R.id.ahahaha)).setText(Utils.copyright);

        if (Utils.isTester(this)) {
            new AlertDialog.Builder(this).setTitle("Tester")
                    .setMessage("You are a tester! You have access to the latest features and updates.")
                    .setPositiveButton("Ok", null).setCancelable(false).show();
        }

        // garante que nenhuma instalacao antiga fique presa no modo offline (removido)
        getSharedPreferences("samp_settings", Context.MODE_PRIVATE).edit()
                .putBoolean("offline_mode", false).apply();

        settingsContainer = findViewById(R.id.settingsContainer);
        btnCloseSettings = findViewById(R.id.btnCloseSettings);
        playersOnlineText = findViewById(R.id.playersOnlineText);

        chooseServerContainer = findViewById(R.id.chooseServerContainer);
        TextView serverPickerName = findViewById(R.id.serverPickerName);
        TextView server1Name = findViewById(R.id.server1_name);
        server1PlayersText = findViewById(R.id.server1_players);
        server1Progress = findViewById(R.id.server1_progress);
        View btnBackChooseServer = findViewById(R.id.btnBackChooseServer);
        View server1Card = findViewById(R.id.server1_card);
        View server2Card = findViewById(R.id.server2_card);
        View server3Card = findViewById(R.id.server3_card);

        serverPickerName.setText(SERVER1_NAME);
        server1Name.setText(SERVER1_NAME);

        ImageButton btnDiscord = findViewById(R.id.btnDiscord);
        ImageButton btnYoutube = findViewById(R.id.btnYoutube);
        ImageButton btnInstagram = findViewById(R.id.btnInstagram);
        View serverPickerCard = findViewById(R.id.serverPickerCard);
        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        MaterialButton btnConnect = findViewById(R.id.btnConnect);

        nicknameField = findViewById(R.id.nicknameField);
        String savedNick = getSharedPreferences("samp_settings", Context.MODE_PRIVATE).getString("nick_name", "");
        nicknameField.setText(savedNick);

        serverField = findViewById(R.id.serverField);
        serverField.setText(getSharedPreferences("launcher_ui", Context.MODE_PRIVATE)
                .getString("server_address", ""));
        serverField.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                // Re-query shortly after typing stops instead of waiting for the next 15s tick.
                playersRefreshHandler.removeCallbacks(addressChangedRunnable);
                playersRefreshHandler.postDelayed(addressChangedRunnable, 800);
            }
        });

        btnDiscord.setOnClickListener(v -> openLink(DISCORD_URL));
        btnYoutube.setOnClickListener(v -> openLink(YOUTUBE_URL));
        btnInstagram.setOnClickListener(v -> openLink(INSTAGRAM_URL));

        serverPickerCard.setOnClickListener(v -> openChooseServerPanel());
        btnBackChooseServer.setOnClickListener(v -> closeChooseServerPanel());

        // servidor 1 e' o unico ativo no momento, entao so fecha o painel
        server1Card.setOnClickListener(v -> closeChooseServerPanel());

        View.OnClickListener comingSoonListener = v -> Toast.makeText(
                this, R.string.coming_soon_message, Toast.LENGTH_LONG).show();
        server2Card.setOnClickListener(comingSoonListener);
        server3Card.setOnClickListener(comingSoonListener);

        btnSettings.setOnClickListener(v -> openSettingsPanel());
        btnCloseSettings.setOnClickListener(v -> closeSettingsPanel());
        btnConnect.setOnClickListener(v -> connectToServer1());
    }

    @Override
    protected void onResume() {
        super.onResume();
        playersRefreshHandler.post(playersRefreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        playersRefreshHandler.removeCallbacks(playersRefreshRunnable);
        playersRefreshHandler.removeCallbacks(addressChangedRunnable);
        saveServerAddress();
    }

    private void openLink(String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, R.string.coming_soon_message, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads the HOST:PORT field. Returns {host, port}, or null when the field is
     * empty or invalid. A bare host (no ":port") uses the default SA-MP port 7777.
     */
    private String[] parseServerAddress() {
        String text = serverField.getText() != null ? serverField.getText().toString().trim() : "";
        if (text.isEmpty() || text.contains(" ")) return null;

        String host = text;
        String port = DEFAULT_PORT;
        int colon = text.lastIndexOf(':');
        if (colon >= 0) {
            host = text.substring(0, colon);
            port = text.substring(colon + 1);
        }
        if (host.isEmpty()) return null;

        try {
            int p = Integer.parseInt(port);
            if (p < 1 || p > 65535) return null;
            port = String.valueOf(p);
        } catch (NumberFormatException e) {
            return null;
        }
        return new String[]{host, port};
    }

    private void saveServerAddress() {
        if (serverField == null || serverField.getText() == null) return;
        getSharedPreferences("launcher_ui", Context.MODE_PRIVATE).edit()
                .putString("server_address", serverField.getText().toString().trim()).apply();
    }

    /**
     * Consulta o servidor 1 em segundo plano e atualiza o contador de players
     * que fica no card ao lado do botao de conectar (ex: "10/200") e, se o
     * painel de escolha de servidor estiver com as views infladas, tambem
     * atualiza o card do servidor 1 la dentro.
     */
    private void refreshPlayerCount() {
        final String[] addr = parseServerAddress();
        if (addr == null) {
            playersOnlineText.setText(R.string.players_online_unknown);
            server1PlayersText.setText(R.string.players_count_unknown);
            return;
        }
        final String host = addr[0];
        final int port = Integer.parseInt(addr[1]);
        final String key = host + ":" + port;

        new Thread(() -> {
            try {
                SampQuery query = new SampQuery(host, port);
                if (query.isOnline()) {
                    String[] info = query.getInfo();
                    if (info != null) {
                        String text = getString(R.string.players_count_format, info[1], info[2]);
                        int current = parsePlayersOrZero(info[1]);
                        int max = parsePlayersOrZero(info[2]);
                        mainHandler.post(() -> {
                            if (!isCurrentAddress(key)) return; // user changed the address meanwhile
                            playersOnlineText.setText(text);
                            server1PlayersText.setText(text);
                            if (max > 0) {
                                server1Progress.setMax(max);
                                server1Progress.setProgress(current);
                            }
                        });
                        return;
                    }
                }
                mainHandler.post(() -> {
                    if (!isCurrentAddress(key)) return;
                    playersOnlineText.setText(R.string.players_online_unknown);
                    server1PlayersText.setText(R.string.players_count_unknown);
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (!isCurrentAddress(key)) return;
                    playersOnlineText.setText(R.string.players_online_unknown);
                    server1PlayersText.setText(R.string.players_count_unknown);
                });
            }
        }).start();
    }

    private boolean isCurrentAddress(String key) {
        String[] now = parseServerAddress();
        return now != null && key.equals(now[0] + ":" + now[1]);
    }

    private int parsePlayersOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Abre o painel de escolha de servidor (tela cheia, por cima da tela
     * principal, igual ao painel de configuracoes). Servidor 1 e' o unico
     * funcional; 2 e 3 mostram "em breve".
     */
    private void openChooseServerPanel() {
        chooseServerContainer.setVisibility(View.VISIBLE);
        chooseServerOpen = true;
    }

    private void closeChooseServerPanel() {
        chooseServerContainer.setVisibility(View.GONE);
        chooseServerOpen = false;
    }

    /**
     * Conecta direto no servidor 1 usando o nickname que ja esta preenchido no
     * campo do canto superior esquerdo da tela principal. Nao existe mais a
     * tela/dialogo separado pedindo o nick - o botao Conectar so verifica se
     * o campo ja tem um nick valido antes de entrar.
     */
    private void connectToServer1() {
        if (!Utils.isOnline(this)) {
            Toast.makeText(this, "No internet connection!", Toast.LENGTH_LONG).show();
            return;
        }

        String nickname = nicknameField.getText() != null ? nicknameField.getText().toString().trim() : "";

        if (nickname.isEmpty()) {
            Toast.makeText(this, "Please set nickname.", Toast.LENGTH_LONG).show();
            return;
        }

        if (nickname.length() > 24) {
            Toast.makeText(this, "Nickname can't be longer than 24 characters.", Toast.LENGTH_LONG).show();
            return;
        }

        String[] addr = parseServerAddress();
        if (addr == null) {
            Toast.makeText(this, "Please enter a valid server address (IP:PORT).", Toast.LENGTH_LONG).show();
            return;
        }
        String host = addr[0];
        String port = addr[1];

        if (Utils.isServerBanned(host, port)) {
            Toast.makeText(this, "This server is banned on this launcher.", Toast.LENGTH_LONG).show();
            return;
        }

        getSharedPreferences("samp_settings", Context.MODE_PRIVATE).edit()
                .putString("nick_name", nickname).apply();

        SharedPreferences.Editor edit = getSharedPreferences("samp_server", Context.MODE_PRIVATE).edit();
        edit.putString("host", host);
        edit.putString("port", port);
        edit.putString("password", "");
        edit.apply();

        saveServerAddress();
        Utils.saveSettings(this);

        try {
            Intent intent = new Intent(this, com.gtasan.online.game.SAMP.class);
            intent.putExtra("extra_check", "nativorpg1337");
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Could not start the game: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void openSettingsPanel() {
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.settingsContainer, SettingsPageFragment.class, null)
                .commit();
        settingsContainer.setVisibility(View.VISIBLE);
        btnCloseSettings.setVisibility(View.VISIBLE);
        settingsOpen = true;
    }

    private void closeSettingsPanel() {
        settingsContainer.setVisibility(View.GONE);
        btnCloseSettings.setVisibility(View.GONE);
        settingsOpen = false;
    }

    /**
     * Esconde a barra de status e a barra de navegacao do Android, deixando a
     * tela cheia (igual ao que ja acontece quando o jogo abre). Precisa ser
     * chamado de novo no onWindowFocusChanged porque o Android tende a
     * restaurar as barras quando o usuario troca de app e volta.
     */
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    void createInterstitialAd() {
        interstitialAd = new MaxInterstitialAd("YOUR_AD_UNIT_ID", this);
        interstitialAd.setListener(this);

        // Load the first ad
        interstitialAd.loadAd();
    }

    // MAX Ad Listener
    @Override
    public void onAdLoaded(final MaxAd maxAd) {
        // Interstitial ad is ready to be shown. interstitialAd.isReady() will now return 'true'

        // Reset retry attempt
        retryAttempt = 0;
    }

    @Override
    public void onAdLoadFailed(final String adUnitId, final MaxError error) {
        // Interstitial ad failed to load
        // AppLovin recommends that you retry with exponentially higher delays up to a maximum delay (in this case 64 seconds)

        retryAttempt++;
        long delayMillis = TimeUnit.SECONDS.toMillis((long) Math.pow(2, Math.min(6, retryAttempt)));

        new Handler().postDelayed(() -> interstitialAd.loadAd(), delayMillis);
    }

    @Override
    public void onAdDisplayFailed(final MaxAd maxAd, final MaxError error) {
        // Interstitial ad failed to display. AppLovin recommends that you load the next ad.
        interstitialAd.loadAd();
    }

    @Override
    public void onAdDisplayed(final MaxAd maxAd) {
    }

    @Override
    public void onAdClicked(final MaxAd maxAd) {
    }

    @Override
    public void onAdHidden(final MaxAd maxAd) {
        // Interstitial ad is hidden. Pre-load the next ad
        interstitialAd.loadAd();
    }

    @Override
    public void onAdExpanded(final MaxAd maxAd) {
    }

    @Override
    public void onAdCollapsed(final MaxAd maxAd) {
    }

    @Override
    public void onBackPressed() {
        if (chooseServerOpen) {
            closeChooseServerPanel();
            return;
        }

        if (settingsOpen) {
            closeSettingsPanel();
            return;
        }

        super.onBackPressed();
        try {
            if (interstitialAd.isReady()) {
                interstitialAd.showAd();
            } else {
                Log.e("interstitialAd", "interstitialAd not ready!");
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error showing interstitialAd: " + e.getMessage());
        }

        quitApp();
    }

    private long quit_time = 0;

    public void quitApp() {
        if ((System.currentTimeMillis() - quit_time) > 2000) {
            Toast.makeText(this, "Press again to quit.", Toast.LENGTH_LONG).show();
            quit_time = System.currentTimeMillis();
        } else {
            finish();
        }
    }
}
