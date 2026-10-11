package dev.technics.pods.hook;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
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

/** Artwork replacement restricted to verified host IDs and the current AZ100 identity. */
final class Az100ArtHook {
    private static final String TAG = "TechnicsPods-Art";
    // Verified in HuaweiPods MiLinkServiceHook.syncMiLinkHeadsetIcon, not inferred from size.
    private static final Set<String> MILINK_IMAGE_IDS = Set.of("device_item",
            "circulate_headset_icon", "circulate_single_battery_headset_icon");
    /** Optional per-ROM IDs. An empty set enables the conservative single-header selector. */
    private static final Set<String> SETTINGS_IMAGE_IDS = Set.of();
    private final XposedModule module;
    private final String packageName;
    private final ClassLoader loader;
    private final boolean diagnostics;
    private final Set<String> discoveredImages = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<View, Boolean> pendingDiscovery = Collections.synchronizedMap(new WeakHashMap<>());
    private final Az100HookDeviceIdentity deviceIdentity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TechnicsPods-art-loader");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<View, String> roots = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, String> explicitRootAddresses = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, Boolean> rootListeners = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, Boolean> layoutRefreshPosts = Collections.synchronizedMap(new WeakHashMap<>());
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
    private final Set<String> loggedEvents = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean observing;
    private int installedHooks;

    Az100ArtHook(XposedModule module, String packageName, ClassLoader loader) {
        this(module, packageName, loader, false);
    }

    Az100ArtHook(XposedModule module, String packageName, ClassLoader loader, boolean diagnostics) {
        this.module = module;
        this.packageName = packageName;
        this.loader = loader;
        this.diagnostics = diagnostics;
        deviceIdentity = new Az100HookDeviceIdentity(module, () -> main.post(() -> {
            generation.incrementAndGet();
            cached = null;
            List<View> live;
            synchronized (roots) { live = new ArrayList<>(roots.keySet()); }
            for (View root : live) { visit(root, this::restore); explicitRootAddresses.remove(root); refresh(root); }
        }));
    }

