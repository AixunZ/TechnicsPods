package dev.technics.pods.hook;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import dev.technics.pods.art.Az100ArtProvider;
import dev.technics.pods.art.Az100ArtStore;
import dev.technics.pods.capture.CaptureContract;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Artwork replacement restricted to verified MiLink image IDs and current AZ100 identity. */
final class Az100ArtHook {
    private static final String TAG = "TechnicsPods-Art";
    // Verified in HuaweiPods MiLinkServiceHook.syncMiLinkHeadsetIcon, not inferred from size.
    private static final Set<String> MILINK_IMAGE_IDS = Set.of("device_item",
            "circulate_headset_icon", "circulate_single_battery_headset_icon");
    private final XposedModule module;
    private final String packageName;
    private final ClassLoader loader;
    private final Az100HookDeviceIdentity deviceIdentity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TechnicsPods-art-loader");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<View, String> roots = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<ImageView, Binding> bindings = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<ImageView, Boolean> attachedListeners = Collections.synchronizedMap(new WeakHashMap<>());
    private final AtomicLong generation = new AtomicLong();
    private final ScheduledExecutorService timeouts = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "TechnicsPods-art-timeout"); thread.setDaemon(true); return thread;
    });
    private final ThreadLocal<Boolean> rendering = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Integer> hostWriteDepth = ThreadLocal.withInitial(() -> 0);
    private volatile Bitmap cached;
    private volatile boolean loading;
    private boolean observing;
    private int installedHooks;

    Az100ArtHook(XposedModule module, String packageName, ClassLoader loader) {
        this.module = module;
        this.packageName = packageName;
        this.loader = loader;
        deviceIdentity = new Az100HookDeviceIdentity(module, () -> main.post(() -> {
            List<View> live;
            synchronized (roots) { live = new ArrayList<>(roots.keySet()); }
            for (View root : live) { visit(root, this::restore); refresh(root); }
        }));
    }

    void install() {
        if (!CaptureContract.SETTINGS_PACKAGE.equals(packageName)
                && !CaptureContract.MILINK_SERVICE_PACKAGE.equals(packageName)) return;
        installCapabilityReport();
        if (CaptureContract.SETTINGS_PACKAGE.equals(packageName)) {
            module.log(Log.INFO, TAG, "Settings artwork unavailable: no verified product-image resource IDs");
            return;
        }
        if (!CaptureContract.MILINK_SERVICE_PACKAGE.equals(packageName)) return;
        try {
            Class<?> detail = Class.forName("com.miui.circulateplus.world.headset.HeadSetsDetail", false, loader);
            Set<String> methods = Set.of("onAttachedToWindow", "onFinishInflate",
                    "setAttachedDeviceInfo", "setHeadsetDeviceInfo", "setHeadsetInfo", "updateState", "x");
            for (Method method : detail.getDeclaredMethods()) {
                if (!methods.contains(method.getName()) || method.getReturnType() != void.class) continue;
                module.hook(method).setId("technicspods.art:" + method.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            if (chain.getThisObject() instanceof View root) refresh(root);
                            return result;
                        });
                installedHooks++;
            }
            for (String setter : List.of("setImageDrawable", "setImageBitmap", "setImageResource")) {
                Class<?> argument = switch (setter) {
                    case "setImageBitmap" -> Bitmap.class;
                    case "setImageResource" -> int.class;
                    default -> Drawable.class;
                };
                Method method = ImageView.class.getDeclaredMethod(setter, argument);
                module.hook(method).setId("technicspods.art:guard:" + setter)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            int depth = hostWriteDepth.get();
                            Object result;
                            hostWriteDepth.set(depth + 1);
                            try { result = chain.proceed(); }
                            finally { hostWriteDepth.set(depth); }
                            if (depth == 0 && !rendering.get() && chain.getThisObject() instanceof ImageView image) {
                                Binding binding = bindings.get(image);
                                if (binding != null) {
                                    Drawable current = image.getDrawable();
                                    if (!(current instanceof BitmapDrawable bitmapDrawable)
                                            || cached == null || bitmapDrawable.getBitmap() != cached) {
                                        if (current == null || current.getConstantState() != null) binding.remember(current);
                                    }
                                    View root = binding.root.get();
                                    if (root == null || !binding.address.equals(identity(root))) restore(image);
                                    else apply(image, binding, cached);
                                }
                            }
                            return result;
                        });
                installedHooks++;
            }
            module.log(Log.INFO, TAG, "MiLink artwork hooks installed with verified resource IDs");
        } catch (Throwable error) {
            module.log(Log.INFO, TAG, "MiLink artwork unavailable for this host build", error);
        }
        reportCapability(currentApplication());
    }

    private void installCapabilityReport() {
        try {
            Method applicationReady = Instrumentation.class.getDeclaredMethod("callApplicationOnCreate", Application.class);
            module.hook(applicationReady).setId("technicspods.art:capability-ready")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        if (chain.getArg(0) instanceof Application application) reportCapability(application);
                        return result;
                    });
            main.post(() -> reportCapability(currentApplication()));
        } catch (Throwable ignored) { }
    }

    private Context currentApplication() {
        try {
            Object app = Class.forName("android.app.ActivityThread", false, loader)
                    .getDeclaredMethod("currentApplication").invoke(null);
            return app instanceof Application application ? application : null;
        } catch (Throwable ignored) { return null; }
    }

    private void reportCapability(Context context) {
        HostCapabilityReport.report(context == null ? null : context.getApplicationContext(), "artwork",
                installedHooks, CaptureContract.SETTINGS_PACKAGE.equals(packageName)
                        ? "No verified Settings product image ID" : "MiLink verified IDs=3;strict device identity required");
    }

    private void refresh(View root) {
        String address = identity(root);
        if (address == null) {
            roots.remove(root);
            visit(root, this::restore);
            return;
        }
        String previous = roots.put(root, address);
        if (previous != null && !previous.equals(address)) visit(root, this::restore);
        visit(root, image -> {
            String entry;
            try { entry = image.getResources().getResourceEntryName(image.getId()); }
            catch (RuntimeException error) { return; }
            if (!MILINK_IMAGE_IDS.contains(entry)) return;
            Binding binding = bindings.get(image);
            if (binding == null) {
                if (image.getDrawable() != null && image.getDrawable().getConstantState() == null) return;
                binding = new Binding(root, address, image.getDrawable(), image.getScaleType());
                bindings.put(image, binding);
            }
            if (attachedListeners.putIfAbsent(image, true) == null) {
                WeakReference<View> weakRoot = new WeakReference<>(root);
                image.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View view) {
                        View current = weakRoot.get();
                        if (current != null) refresh(current);
                    }
                    @Override public void onViewDetachedFromWindow(View view) { restore(image); }
                });
            }
            apply(image, binding, cached);
        });
        observe(root.getContext());
        preload(root.getContext());
    }

    private synchronized void observe(Context context) {
        if (observing) return;
        try {
            context.getContentResolver().registerContentObserver(Az100ArtProvider.ART_URI,
                    false, new ContentObserver(main) {
                        @Override public void onChange(boolean selfChange) {
                            generation.incrementAndGet();
                            cached = null;
                            List<View> live;
                            synchronized (roots) { live = new ArrayList<>(roots.keySet()); }
                            for (View root : live) { visit(root, Az100ArtHook.this::restore); refresh(root); }
                        }
                    });
            observing = true;
        } catch (RuntimeException ignored) { }
    }

    private synchronized void preload(Context context) {
        if (cached != null || loading) return;
        loading = true;
        long request = generation.get();
        Context app = context.getApplicationContext();
        if (app == null) { loading = false; return; }
        decoder.execute(() -> {
            Bitmap bitmap = null;
            long started = android.os.SystemClock.elapsedRealtime();
            AtomicReference<InputStream> active = new AtomicReference<>();
            ScheduledFuture<?> timeout = timeouts.schedule(() -> {
                InputStream stream = active.get();
                if (stream != null) try { stream.close(); } catch (java.io.IOException ignored) { }
            }, 10L, TimeUnit.SECONDS);
            try (InputStream input = app.getContentResolver().openInputStream(Az100ArtProvider.ART_URI)) {
                active.set(input);
                if (android.os.SystemClock.elapsedRealtime() - started >= 10_000L) throw new java.io.IOException("Artwork timeout");
                if (input != null) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        if (request != generation.get() || android.os.SystemClock.elapsedRealtime() - started >= 10_000L) {
                            throw new java.io.IOException("Expired artwork read");
                        }
                        if (output.size() + count > Az100ArtStore.MAX_BYTES) throw new java.io.IOException("Oversized artwork");
                        output.write(buffer, 0, count);
                    }
                    byte[] bytes = output.toByteArray();
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    if (bounds.outWidth > 0 && bounds.outHeight > 0
                            && bounds.outWidth <= Az100ArtStore.MAX_DIMENSION
                            && bounds.outHeight <= Az100ArtStore.MAX_DIMENSION) {
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    }
                }
            } catch (Exception ignored) { }
            finally { active.set(null); timeout.cancel(false); }
            Bitmap result = bitmap;
            main.post(() -> {
                loading = false;
                if (request != generation.get()) {
                    if (result != null) result.recycle();
                    List<View> live;
                    synchronized (roots) { live = new ArrayList<>(roots.keySet()); }
                    if (!live.isEmpty()) preload(live.get(0).getContext());
                    return;
                }
                cached = result;
                if (result != null) {
                    List<View> live;
                    synchronized (roots) { live = new ArrayList<>(roots.keySet()); }
                    for (View root : live) refresh(root);
                }
            });
        });
    }

    private void apply(ImageView image, Binding binding, Bitmap bitmap) {
        View root = binding.root.get();
        if (bitmap == null || bitmap.isRecycled() || root == null
                || !binding.address.equals(identity(root))) return;
        rendering.set(true);
        try { image.setImageBitmap(bitmap); image.setScaleType(ImageView.ScaleType.FIT_CENTER); }
        finally { rendering.set(false); }
    }

    private void restore(ImageView image) {
        Binding binding = bindings.remove(image);
        if (binding == null) return;
        rendering.set(true);
        try {
            Drawable original = binding.original.get();
            if (original == null && binding.originalState != null) {
                original = binding.originalState.newDrawable(image.getResources());
            }
            if (original != null || binding.wasEmpty) image.setImageDrawable(original);
            image.setScaleType(binding.scale);
        }
        finally { rendering.set(false); }
    }

    private static void visit(View view, java.util.function.Consumer<ImageView> consumer) {
        if (view instanceof ImageView image) consumer.accept(image);
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) visit(group.getChildAt(i), consumer);
    }

    private String identity(Object owner) {
        return deviceIdentity.targetAddress(owner);
    }

    private static final class Binding {
        final WeakReference<View> root;
        final String address;
        WeakReference<Drawable> original;
        Drawable.ConstantState originalState;
        final ImageView.ScaleType scale;
        final boolean wasEmpty;
        Binding(View root, String address, Drawable original, ImageView.ScaleType scale) {
            this.root = new WeakReference<>(root); this.address = address; remember(original); this.scale = scale;
            wasEmpty = original == null;
        }
        void remember(Drawable drawable) {
            original = new WeakReference<>(drawable);
            originalState = drawable == null ? null : drawable.getConstantState();
        }
    }
}
