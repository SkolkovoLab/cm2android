package net.kdt.pojavlaunch.tasks;

import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.mirrors.DownloadMirror;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;

import org.apache.commons.codec.binary.Hex;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Strips sound assets out of a Minecraft asset index.
 * <p>
 * The server replaces every sound through its own resource pack, so the vanilla sound files - by
 * far the biggest part of the asset download - are dead weight. This filter drops all of them
 * except the files used by {@link #KEPT_SOUND_EVENTS}, and replaces the sounds.json of every
 * affected namespace with a stripped copy declaring only those events. Without that replacement
 * the game would warn about a missing file for every single sound event it knows about.
 * <p>
 * The stripped sounds.json is stored as a regular asset object and referenced from the rewritten
 * index by its own hash and size, so the downloader accepts it instead of fetching the original.
 */
public class SoundAssetFilter {
    private static final String TAG = "SoundAssetFilter";

    /** Sound events kept in the game. Everything else under {@code <namespace>/sounds/} is removed. */
    private static final String[] KEPT_SOUND_EVENTS = {"minecraft:ui.button.click"};

    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final String SOUND_INDEX_SUFFIX = "/sounds.json";
    private static final String SOUND_PATH_PREFIX = "sounds/";
    private static final String SOUND_PATH_INFIX = "/sounds/";
    private static final String SOUND_FILE_SUFFIX = ".ogg";
    private static final String CACHE_DIRECTORY = "cm2_sound_index";

    /**
     * Rewrite an asset index in place, removing all sound files but the kept ones.
     * @param assetIndex the parsed asset index, modified in place
     * @throws IOException if downloading or storing a sounds.json fails
     */
    public static void stripSounds(JsonObject assetIndex) throws IOException {
        JsonElement objectsElement = assetIndex.get("objects");
        if(objectsElement == null || !objectsElement.isJsonObject()) return;
        if(isBoolean(assetIndex, "virtual") || isBoolean(assetIndex, "map_to_resources")) {
            // Pre-1.7 indexes lay the assets out by name instead of by hash, and predate sounds.json.
            Log.i(TAG, "Legacy asset index layout, leaving the sounds alone");
            return;
        }
        JsonObject objects = objectsElement.getAsJsonObject();
        Set<String> keptFiles = new HashSet<>();
        for(Map.Entry<String, List<String>> namespace : keptEventsByNamespace().entrySet()) {
            keptFiles.addAll(rewriteSoundIndex(objects, namespace.getKey(), namespace.getValue()));
        }
        removeSoundFiles(objects, keptFiles);
    }

    /**
     * Replace the sounds.json of a namespace with a copy declaring only the kept events.
     * @param objects the "objects" map of the asset index, modified in place
     * @param namespace the asset namespace to process
     * @param events the names (without namespace) of the events to keep
     * @return the asset paths of the sound files used by the kept events
     * @throws IOException if downloading or storing the sounds.json fails
     */
    private static Set<String> rewriteSoundIndex(JsonObject objects, String namespace, List<String> events) throws IOException {
        Set<String> soundFiles = new HashSet<>();
        JsonElement indexEntryElement = objects.get(namespace + SOUND_INDEX_SUFFIX);
        if(indexEntryElement == null || !indexEntryElement.isJsonObject()) {
            Log.w(TAG, "No sounds.json for namespace " + namespace + ", keeping no sounds at all");
            return soundFiles;
        }
        JsonObject indexEntry = indexEntryElement.getAsJsonObject();
        JsonObject soundIndex = readSoundIndex(indexEntry.get("hash").getAsString());

        JsonObject strippedIndex = new JsonObject();
        Queue<String> pendingEvents = new ArrayDeque<>(events);
        String event;
        while((event = pendingEvents.poll()) != null) {
            if(strippedIndex.has(event)) continue;
            JsonElement registration = soundIndex.get(event);
            if(registration == null) {
                Log.w(TAG, "Sound event " + namespace + ":" + event + " is not declared in the sound index");
                continue;
            }
            strippedIndex.add(event, registration);
            collectSounds(registration, namespace, soundFiles, pendingEvents);
        }

        byte[] strippedContent = Tools.GLOBAL_GSON.toJson(strippedIndex).getBytes(StandardCharsets.UTF_8);
        String strippedHash = sha1(strippedContent);
        storeAssetObject(strippedContent, strippedHash);
        indexEntry.addProperty("hash", strippedHash);
        indexEntry.addProperty("size", strippedContent.length);
        Log.i(TAG, "Kept " + strippedIndex.size() + " sound events of namespace " + namespace);
        return soundFiles;
    }

    /**
     * Collect the sound files of one sound event registration.
     * @param registration the sound event registration from a sounds.json
     * @param namespace the namespace of the sounds.json the registration comes from
     * @param soundFiles the set collecting the asset paths of the referenced sound files
     * @param pendingEvents the queue collecting the events referenced by this registration
     */
    private static void collectSounds(JsonElement registration, String namespace, Set<String> soundFiles, Queue<String> pendingEvents) {
        if(!registration.isJsonObject()) return;
        JsonElement soundsElement = registration.getAsJsonObject().get("sounds");
        if(soundsElement == null || !soundsElement.isJsonArray()) return;
        for(JsonElement soundElement : soundsElement.getAsJsonArray()) {
            String name;
            String type = "file";
            if(soundElement.isJsonPrimitive()) {
                name = soundElement.getAsString();
            } else if(soundElement.isJsonObject()) {
                JsonObject sound = soundElement.getAsJsonObject();
                JsonElement nameElement = sound.get("name");
                if(nameElement == null) continue;
                name = nameElement.getAsString();
                JsonElement typeElement = sound.get("type");
                if(typeElement != null) type = typeElement.getAsString();
            } else {
                continue;
            }
            int separator = name.indexOf(':');
            String soundNamespace = separator == -1 ? namespace : name.substring(0, separator);
            String soundPath = name.substring(separator + 1);
            if("event".equals(type)) {
                // The event references another event, which has to be kept (and resolved) as well.
                if(soundNamespace.equals(namespace)) pendingEvents.add(soundPath);
                else Log.w(TAG, "Cannot keep the sound event " + name + " from another namespace");
                continue;
            }
            soundFiles.add(soundNamespace + SOUND_PATH_INFIX + soundPath + SOUND_FILE_SUFFIX);
        }
    }

    /**
     * Read a sounds.json asset object, downloading it into the cache directory if needed.
     * @param hash the SHA1 hash of the object, as declared by the asset index
     * @return the parsed sounds.json
     * @throws IOException if the download fails
     */
    private static JsonObject readSoundIndex(String hash) throws IOException {
        File cacheFile = new File(Tools.DIR_CACHE, CACHE_DIRECTORY + File.separator + hash + ".json");
        DownloadUtils.ensureSha1(cacheFile, hash, () -> {
            DownloadMirror.downloadFileMirrored(DownloadMirror.DOWNLOAD_CLASS_ASSETS,
                    MoJsonDownloader.MC_RES + hash.substring(0, 2) + "/" + hash, cacheFile);
            return null;
        });
        return JsonParser.parseString(Tools.read(cacheFile)).getAsJsonObject();
    }

    /**
     * Store a generated asset object, so that the downloader finds it already present on disk.
     * @param content the content of the object
     * @param hash the SHA1 hash of the content
     * @throws IOException if writing the object fails
     */
    private static void storeAssetObject(byte[] content, String hash) throws IOException {
        File objectFile = new File(Tools.ASSETS_PATH,
                "objects" + File.separator + hash.substring(0, 2) + File.separator + hash);
        FileUtils.ensureParentDirectory(objectFile);
        try(FileOutputStream outputStream = new FileOutputStream(objectFile)) {
            outputStream.write(content);
        }
    }

    /**
     * Remove every sound file that is not needed by the kept sound events.
     * @param objects the "objects" map of the asset index, modified in place
     * @param keptFiles the asset paths of the sound files to keep
     */
    private static void removeSoundFiles(JsonObject objects, Set<String> keptFiles) {
        Iterator<Map.Entry<String, JsonElement>> iterator = objects.entrySet().iterator();
        int removedCount = 0;
        long removedSize = 0;
        while(iterator.hasNext()) {
            Map.Entry<String, JsonElement> object = iterator.next();
            String path = object.getKey();
            if(!isSoundFile(path) || keptFiles.contains(path)) continue;
            JsonElement value = object.getValue();
            if(value.isJsonObject()) {
                JsonElement size = value.getAsJsonObject().get("size");
                if(size != null) removedSize += size.getAsLong();
            }
            iterator.remove();
            removedCount++;
        }
        Log.i(TAG, "Removed " + removedCount + " sound assets (" + (removedSize / 1048576) + " MiB) from the asset index");
    }

    private static boolean isSoundFile(String path) {
        return path.startsWith(SOUND_PATH_PREFIX) || path.contains(SOUND_PATH_INFIX);
    }

    /**
     * Group the kept sound events by the namespace they belong to.
     * @return a map of namespace to the event names (without namespace) inside it
     */
    private static Map<String, List<String>> keptEventsByNamespace() {
        Map<String, List<String>> events = new LinkedHashMap<>();
        for(String keptEvent : KEPT_SOUND_EVENTS) {
            int separator = keptEvent.indexOf(':');
            String namespace = separator == -1 ? DEFAULT_NAMESPACE : keptEvent.substring(0, separator);
            List<String> namespaceEvents = events.get(namespace);
            if(namespaceEvents == null) {
                namespaceEvents = new ArrayList<>();
                events.put(namespace, namespaceEvents);
            }
            namespaceEvents.add(keptEvent.substring(separator + 1));
        }
        return events;
    }

    private static boolean isBoolean(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static String sha1(byte[] content) {
        try {
            return Hex.encodeHexString(MessageDigest.getInstance("SHA-1").digest(content));
        }catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("WTF? SHA-1 digest missing!", e);
        }
    }
}
