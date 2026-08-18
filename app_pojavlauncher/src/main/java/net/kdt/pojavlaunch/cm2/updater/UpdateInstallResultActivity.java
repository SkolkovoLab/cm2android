package net.kdt.pojavlaunch.cm2.updater;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import git.artdeell.mojo.R;

/**
 * cm2android: receives the outcome of the launcher update install session.
 *
 * <p>This is an activity rather than a broadcast receiver on purpose. The application wraps its
 * base context in {@link net.kdt.pojavlaunch.utils.LocaleUtils} (a ContextWrapper), while
 * ActivityThread casts that base context to ContextImpl when instantiating a manifest-declared
 * receiver — which crashes the process. Activities get their own context and are unaffected.
 * The package installer also requires a mutable PendingIntent, and Android 14 rejects mutable
 * pending intents built from implicit intents, so the target has to be an explicit component.
 */
public class UpdateInstallResultActivity extends Activity {
    private static final String TAG = "cm2updater";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleResult(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleResult(intent);
        finish();
    }

    private void handleResult(@Nullable Intent intent) {
        if (intent == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // The system wants the user to confirm the installation.
                Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmation == null) {
                    Log.w(TAG, "Install session asked for user action without an intent");
                    return;
                }
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(confirmation);
                break;
            case PackageInstaller.STATUS_SUCCESS:
                Log.i(TAG, "Launcher update installed");
                break;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                // The user declined the installation, no need to nag them about it.
                Log.i(TAG, "Launcher update installation cancelled");
                break;
            default:
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Log.w(TAG, "Launcher update installation failed: status=" + status + ", message=" + message);
                Toast.makeText(getApplicationContext(),
                        getString(R.string.cm2_update_install_failed,
                                message == null ? String.valueOf(status) : message),
                        Toast.LENGTH_LONG).show();
                break;
        }
    }
}
