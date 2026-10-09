package dev.technics.pods.art;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dev.technics.pods.capture.CaptureContract;

/** User-supplied artwork cache. It never bundles or fetches proprietary images. */
public final class Az100ArtStore {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_DIMENSION = 2048;
    private static final String FILE_NAME = "az100.png";
    private static final String TEMP_NAME = "az100.png.tmp";
    private static final String PREFS = "az100_art";
    private static final String VERSION = "version";
    private static final Object LOCK = new Object();

    private Az100ArtStore() {}

    public static Bitmap load(Context context) {
        if (context == null) return null;
        synchronized (LOCK) {
            File file = imageFile(context);
            if (!file.isFile() || file.length() <= 0L || file.length() > MAX_BYTES) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                    || bounds.outWidth > MAX_DIMENSION || bounds.outHeight > MAX_DIMENSION) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }
    }

    public static void importImage(Context context, Uri uri) throws IOException {
        if (context == null || uri == null) throw new IOException("Missing artwork source");
        byte[] bytes;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Unable to open artwork source");
            bytes = readBounded(input);
        }
        normalizeAndStore(context, bytes);
    }

    /** Imports an explicitly AZ100-named image from the installed official APK, if present. */
    public static boolean importOfficial(Context context) {
        if (context == null) return false;
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(CaptureContract.OFFICIAL_APP_PACKAGE, 0);
            List<String> sources = new ArrayList<>();
            if (info.sourceDir != null) sources.add(info.sourceDir);
            if (info.splitSourceDirs != null) sources.addAll(List.of(info.splitSourceDirs));
            for (String source : sources) try (ZipFile zip = new ZipFile(source)) {
                List<? extends ZipEntry> entries = zip.stream()
                        .filter(Az100ArtStore::isCandidateEntry)
                        .filter(item -> item.getSize() >= 0 && item.getSize() <= MAX_BYTES)
                        .sorted(Comparator.comparingLong(ZipEntry::getSize).reversed())
                        .toList();
                for (ZipEntry entry : entries) try (InputStream input = zip.getInputStream(entry)) {
                    try {
                        normalizeAndStore(context, readBounded(input));
                        return true;
                    } catch (IOException ignored) {
                        // A named entry may be a format the decoder does not support.
                    }
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void clear(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            new AtomicFile(imageFile(context)).delete();
            new File(artDir(context), TEMP_NAME).delete();
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(VERSION, Math.max(System.currentTimeMillis(), version(context) + 1L)).apply();
            context.getContentResolver().notifyChange(Az100ArtProvider.ART_URI, null);
            context.getContentResolver().notifyChange(dev.technics.pods.state.Az100StateProvider.STATE_URI, null);
        }
    }

    public static long version(Context context) {
        return context == null ? 0L : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(VERSION, 0L);
    }

    static byte[] encodedImage(Context context) throws IOException {
        synchronized (LOCK) {
            File file = imageFile(context);
            if (!file.isFile() || file.length() <= 0 || file.length() > MAX_BYTES) return null;
            try (InputStream input = new AtomicFile(file).openRead()) { return readBounded(input); }
        }
    }

    private static void normalizeAndStore(Context context, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IOException("Artwork exceeds size limit");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                || bounds.outWidth > 32768 || bounds.outHeight > 32768) {
            throw new IOException("Invalid or oversized image dimensions");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > MAX_DIMENSION
                || bounds.outHeight / options.inSampleSize > MAX_DIMENSION) options.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("Invalid image");
        Bitmap normalized = bitmap;
        try {
            int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (max > MAX_DIMENSION) {
                float scale = MAX_DIMENSION / (float) max;
                normalized = Bitmap.createScaledBitmap(bitmap,
                        Math.max(1, Math.round(bitmap.getWidth() * scale)),
                        Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
            }
            File dir = artDir(context);
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Unable to create artwork cache");
            File temp = File.createTempFile("az100-", ".tmp", dir);
            try {
                try (FileOutputStream output = new FileOutputStream(temp)) {
                    if (!normalized.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw new IOException("Unable to encode artwork");
                    }
                    output.getFD().sync();
                }
                if (temp.length() > MAX_BYTES) {
                    throw new IOException("Normalized artwork exceeds size limit");
                }
                synchronized (LOCK) {
                    AtomicFile target = new AtomicFile(imageFile(context));
                    FileOutputStream output = target.startWrite();
                    try (InputStream input = new java.io.FileInputStream(temp)) {
                        byte[] buffer = new byte[16384];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                        target.finishWrite(output);
                    } catch (IOException error) {
                        target.failWrite(output);
                        throw error;
                    }
                    long previousVersion = version(context);
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putLong(VERSION, Math.max(System.currentTimeMillis(), previousVersion + 1L)).apply();
                }
                context.getContentResolver().notifyChange(Az100ArtProvider.ART_URI, null);
                context.getContentResolver().notifyChange(dev.technics.pods.state.Az100StateProvider.STATE_URI, null);
            } finally { temp.delete(); }
        } finally {
            if (normalized != bitmap) normalized.recycle();
            bitmap.recycle();
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(MAX_BYTES, 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_BYTES) throw new IOException("Artwork exceeds size limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static boolean isCandidateEntry(ZipEntry entry) {
        return !entry.isDirectory() && Az100ArtPolicy.officialEntry(entry.getName());
    }

    private static File artDir(Context context) {
        return new File(context.getFilesDir(), "az100_art");
    }

    private static File imageFile(Context context) {
        return new File(artDir(context), FILE_NAME);
    }
}
