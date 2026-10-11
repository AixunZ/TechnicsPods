package dev.technics.pods.art;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.technics.pods.ModulePreferences;
import dev.technics.pods.capture.CaptureContract;

/** Bounded, atomic artwork sets shared by the app and authorized host processes. */
public final class Az100ArtStore {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_DIMENSION = 2048;
    private static final String PREFS = "az100_art";
    private static final String VERSION = "version";
    private static final String ACTIVE_SET = "active_set";
    private static final String MANIFEST = "manifest.xml";
    private static final String BUNDLED_MANIFEST = "az100-art/manifest.xml";
    private static final String REVIEWED_MAPPING = "official-mapping.xml";
    private static final Object LOCK = new Object();

    private Az100ArtStore() { }

    public static Bitmap load(Context context) { return load(context, "main"); }

    public static Bitmap load(Context context, String slot) {
        if (context == null || !Az100ArtPolicy.validSlot(slot)) return null;
        try {
            byte[] bytes = encodedImage(context, slot);
            if (bytes == null) return null;
            BitmapFactory.Options bounds = bounds(bytes);
            if (bounds.outWidth > MAX_DIMENSION || bounds.outHeight > MAX_DIMENSION) return null;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IOException | RuntimeException error) { return null; }
    }

    /** Select one of the reviewed AZ100 finish sets bundled with the module. */
    public static boolean selectBundledColor(Context context, String color) throws IOException {
        if (context == null || color == null || !color.matches("black|silver|blue|gold|violet")) return false;
        synchronized (LOCK) {
            Az100ArtManifest current = metadata(context);
            if ("bundled".equals(current.source()) && color.equalsIgnoreCase(current.color())
                    && current.deviceAddress().equalsIgnoreCase(selectedAddress(context))) return false;
            Map<String, byte[]> images = new LinkedHashMap<>();
            Map<String, Az100ArtManifest.Slot> slots = new LinkedHashMap<>();
            for (String slot : new String[]{"main", "left", "right", "case"}) {
                String asset = "az100-art/" + color + "/" + ("main".equals(slot) ? "main" : slot) + ".webp";
                byte[] image = normalize(readBounded(context.getAssets().open(asset)));
                BitmapFactory.Options dimensions = bounds(image);
                images.put(slot, image);
                slots.put(slot, new Az100ArtManifest.Slot(slot, "", "", slot + ".png", digest(image),
                        dimensions.outWidth, dimensions.outHeight));
            }
            Az100ArtManifest manifest = new Az100ArtManifest("bundled", color, "5.0.1", 104L, "",
                    selectedAddress(context), true, slots);
            publish(context, manifest, images);
            return true;
        }
    }