    void install() {
        if (!CaptureContract.SETTINGS_PACKAGE.equals(packageName)
                && !CaptureContract.MILINK_SERVICE_PACKAGE.equals(packageName)) return;
        installCapabilityReport();
        if (CaptureContract.SETTINGS_PACKAGE.equals(packageName)) {
            module.log(Log.INFO, TAG, "Settings artwork uses verified AZ100 identity and conservative header selection");
            installImageViewWriteGuards();
            installSettingsDiscovery();
            return;
        }
        if (!CaptureContract.MILINK_SERVICE_PACKAGE.equals(packageName)) return;
        try {
            Class<?> detail = Class.forName("com.miui.circulateplus.world.headset.HeadSetsDetail", false, loader);
            Set<String> methods = Set.of("onAttachedToWindow", "onFinishInflate",
                    "setAttachedDeviceInfo", "setHeadsetDeviceInfo", "setHeadsetInfo", "updateState", "x");
            // The detail view often performs its first image bind from a constructor
            // and never calls one of the public refresh methods.  Register every
            // constructor, but still require the strict Bluetooth identity in
            // refresh() before touching a child ImageView.
            for (java.lang.reflect.Constructor<?> constructor : detail.getDeclaredConstructors()) {
                try {
                    constructor.setAccessible(true);
                    module.hook(constructor).setId("technicspods.art:detail:" + constructor.toGenericString())
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                                Object result = chain.proceed();
                                if (chain.getThisObject() instanceof View root) rememberRoot(root, chain.getArgs());
                                return result;
                            });
                    installedHooks++;
                } catch (Throwable error) {
                    module.log(Log.INFO, TAG, "MiLink detail constructor hook skipped", error);
                }
            }
            for (Method method : detail.getDeclaredMethods()) {
                if (!methods.contains(method.getName()) || method.getReturnType() != void.class) continue;
                module.hook(method).setId("technicspods.art:" + method.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            if (chain.getThisObject() instanceof View root) rememberRoot(root, chain.getArgs());
                            return result;
                        });
                installedHooks++;
            }
            installImageViewWriteGuards();
            module.log(Log.INFO, TAG, "MiLink artwork hooks installed with verified resource IDs");
        } catch (Throwable error) {
            module.log(Log.INFO, TAG, "MiLink artwork unavailable for this host build", error);
        }
        reportCapability(currentApplication());
    }

    private void installSettingsDiscovery() {
        try {
            Class<?> fragment = Class.forName("com.android.settings.bluetooth.MiuiHeadsetFragment", false, loader);
            for (Method method : fragment.getDeclaredMethods()) {
                if (!Set.of("onCreateView", "onViewCreated", "onResume", "onServiceConnected", "refreshStatus").contains(method.getName())) continue;
                final String callback = method.getName();
                module.hook(method).setId("technicspods.art:settings-discovery:" + method.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            WeakReference<Object> owner = new WeakReference<>(chain.getThisObject());
                            View view = result instanceof View root ? root : fragmentView(owner.get());
                            String address = identity(owner.get());
                            if (view != null) main.post(() -> {
                                if (address == null) {
                                    logOnce("settings-identity", "Settings artwork discovery skipped: fragment has no verified AZ100 address");
                                    return;
                                }
                                if (!view.isAttachedToWindow() && pendingDiscovery.putIfAbsent(view, true) == null) {
                                    View root = view;
                                    root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                                        @Override public void onViewAttachedToWindow(View view) {
                                            root.removeOnAttachStateChangeListener(this);
                                            pendingDiscovery.remove(root);
                                            if (address.equals(identity(owner.get()))) {
                                                rememberRoot(root, List.of(address));
                                                if (diagnostics) logSettingsImages(root, address, callback);
                                            }
                                        }
                                        @Override public void onViewDetachedFromWindow(View view) { }
                                    });
                                } else if (view.isAttachedToWindow() && address.equals(identity(owner.get()))) {
                                    rememberRoot(view, List.of(address));
                                    if (diagnostics) logSettingsImages(view, address, callback);
                                }
                            });
                            return result;
                        });
            }
        } catch (Throwable error) {
            module.log(Log.INFO, TAG, "Settings artwork discovery unavailable", error);
        }
    }

    private static View fragmentView(Object fragment) {
        if (fragment == null) return null;
        try {
            Object result = fragment.getClass().getMethod("getView").invoke(fragment);
            return result instanceof View view ? view : null;
        } catch (ReflectiveOperationException error) { return null; }
    }

    private void logSettingsImages(View root, String address, String source) {
        logOnce("settings-host", "Settings product-image discovery host=" + hostBuild(root.getContext())
                + ";fragment=com.android.settings.bluetooth.MiuiHeadsetFragment");
        int[] count = {0};
        visit(root, image -> {
            if (++count[0] > 64 || discoveredImages.size() >= 64) return;
            String resource;
            try { resource = image.getResources().getResourceName(image.getId()); }
            catch (RuntimeException ignored) { return; }
            if (!discoveredImages.add(resource)) return;
            Drawable drawable = image.getDrawable();
            String layout = image.getLayoutParams() == null ? "none"
                    : image.getLayoutParams().width + "x" + image.getLayoutParams().height;
            module.log(Log.INFO, TAG, "Settings image candidate=" + resource + ";address=" + address + ";callback=" + source + ";view="
                    + image.getClass().getName() + ";drawable="
                    + (drawable == null ? "null" : drawable.getClass().getName())
                    + ";size=" + image.getWidth() + "x" + image.getHeight() + ";layout=" + layout
                    + ";visible=" + image.isShown() + ";parents=" + parentIds(image, root));
        });
    }

    /**
     * Settings layouts have no stable image resource ID across HyperOS releases.  A
     * candidate must be a visible, already-sized, large image in the upper part of
     * the selected AZ100 detail page.  Ambiguous pages are left untouched.
     */
    private ImageView settingsImageCandidate(View root) {
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return null;
        float density = root.getResources().getDisplayMetrics().density;
        int minSize = Math.max(96, Math.round(112f * density));
        List<ImageView> candidates = new ArrayList<>();
        visit(root, image -> {
            if (!image.isShown() || image.getAlpha() <= 0f || image.getWidth() < minSize
                    || image.getHeight() < minSize || image.getTop() > root.getHeight() * 0.72f) return;
            float ratio = image.getWidth() / (float) image.getHeight();
            if (ratio < 0.45f || ratio > 2.8f) return;
            candidates.add(image);
        });
        if (candidates.isEmpty()) return null;
        candidates.sort((left, right) -> Long.compare(imageArea(right), imageArea(left)));
        ImageView best = candidates.get(0);
        if (candidates.size() > 1 && imageArea(best) * 100L < imageArea(candidates.get(1)) * 125L) {
            logOnce("settings-ambiguous", "Settings artwork candidate ambiguous; preserving host image");
            return null;
        }
        return best;
    }

    private static long imageArea(ImageView image) {
        return (long) image.getWidth() * (long) image.getHeight();
    }

    private void installImageViewWriteGuards() {
        for (String setter : List.of("setImageDrawable", "setImageBitmap", "setImageResource")) {
            try {
                Class<?> argument = switch (setter) {
                    case "setImageBitmap" -> Bitmap.class;
                    case "setImageResource" -> int.class;
                    default -> Drawable.class;
                };
                Method method = ImageView.class.getDeclaredMethod(setter, argument);
                module.hook(method).setId("technicspods.art:guard:" + packageName + ":" + setter)
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
                                        if (current != null && current.getConstantState() == null) {
                                            bindings.remove(image);
                                            image.setScaleType(binding.scale);
                                            return result;
                                        }
                                        binding.remember(current);
                                    }
                                    View root = binding.root.get();
                                    if (root == null || !binding.address.equals(rootAddress(root))) restore(image);
                                    else apply(image, binding, cached);
                                }
                            }
                            return result;
                        });
                installedHooks++;
            } catch (Throwable error) {
                module.log(Log.INFO, TAG, "ImageView write guard skipped: " + setter, error);
            }
        }
    }

    private static String parentIds(View view, View root) {
        StringBuilder path = new StringBuilder();
        View current = view;
        for (int i = 0; i < 5 && current != root && current.getParent() instanceof View parent; i++) {
            if (path.length() > 0) path.append('/');
            try { path.append(parent.getResources().getResourceEntryName(parent.getId())); }
            catch (RuntimeException missing) { path.append(parent.getClass().getSimpleName()); }
            current = parent;
        }
        return path.toString();
    }

    private static String hostBuild(Context context) {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return context.getPackageName() + "/" + info.versionName + "/" + info.getLongVersionCode()
                    + ";sdk=" + android.os.Build.VERSION.SDK_INT + ";rom=" + android.os.Build.VERSION.INCREMENTAL;
        } catch (Exception ignored) { return context.getPackageName(); }
    }

    private void rememberRoot(View root, List<Object> args) {
        if (root == null) return;
        String explicit = addressFromArgs(args);
        if (explicit != null) explicitRootAddresses.put(root, explicit);
        if (rootListeners.putIfAbsent(root, true) == null) {
            root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { refresh(view); }
                @Override public void onViewDetachedFromWindow(View view) {
                    visit(view, Az100ArtHook.this::restore);
                    roots.remove(view);
                    explicitRootAddresses.remove(view);
                }
            });
        }
        refresh(root);
    }

    private String addressFromArgs(List<Object> args) {
        if (args == null) return null;
        String address = null;
        for (Object argument : args) {
            String candidate = deviceIdentity.targetAddress(argument);
            if (candidate != null) {
                if (address != null && !address.equals(candidate)) return null;
                address = candidate;
            } else if (argument instanceof String value && deviceIdentity.permitsKnownAddress(value)) {
                String candidateAddress = Az100HookDeviceIdentity.normalize(value);
                if (candidateAddress != null) {
                    if (address != null && !address.equals(candidateAddress)) return null;
                    address = candidateAddress;
                }
            }
        }
        return address;
    }

    private void logOnce(String key, String message) {
        if (loggedEvents.add(key)) module.log(Log.INFO, TAG, message);
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
                        ? "Settings conservative AZ100 header selection;strict device identity required"
                        : "MiLink verified IDs=3;strict device identity required");
    }

    private void refresh(View root) {
        String resolvedAddress = explicitRootAddresses.get(root);
        if (resolvedAddress == null) resolvedAddress = identity(root);
        if (resolvedAddress == null) {
            if (CaptureContract.MILINK_SERVICE_PACKAGE.equals(packageName)) {
                logOnce("milink-identity", "MiLink artwork deferred: HeadSetsDetail exposed no selected AZ100 Bluetooth address");
            }
            roots.remove(root);
            explicitRootAddresses.remove(root);
            visit(root, this::restore);
            return;
        }
        final String address = resolvedAddress;
        String previous = roots.put(root, address);
        if (previous != null && !previous.equals(address)) visit(root, this::restore);
        ImageView settingsCandidate = CaptureContract.SETTINGS_PACKAGE.equals(packageName)
                ? settingsImageCandidate(root) : null;
        if (settingsCandidate != null) {
            try {
                String resource = settingsCandidate.getResources().getResourceName(settingsCandidate.getId());
                logOnce("settings-selected:" + resource,
                        "Settings product-image selected=" + resource + ";address=" + address
                                + ";size=" + settingsCandidate.getWidth() + "x" + settingsCandidate.getHeight());
            } catch (RuntimeException ignored) {
                logOnce("settings-selected:anonymous", "Settings product-image selected anonymous ImageView;address=" + address);
            }
        }
        visit(root, image -> {
            String entry;
            try { entry = image.getResources().getResourceEntryName(image.getId()); }
            catch (RuntimeException error) { entry = ""; }
            if (CaptureContract.MILINK_SERVICE_PACKAGE.equals(packageName)) {
                if (!MILINK_IMAGE_IDS.contains(entry)) return;
            } else {
                if (settingsCandidate == null || image != settingsCandidate) return;
                // A populated allow-list takes precedence when a ROM has been
                // verified.  Otherwise use the conservative candidate above.
                if (!SETTINGS_IMAGE_IDS.isEmpty() && !SETTINGS_IMAGE_IDS.contains(entry)) return;
            }
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
        if (CaptureContract.SETTINGS_PACKAGE.equals(packageName) && root.isAttachedToWindow()
                && settingsCandidate == null && (root.getWidth() <= 0 || root.getHeight() <= 0)
                && layoutRefreshPosts.putIfAbsent(root, true) == null) {
            root.post(() -> {
                layoutRefreshPosts.remove(root);
                if (root.isAttachedToWindow() && address.equals(rootAddress(root))) refresh(root);
            });
        }
    }

    private synchronized void observe(Context context) {
        if (observing) return;
        try {
            context.getContentResolver().registerContentObserver(Az100ArtProvider.ART_URI,
                    true, new ContentObserver(main) {
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
                || !binding.address.equals(rootAddress(root))) return;
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

    private String rootAddress(View root) {
        if (root == null) return null;
        String explicit = explicitRootAddresses.get(root);
        return explicit != null ? explicit : identity(root);
    }

    private static final class Binding {
        final WeakReference<View> root;
        final String address;
        WeakReference<Drawable> original;
        Drawable.ConstantState originalState;
        final ImageView.ScaleType scale;
        boolean wasEmpty;
        Binding(View root, String address, Drawable original, ImageView.ScaleType scale) {
            this.root = new WeakReference<>(root); this.address = address; remember(original); this.scale = scale;
        }
        void remember(Drawable drawable) {
            original = new WeakReference<>(drawable);
            originalState = drawable == null ? null : drawable.getConstantState();
            wasEmpty = drawable == null;
        }
    }
}
