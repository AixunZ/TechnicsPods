package dev.technics.pods.art;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

import dev.technics.pods.capture.CaptureContract;

/** Read-only artwork bridge with package and UID checks before opening the cache. */
public final class Az100ArtProvider extends ContentProvider {
    public static final Uri ART_URI = Uri.parse("content://dev.technics.pods.art/product");
    private static final Set<String> ALLOWED = Set.of(
            CaptureContract.SYSTEM_UI_PACKAGE,
            CaptureContract.SETTINGS_PACKAGE,
            CaptureContract.MIUI_HOME_PACKAGE,
            CaptureContract.MILINK_SERVICE_PACKAGE,
            CaptureContract.XIAOMI_BLUETOOTH_PACKAGE
    );
    private long cachedVersion = -1;
    private byte[] cachedImage;

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (uri == null || !Az100ArtPolicy.readableUri(uri.getScheme(), uri.getAuthority(),
                uri.getPath(), uri.getQuery(), uri.getFragment(), mode) || !authorizedCaller()) {
            throw new FileNotFoundException("Unauthorized");
        }
        final android.content.Context context = getContext();
        try {
            final byte[] bytes = cachedImage(context);
            if (bytes == null) throw new FileNotFoundException("No AZ100 artwork");
            return openPipeHelper(uri, "image/png", null, bytes, (output, targetUri, mime, options, image) -> {
                try (OutputStream stream = new ParcelFileDescriptor.AutoCloseOutputStream(output)) {
                    stream.write(image);
                } catch (IOException ignored) { }
            });
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    private synchronized byte[] cachedImage(android.content.Context context) throws IOException {
        if (context == null) return null;
        long version = Az100ArtStore.version(context);
        if (cachedVersion != version) {
            cachedImage = Az100ArtStore.encodedImage(context);
            cachedVersion = version;
        }
        return cachedImage;
    }

    private boolean authorizedCaller() {
        if (getContext() == null) return false;
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.myUid()) return true;
        String caller = getCallingPackage();
        if (caller == null || !ALLOWED.contains(caller)) return false;
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        if (packages == null) return false;
        for (String packageName : packages) if (caller.equals(packageName)) return true;
        return false;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { return 0; }

}
