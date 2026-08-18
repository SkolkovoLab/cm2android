package net.kdt.pojavlaunch.cm2.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

import net.kdt.pojavlaunch.Tools;

import git.artdeell.mojo.R;

/** cm2android: receives the outcome of the launcher update install session. */
public class InstallResultReceiver extends BroadcastReceiver {
    private static final String TAG = "cm2updater";

    @Override
    public void onReceive(Context context, Intent intent) {
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
                context.startActivity(confirmation);
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
                Context applicationContext = context.getApplicationContext();
                Tools.runOnUiThread(() -> Toast.makeText(applicationContext,
                        applicationContext.getString(R.string.cm2_update_install_failed,
                                message == null ? String.valueOf(status) : message),
                        Toast.LENGTH_LONG).show());
                break;
        }
    }
}
