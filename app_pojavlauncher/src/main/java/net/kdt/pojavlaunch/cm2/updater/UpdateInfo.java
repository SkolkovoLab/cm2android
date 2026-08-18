package net.kdt.pojavlaunch.cm2.updater;

import androidx.annotation.NonNull;

/** A launcher release that is newer than the installed one. */
public final class UpdateInfo {
    /** Human-readable version, without the leading "v" (e.g. "1.2.3"). */
    public final String versionName;
    /** Version code derived from the release tag, comparable with BuildConfig.VERSION_CODE. */
    public final int versionCode;
    public final String apkUrl;
    /** APK size in bytes, or -1 if the release did not report one. */
    public final long apkSize;
    /** Release body, may be empty. */
    public final String releaseNotes;

    UpdateInfo(@NonNull String versionName, int versionCode, @NonNull String apkUrl,
               long apkSize, @NonNull String releaseNotes) {
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.apkUrl = apkUrl;
        this.apkSize = apkSize;
        this.releaseNotes = releaseNotes;
    }
}
