package com.gtasan.online;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.applovin.sdk.AppLovinSdk;
import com.google.android.material.textview.MaterialTextView;
import com.joom.paranoid.Obfuscate;

import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import pub.devrel.easypermissions.EasyPermissions;
import com.gtasan.online.utils.Logcat;
import com.gtasan.online.utils.Utils;

@Obfuscate
public class SplashActivity extends AppCompatActivity implements GLSurfaceView.Renderer, EasyPermissions.PermissionCallbacks {
    private static final int PERMISSION_REQUEST_CODE = 1337;
    private static String[] permissions = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };

    GLSurfaceView glSurfaceView;

    Logcat logcat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        hideSystemUI();

        getExternalFilesDir(null);
        getObbDir();
        getExternalMediaDirs();

        logcat = new Logcat(this);
        logcat.start();

        try {
            if (!Utils.isTester(this)) {
                AppLovinSdk.getInstance(this).setMediationProvider("max");
                AppLovinSdk.initializeSdk(this, configuration -> Log.i("AppLovinSdk", "initialized!"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        ((MaterialTextView) findViewById(R.id.ahahaha)).setText(Utils.copyright);

        glSurfaceView = findViewById(R.id.surface);
        glSurfaceView.setRenderer(this);

        Toast.makeText(this, "Nativo SA-MP Mobile v" + BuildConfig.VERSION_NAME, Toast.LENGTH_SHORT).show();

        if (EasyPermissions.hasPermissions(this, permissions)) {
            startApp();
        } else {
            EasyPermissions.requestPermissions(this, "Allow app permisions", PERMISSION_REQUEST_CODE, permissions);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //logcat.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> list) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            startApp();
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> list) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Toast.makeText(this, "App permissions not granted! (try to give from app settings)", Toast.LENGTH_LONG).show();
            startApp();
        }
    }

    private void startApp() {
        if (!Utils.isOnline(this)) {
            // dialog
            new AlertDialog.Builder(this)
                    .setTitle("No internet connection!")
                    .setMessage("Please connect to the internet and try again.")
                    .setPositiveButton("Retry", (dialog, which) -> {
                        dialog.dismiss();
                        startApp();
                    }).setCancelable(false).show();
            return;
        }

        // Selecao Lite/Full removida: sempre usamos um unico pacote de dados completo.
        getSharedPreferences("samp_settings", Context.MODE_PRIVATE).edit().putString("files_type", "full").apply();

        // Game file checks and the forced-app-update prompt used to run here (via
        // UpdateService) before the player could reach the main screen. That's now
        // handled from MainActivity instead: the player can open the server list
        // right away, and downloads the game files from there whenever they want.
        new Handler().postDelayed(this::startMain, 2500);
    }

    private void startMain() {
        if (!isFinishing()) {
            try {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                intent.putExtra("extra_check", "nativorpg1337");
                startActivity(intent);
                finish();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        String renderer = gl10.glGetString(gl10.GL_RENDERER);
        Log.i("SplashActivity", "Renderer: " + renderer);

        String extensions = gl10.glGetString(gl10.GL_EXTENSIONS);
        Log.i("SplashActivity", "Extensions: " + extensions);

        Utils.GPUType type;

        if (extensions.contains("GL_IMG_texture_compression_pvrtc")) {
            type = Utils.GPUType.PVR;
        } else if (extensions.contains("GL_EXT_texture_compression_dxt1") || extensions.contains("GL_EXT_texture_compression_s3tc") || extensions.contains("GL_AMD_compressed_ATC_texture")) {
            type = Utils.GPUType.DXT;
        } else {
            type = Utils.GPUType.ETC;
        }

        /*if (extensions.contains("pvr")) {
            type = Utils.GPUType.PVR;
        } else if (extensions.contains("dxt")) {
            type = Utils.GPUType.DXT;
        } else {
            type = Utils.GPUType.ETC;
        }*/

        Utils.GPU_TYPE = type;
        Log.i("SplashActivity", "GPU_TYPE: " + Utils.GPU_TYPE.name());

        int error = gl10.glGetError();
        if (error != gl10.GL_NO_ERROR) {
            Log.e("SplashActivity", "OpenGL error: " + error);
        }

        runOnUiThread(() -> glSurfaceView.setVisibility(View.GONE));
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int i, int i1) {

    }

    @Override
    public void onDrawFrame(GL10 gl10) {

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
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
}
