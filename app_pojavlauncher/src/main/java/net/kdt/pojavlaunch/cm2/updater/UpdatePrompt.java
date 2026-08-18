package net.kdt.pojavlaunch.cm2.updater;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import git.artdeell.mojo.BuildConfig;
import git.artdeell.mojo.R;

/**
 * cm2android: checks GitHub releases on launcher startup and offers to install a newer build.
 *
 * <p>Declining only dismisses the dialog: the next launcher start asks again.
 */
public final class UpdatePrompt {
    private static final String TAG = "cm2updater";
    /** Release notes longer than this are cut off to keep the dialog usable. */
    private static final int MAX_RELEASE_NOTES_LENGTH = 500;

    private static final AtomicBoolean sCheckStarted = new AtomicBoolean(false);
    private static final AtomicBoolean sUpdateRunning = new AtomicBoolean(false);
    @Nullable private static volatile UpdateInfo sPendingUpdate;
    private static boolean sDialogShown = false;

    private UpdatePrompt() {}

    /** Runs the update check once per process, then asks the user through the visible activity. */
    public static void checkAsync() {
        if (!sCheckStarted.compareAndSet(false, true)) return;
        sExecutorService.execute(() -> {
            UpdateInfo update = UpdateChecker.checkForUpdate();
            if (update == null) return;
            Log.i(TAG, "Update available: " + update.versionName + " (installed: " + BuildConfig.VERSION_NAME + ")");
            sPendingUpdate = update;
            // If no activity is around yet, the next onResume() picks the update up instead.
            ContextExecutor.executeActivity(UpdatePrompt::showPendingUpdate);
        });
    }

    /**
     * Shows the update dialog if an update was found and the user has not answered it yet in this
     * process. Safe to call on every activity resume.
     */
    public static void showPendingUpdate(@NonNull Activity activity) {
        UpdateInfo update = sPendingUpdate;
        if (update == null || sDialogShown) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        sDialogShown = true;

        new AlertDialog.Builder(activity)
                .setTitle(R.string.cm2_update_dialog_title)
                .setMessage(buildDialogMessage(activity, update))
                .setPositiveButton(R.string.cm2_update_dialog_update, (dialog, which) -> startUpdate(activity, update))
                .setNegativeButton(R.string.cm2_update_dialog_later, null)
                .show();
    }

    private static String buildDialogMessage(Activity activity, UpdateInfo update) {
        String message = activity.getString(R.string.cm2_update_dialog_message,
                update.versionName, BuildConfig.VERSION_NAME);
        if (update.releaseNotes.isEmpty()) return message;
        String notes = update.releaseNotes.length() > MAX_RELEASE_NOTES_LENGTH
                ? update.releaseNotes.substring(0, MAX_RELEASE_NOTES_LENGTH) + "…"
                : update.releaseNotes;
        return message + "\n\n" + notes;
    }

    private static void startUpdate(@NonNull Activity activity, @NonNull UpdateInfo update) {
        if (!canInstallPackages(activity)) {
            requestInstallPermission(activity);
            return;
        }
        if (!sUpdateRunning.compareAndSet(false, true)) return;

        Context applicationContext = activity.getApplicationContext();
        sExecutorService.execute(() -> {
            try {
                File apkFile;
                try {
                    apkFile = UpdateInstaller.downloadApk(applicationContext, update);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to download the launcher update", e);
                    toast(applicationContext, applicationContext.getString(R.string.cm2_update_download_failed));
                    return;
                }
                Log.i(TAG, "Update downloaded to " + apkFile + ", handing it to the package installer");
                UpdateInstaller.installApk(applicationContext, apkFile);
            } catch (Exception e) {
                Log.e(TAG, "Failed to install the launcher update", e);
                toast(applicationContext,
                        applicationContext.getString(R.string.cm2_update_install_failed, e.toString()));
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.DOWNLOAD_UPDATE);
                sUpdateRunning.set(false);
            }
        });
    }

    private static boolean canInstallPackages(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return activity.getPackageManager().canRequestPackageInstalls();
    }

    /**
     * Sends the user to the "install unknown apps" system screen. The dialog is re-armed so that
     * coming back to the launcher offers the update again.
     */
    private static void requestInstallPermission(@NonNull Activity activity) {
        sDialogShown = false;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.cm2_update_dialog_title)
                .setMessage(R.string.cm2_update_unknown_sources)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    try {
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        Log.w(TAG, "Unable to open the unknown app sources screen", e);
                        toast(activity.getApplicationContext(),
                                activity.getString(R.string.cm2_update_install_failed, e.toString()));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void toast(Context context, String message) {
        Tools.runOnUiThread(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }
}
