package net.kdt.pojavlaunch.cm2.updater;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.app.PendingIntent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import git.artdeell.mojo.R;

/** cm2android: downloads a launcher release APK and hands it over to the system package installer. */
public final class UpdateInstaller {
    private static final String TAG = "cm2updater";
    private static final String CACHE_SUBDIRECTORY = "launcher_update";
    private static final int BUFFER_SIZE = 65536;

    private UpdateInstaller() {}

    /**
     * Downloads the update APK into the cache directory, reporting progress through
     * {@link ProgressLayout#DOWNLOAD_UPDATE}. Blocking, must not run on the main thread.
     */
    @NonNull
    public static File downloadApk(@NonNull Context context, @NonNull UpdateInfo update) throws IOException {
        File targetDirectory = new File(context.getCacheDir(), CACHE_SUBDIRECTORY);
        // Drop leftovers of previous (possibly partial or outdated) downloads.
        if (targetDirectory.exists()) org.apache.commons.io.FileUtils.deleteDirectory(targetDirectory);
        File targetFile = new File(targetDirectory, "cm2android-" + update.versionName + ".apk");
        FileUtils.ensureParentDirectory(targetFile);

        HttpURLConnection connection = (HttpURLConnection) new URL(update.apkUrl).openConnection();
        try {
            connection.setRequestProperty("User-Agent", DownloadUtils.USER_AGENT);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " + responseCode + ": " + connection.getResponseMessage());
            }

            long totalBytes = update.apkSize > 0 ? update.apkSize : connection.getContentLength();
            byte[] buffer = new byte[BUFFER_SIZE];
            long downloadedBytes = 0;
            int read;
            try (InputStream inputStream = connection.getInputStream();
                 OutputStream outputStream = new FileOutputStream(targetFile)) {
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                    downloadedBytes += read;
                    publishProgress(downloadedBytes, totalBytes);
                }
            }
        } finally {
            connection.disconnect();
        }
        return targetFile;
    }

    private static void publishProgress(long downloadedBytes, long totalBytes) {
        int percentage = totalBytes > 0 ? (int) (downloadedBytes * 100 / totalBytes) : 0;
        ProgressLayout.setProgress(ProgressLayout.DOWNLOAD_UPDATE, percentage,
                R.string.cm2_update_downloading, formatSize(downloadedBytes, totalBytes));
    }

    private static String formatSize(long downloadedBytes, long totalBytes) {
        if (totalBytes <= 0) return String.format(Locale.US, "%.1f MB", downloadedBytes / 1048576f);
        return String.format(Locale.US, "%.1f/%.1f MB", downloadedBytes / 1048576f, totalBytes / 1048576f);
    }

    /**
     * Pushes the APK into a package installer session and commits it. The system then asks the
     * user to confirm the installation; the outcome arrives in {@link InstallResultReceiver}.
     */
    public static void installApk(@NonNull Context context, @NonNull File apkFile) throws IOException {
        Context applicationContext = context.getApplicationContext();
        PackageInstaller packageInstaller = applicationContext.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = packageInstaller.createSession(params);
        Log.i(TAG, "Committing install session " + sessionId + " for " + apkFile.getName());

        try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
            try (InputStream inputStream = new FileInputStream(apkFile);
                 OutputStream outputStream = session.openWrite("cm2android", 0, apkFile.length())) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                session.fsync(outputStream);
            }
            session.commit(createStatusReceiver(applicationContext, sessionId));
        }
    }

    private static IntentSender createStatusReceiver(Context applicationContext, int sessionId) {
        Intent intent = new Intent(applicationContext, InstallResultReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        // The package installer fills the intent in with the install status, so it must stay mutable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(applicationContext, sessionId, intent, flags).getIntentSender();
    }
}
