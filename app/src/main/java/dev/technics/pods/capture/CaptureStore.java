package dev.technics.pods.capture;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class CaptureStore {
    private static final Object LOCK = new Object();
    private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;
    private static final int FILE_COUNT = 4;

    private CaptureStore() {}

    public static void append(Context context, CaptureRecord record) throws IOException {
        appendLine(context, record.toJsonLine());
    }

    public static void appendMarker(Context context, String session, String note) throws IOException {
        append(context, new CaptureRecord(
                System.currentTimeMillis(),
                android.os.SystemClock.elapsedRealtimeNanos(),
                session,
                context.getPackageName(),
                Thread.currentThread().getName(),
                "META",
                "session-marker",
                "",
                "",
                "",
                -1,
                -1,
                0,
                "",
                note
        ));
    }

    private static void appendLine(Context context, String line) throws IOException {
        synchronized (LOCK) {
            File directory = captureDirectory(context);
            File current = new File(directory, fileName(0));
            int encodedLength = line.getBytes(StandardCharsets.UTF_8).length + 1;
            if (current.length() + encodedLength > MAX_FILE_BYTES) rotate(directory);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(current, true), StandardCharsets.UTF_8))) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    public static void exportAll(Context context, OutputStream output) throws IOException {
        synchronized (LOCK) {
            File directory = captureDirectory(context);
            for (int index = FILE_COUNT - 1; index >= 0; index--) {
                File source = new File(directory, fileName(index));
                if (!source.isFile()) continue;
                Files.copy(source.toPath(), output);
            }
        }
    }

    public static long totalBytes(Context context) {
        synchronized (LOCK) {
            long result = 0L;
            File directory = captureDirectory(context);
            for (int index = 0; index < FILE_COUNT; index++) {
                result += new File(directory, fileName(index)).length();
            }
            return result;
        }
    }

    public static int fileCount(Context context) {
        synchronized (LOCK) {
            int result = 0;
            File directory = captureDirectory(context);
            for (int index = 0; index < FILE_COUNT; index++) {
                if (new File(directory, fileName(index)).isFile()) result++;
            }
            return result;
        }
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            File directory = captureDirectory(context);
            for (int index = 0; index < FILE_COUNT; index++) {
                File target = new File(directory, fileName(index));
                if (target.isFile() && !target.delete()) {
                    android.util.Log.w("TechnicsPods-Store", "Could not delete " + target.getName());
                }
            }
        }
    }

    private static File captureDirectory(Context context) {
        File directory = new File(context.getFilesDir(), "captures");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Cannot create capture directory");
        }
        return directory;
    }

    private static void rotate(File directory) throws IOException {
        File oldest = new File(directory, fileName(FILE_COUNT - 1));
        if (oldest.exists() && !oldest.delete()) throw new IOException("Cannot remove oldest capture");
        for (int index = FILE_COUNT - 2; index >= 0; index--) {
            File source = new File(directory, fileName(index));
            if (!source.exists()) continue;
            Files.move(
                    source.toPath(),
                    new File(directory, fileName(index + 1)).toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private static String fileName(int index) {
        return "capture-" + index + ".jsonl";
    }
}