    public static void importImage(Context context, Uri uri) throws IOException {
        if (context == null || uri == null) throw new IOException("Missing artwork source");
        String targetAddress = selectedAddress(context);
        byte[] bytes;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Unable to open artwork source");
            bytes = normalize(readBounded(input));
        }
        BitmapFactory.Options dimensions = bounds(bytes);
        Az100ArtManifest.Slot slot = new Az100ArtManifest.Slot("main", "", "", "main.png",
                digest(bytes), dimensions.outWidth, dimensions.outHeight);
        publish(context, new Az100ArtManifest("manual", "unspecified", "", 0L, "",
                targetAddress, false, Map.of("main", slot)), Map.of("main", bytes));
    }

    /** Compatibility API. No resource-name heuristic or size-based guess is permitted. */
    public static boolean importOfficial(Context context) {
        return importOfficialResult(context, null).success();
    }

    public static Az100ArtImportResult importOfficialResult(Context context, String color) {
        if (context == null) return failure(Az100ArtImportResult.Status.FAILED, "Missing context");
        String targetAddress = selectedAddress(context);
        try {
            context.getPackageManager().getApplicationInfo(CaptureContract.OFFICIAL_APP_PACKAGE, 0);
            Az100ArtManifest mapping;
            File reviewed = new File(artDir(context), REVIEWED_MAPPING);
            if (reviewed.isFile()) {
                try (InputStream input = new FileInputStream(reviewed)) { mapping = Az100ArtManifest.read(input); }
            } else mapping = bundled(context);
            return importMapping(context, mapping, color, targetAddress);
        } catch (PackageManager.NameNotFoundException error) {
            return failure(Az100ArtImportResult.Status.APP_MISSING, "Official app is not installed");
        } catch (IOException | RuntimeException error) {
            return failure(Az100ArtImportResult.Status.FAILED, "Official resource inspection failed: " + error.getMessage());
        }
    }

    /** Import reviewed XML, then render the installed official base/split resources. */
    public static Az100ArtImportResult importOfficialManifest(Context context, Uri manifestUri) {
        if (context == null || manifestUri == null) return failure(Az100ArtImportResult.Status.FAILED, "Missing manifest");
        String targetAddress = selectedAddress(context);
        try (InputStream input = context.getContentResolver().openInputStream(manifestUri)) {
            if (input == null) throw new IOException("Unable to open artwork manifest");
            Az100ArtManifest mapping = Az100ArtManifest.read(input);
            Az100ArtImportResult result = importMapping(context, mapping, mapping.color(), targetAddress);
            if (result.success()) writeAtomic(new File(artDir(context), REVIEWED_MAPPING), mapping.encode());
            return result;
        } catch (IOException | RuntimeException error) {
            return failure(Az100ArtImportResult.Status.FAILED, "Invalid artwork mapping: " + error.getMessage());
        }
    }

    private static Az100ArtImportResult importMapping(Context context, Az100ArtManifest mapping,
            String color, String targetAddress) {
        if (!mapping.verified() || !"official".equals(mapping.source()) || mapping.slots().isEmpty()) {
            return failure(Az100ArtImportResult.Status.MAPPING_REQUIRED,
                    "A reviewed AZ100 model/color resource manifest is required for this APK and its splits");
        }
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo official = manager.getPackageInfo(CaptureContract.OFFICIAL_APP_PACKAGE,
                    PackageManager.GET_SIGNING_CERTIFICATES);
            Set<String> certificates = new HashSet<>();
            if (official.signingInfo != null) for (Signature signature : official.signingInfo.getApkContentsSigners()) {
                certificates.add(digest(signature.toByteArray()));
            }
            if (!mapping.matchesOfficial(official.packageName, official.getLongVersionCode(), certificates, color)) {
                return failure(Az100ArtImportResult.Status.SOURCE_MISMATCH,
                        "Official package version, signature or selected color does not match the reviewed mapping");
            }
            Resources resources = manager.getResourcesForApplication(official.applicationInfo);
            Map<String, byte[]> images = new LinkedHashMap<>();
            Map<String, Az100ArtManifest.Slot> slots = new LinkedHashMap<>();
            for (Az100ArtManifest.Slot slot : mapping.slots().values()) {
                String[] name = slot.resource().split("/", 2);
                int resourceId = resources.getIdentifier(name[1], name[0], CaptureContract.OFFICIAL_APP_PACKAGE);
                if (resourceId == 0) return failure(Az100ArtImportResult.Status.RESOURCE_MISSING,
                        "Mapped official resource is unavailable: " + slot.resource());
                byte[] resource;
                try (InputStream input = resources.openRawResource(resourceId)) { resource = readBounded(input); }
                if (!digest(resource).equalsIgnoreCase(slot.resourceSha256())) {
                    return failure(Az100ArtImportResult.Status.SOURCE_MISMATCH,
                            "Resource digest mismatch: " + slot.resource());
                }
                // Resources includes installed splits and resolves vector/XML references.
                BitmapFactory.Options probe = new BitmapFactory.Options();
                probe.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(resource, 0, resource.length, probe);
                byte[] image = probe.outWidth > 0 && probe.outHeight > 0
                        ? normalize(resource) : render(resources.getDrawable(resourceId, null));
                BitmapFactory.Options dimensions = bounds(image);
                images.put(slot.name(), image);
                slots.put(slot.name(), new Az100ArtManifest.Slot(slot.name(), slot.resource(),
                        slot.resourceSha256(), slot.name() + ".png", digest(image),
                        dimensions.outWidth, dimensions.outHeight));
            }
            Az100ArtManifest output = new Az100ArtManifest("official", mapping.color(), official.versionName == null
                    ? "" : official.versionName, official.getLongVersionCode(), mapping.signatureSha256(),
                    targetAddress, true, slots);
            publish(context, output, images);
            return new Az100ArtImportResult(Az100ArtImportResult.Status.SUCCESS,
                    "Imported reviewed AZ100 artwork", slots.keySet());
        } catch (PackageManager.NameNotFoundException error) {
            return failure(Az100ArtImportResult.Status.APP_MISSING, "Official app is not installed");
        } catch (Resources.NotFoundException error) {
            return failure(Az100ArtImportResult.Status.RESOURCE_MISSING, "Mapped resource cannot be rendered");
        } catch (IOException | RuntimeException error) {
            return failure(Az100ArtImportResult.Status.FAILED, "Official artwork import failed: " + error.getMessage());
        }
    }

    public static Az100ArtManifest metadata(Context context) {
        if (context == null) return Az100ArtManifest.empty();
        synchronized (LOCK) {
            File active = activeSet(context);
            if (active != null) {
                try (InputStream input = new AtomicFile(new File(active, MANIFEST)).openRead()) {
                    return Az100ArtManifest.read(input);
                } catch (IOException ignored) { return Az100ArtManifest.empty(); }
            }
            // beta.1 used a fixed PNG. It remains readable after upgrading.
            File legacy = new File(artDir(context), "az100.png");
            if (legacy.isFile()) try (InputStream input = new FileInputStream(legacy)) {
                byte[] bytes = readBounded(input);
                return new Az100ArtManifest("manual", "unspecified", "", 0L, "", "", false,
                        Map.of("main", new Az100ArtManifest.Slot("main", "", "", "az100.png", digest(bytes), 0, 0)));
            } catch (IOException ignored) { return Az100ArtManifest.empty(); }
            try { return bundled(context); }
            catch (IOException ignored) { return Az100ArtManifest.empty(); }
        }
    }

    public static long version(Context context) {
        return context == null ? 0L : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(VERSION, 0L);
    }

    public static void clear(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ACTIVE_SET)
                    .putLong(VERSION, nextVersion(context)).commit()) {
                throw new IllegalStateException("Unable to reset artwork cache");
            }
            new AtomicFile(new File(artDir(context), "az100.png")).delete();
            pruneSets(context, null);
        }
        notifyChanged(context);
    }

    static byte[] encodedImage(Context context) throws IOException { return encodedImage(context, "main"); }

    static byte[] encodedImage(Context context, String name) throws IOException {
        if (context == null || !Az100ArtPolicy.validSlot(name)) return null;
        synchronized (LOCK) {
            Az100ArtManifest manifest = metadata(context);
            String selected = selectedAddress(context);
            if (!manifest.deviceAddress().isEmpty() && !manifest.deviceAddress().equalsIgnoreCase(selected)) return null;
            Az100ArtManifest.Slot slot = manifest.slot(name);
            if (slot == null || slot.file().isEmpty()) return null;
            File active = activeSet(context);
            byte[] bytes;
            if (active != null) {
                try (InputStream input = new AtomicFile(new File(active, slot.file())).openRead()) { bytes = readBounded(input); }
            } else if ("manual".equals(manifest.source())) {
                try (InputStream input = new FileInputStream(new File(artDir(context), slot.file()))) { bytes = readBounded(input); }
            } else {
                if (!manifest.verified()) return null;
                try (InputStream input = context.getAssets().open("az100-art/" + slot.file())) { bytes = readBounded(input); }
            }
            if (!digest(bytes).equalsIgnoreCase(slot.sha256())) throw new IOException("Cached artwork digest mismatch");
            return bytes;
        }
    }

    private static Az100ArtManifest bundled(Context context) throws IOException {
        try (InputStream input = context.getAssets().open(BUNDLED_MANIFEST)) { return Az100ArtManifest.read(input); }
    }

    private static void publish(Context context, Az100ArtManifest manifest, Map<String, byte[]> images) throws IOException {
        synchronized (LOCK) {
            checkSelection(context, manifest);
            File sets = new File(artDir(context), "sets");
            String id = UUID.randomUUID().toString();
            File directory = new File(sets, id);
            if (!directory.mkdirs()) throw new IOException("Unable to create artwork set");
            boolean published = false;
            try {
                for (Map.Entry<String, byte[]> image : images.entrySet()) {
                    writeAtomic(new File(directory, image.getKey() + ".png"), image.getValue());
                }
                writeAtomic(new File(directory, MANIFEST), manifest.encode());
                checkSelection(context, manifest);
                if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(ACTIVE_SET, id).putLong(VERSION, nextVersion(context)).commit()) {
                    throw new IOException("Unable to commit artwork set");
                }
                published = true;
                pruneSets(context, id);
            } finally { if (!published) deleteSet(directory); }
        }
        notifyChanged(context);
    }

    private static void checkSelection(Context context, Az100ArtManifest manifest) throws IOException {
        if (!manifest.deviceAddress().equalsIgnoreCase(selectedAddress(context))) {
            throw new IOException("Device selection changed during artwork import");
        }
    }

    private static File activeSet(Context context) {
        String id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACTIVE_SET, "");
        return id != null && id.matches("[a-f0-9-]{36}") ? new File(new File(artDir(context), "sets"), id) : null;
    }
    private static File artDir(Context context) { return new File(context.getFilesDir(), "az100_art"); }
    private static String selectedAddress(Context context) {
        String address = ModulePreferences.get(context).getString(ModulePreferences.SELECTED_ADDRESS, "");
        return address == null ? "" : address;
    }
    private static long nextVersion(Context context) { return Math.max(System.currentTimeMillis(), version(context) + 1L); }
    private static void notifyChanged(Context context) {
        context.getContentResolver().notifyChange(Az100ArtProvider.ART_URI, null);
        context.getContentResolver().notifyChange(dev.technics.pods.state.Az100StateProvider.STATE_URI, null);
    }
    private static void writeAtomic(File file, byte[] bytes) throws IOException {
        AtomicFile target = new AtomicFile(file);
        FileOutputStream output = target.startWrite();
        try { output.write(bytes); target.finishWrite(output); }
        catch (IOException error) { target.failWrite(output); throw error; }
    }
    private static void pruneSets(Context context, String retain) {
        File[] children = new File(artDir(context), "sets").listFiles();
        if (children != null) for (File child : children) if (!child.getName().equals(retain)) deleteSet(child);
    }
    private static void deleteSet(File directory) {
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) file.delete();
        directory.delete();
    }
    private static byte[] render(Drawable drawable) throws IOException {
        if (drawable == null) throw new IOException("Empty official drawable");
        int width = drawable.getIntrinsicWidth(), height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0 || width > 32768 || height > 32768) throw new IOException("Unsupported drawable dimensions");
        float scale = Math.min(1f, MAX_DIMENSION / (float) Math.max(width, height));
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
        try {
            drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            drawable.draw(new Canvas(bitmap));
            return encode(bitmap);
        } finally { bitmap.recycle(); }
    }
    private static byte[] normalize(byte[] bytes) throws IOException {
        BitmapFactory.Options options = bounds(bytes);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > MAX_DIMENSION
                || options.outHeight / options.inSampleSize > MAX_DIMENSION) options.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("Invalid artwork image");
        try { return encode(bitmap); }
        finally { bitmap.recycle(); }
    }
    private static BitmapFactory.Options bounds(byte[] bytes) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth > 32768 || options.outHeight > 32768) {
            throw new IOException("Invalid artwork dimensions");
        }
        return options;
    }
    private static byte[] encode(Bitmap bitmap) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) || output.size() > MAX_BYTES) {
            throw new IOException("Normalized artwork exceeds size limit");
        }
        return output.toByteArray();
    }
    private static byte[] readBounded(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Artwork exceeds size limit");
        return bytes;
    }
    static String digest(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder output = new StringBuilder(64);
            for (byte value : hash) output.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return output.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static Az100ArtImportResult failure(Az100ArtImportResult.Status status, String message) {
        return Az100ArtImportResult.failure(status, message);
    }
}
