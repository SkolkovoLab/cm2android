package net.kdt.pojavlaunch.cm2.updater;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import git.artdeell.mojo.BuildConfig;

/**
 * cm2android: queries the GitHub releases of {@link BuildConfig#CM2_UPDATE_REPO} for a launcher
 * build newer than the installed one.
 *
 * <p>Release tags are expected to be {@code v<major>.<minor>.<patch>}; the version code is derived
 * from the tag with the same formula the build script uses, so no extra metadata has to be
 * published alongside a release.
 */
public final class UpdateChecker {
    private static final String TAG = "cm2updater";
    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/%s/releases/latest";
    private static final Pattern SEMVER_TAG = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)$");

    private UpdateChecker() {}

    /**
     * Blocking network call, must not run on the main thread.
     *
     * @return the newer release, or null if the launcher is up to date, the release is unusable
     *         or the check failed (offline, rate limited, ...). Failures are not fatal: the
     *         launcher keeps working with whatever version is installed.
     */
    @Nullable
    public static UpdateInfo checkForUpdate() {
        try {
            String url = String.format(LATEST_RELEASE_URL, BuildConfig.CM2_UPDATE_REPO);
            JsonObject release = JsonParser.parseString(DownloadUtils.downloadString(url)).getAsJsonObject();
            return parseRelease(release);
        } catch (Exception e) {
            Log.i(TAG, "Update check failed", e);
            return null;
        }
    }

    @Nullable
    private static UpdateInfo parseRelease(JsonObject release) {
        if (getAsBoolean(release, "draft") || getAsBoolean(release, "prerelease")) return null;

        String tagName = getAsString(release, "tag_name");
        int versionCode = versionCodeOfTag(tagName);
        if (versionCode < 0) {
            Log.i(TAG, "Latest release tag '" + tagName + "' is not a v<major>.<minor>.<patch> tag");
            return null;
        }
        if (versionCode <= BuildConfig.VERSION_CODE) return null;

        JsonObject apkAsset = findApkAsset(release);
        if (apkAsset == null) {
            Log.w(TAG, "Release " + tagName + " has no APK asset");
            return null;
        }
        String apkUrl = getAsString(apkAsset, "browser_download_url");
        if (apkUrl.isEmpty()) return null;

        long apkSize = apkAsset.has("size") && apkAsset.get("size").isJsonPrimitive()
                ? apkAsset.get("size").getAsLong() : -1;

        return new UpdateInfo(stripTagPrefix(tagName), versionCode, apkUrl, apkSize,
                getAsString(release, "body").trim());
    }

    @Nullable
    private static JsonObject findApkAsset(JsonObject release) {
        JsonElement assets = release.get("assets");
        if (assets == null || !assets.isJsonArray()) return null;
        JsonArray assetArray = assets.getAsJsonArray();
        for (JsonElement element : assetArray) {
            if (!element.isJsonObject()) continue;
            JsonObject asset = element.getAsJsonObject();
            if (getAsString(asset, "name").toLowerCase().endsWith(".apk")) return asset;
        }
        return null;
    }

    /**
     * Converts a release tag into a comparable version code, mirroring the formula in
     * app_pojavlauncher/build.gradle.
     *
     * @return the version code, or -1 if the tag is not a semantic version
     */
    static int versionCodeOfTag(@Nullable String tagName) {
        if (tagName == null) return -1;
        Matcher matcher = SEMVER_TAG.matcher(tagName.trim());
        if (!matcher.matches()) return -1;
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = Integer.parseInt(matcher.group(3));
            return major * 10000 + minor * 100 + patch;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String stripTagPrefix(String tagName) {
        String trimmed = tagName.trim();
        return trimmed.startsWith("v") ? trimmed.substring(1) : trimmed;
    }

    private static String getAsString(JsonObject object, String member) {
        JsonElement element = object.get(member);
        if (element == null || !element.isJsonPrimitive()) return "";
        return element.getAsString();
    }

    private static boolean getAsBoolean(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }
}
