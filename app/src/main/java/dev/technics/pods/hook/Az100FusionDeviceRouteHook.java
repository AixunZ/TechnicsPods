package dev.technics.pods.hook;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.technics.pods.diagnostic.Az100DeviceMatcher;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Repairs the AZ100 headset-service lookup used by MiLink's fusion device card. */
final class Az100FusionDeviceRouteHook {
    private static final String TAG = "TechnicsPods-FusionRoute";
    private static final String DEVICE_INFO =
            "com.miui.circulate.api.service.CirculateDeviceInfo";
    private static final String SERVICE_INFO =
            "com.miui.circulate.api.service.CirculateServiceInfo";
    private static final String HEADSET_SERVICE_CLIENT =
            "com.miui.circulate.api.protocol.headset.HeadsetServiceClient";
    private static final String MAIN_CARD_VIEW =
            "com.miui.circulate.world.sticker.MainCardView";
    private static final String PANEL_INFO = RomCompatibilityProfile.PANEL_INFO;
    private static final String DISPLAY_CONFIG = RomCompatibilityProfile.DISPLAY_CONFIG;

    private final XposedModule module;
    private final ClassLoader classLoader;
    private final Az100HookDeviceIdentity identity;
    private final boolean diagnostics;
    private final Map<View, Object> routeTokens = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private final Set<String> diagnosticEvents = ConcurrentHashMap.newKeySet();
    private final Set<String> observedControllerTypes = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Object> activeController = new ThreadLocal<>();
    private final ThreadLocal<Method> activeControllerMethod = new ThreadLocal<>();
    private volatile boolean installed;
    private volatile boolean headsetDiscoveryActive;
    private volatile int headsetSession;
    private volatile Object cachedHeadsetService;
    private volatile Object cachedHeadsetPanel;
    private volatile Object cachedHeadsetDisplay;
    private volatile Object cachedHeadsetController;
    private volatile Method cachedPerformCardClick;
    private volatile Context appContext;
    private boolean obfuscatedProfile;

    Az100FusionDeviceRouteHook(XposedModule module, ClassLoader classLoader, boolean diagnostics) {
        this.module = module;
        this.classLoader = classLoader;
        this.diagnostics = diagnostics;
        identity = new Az100HookDeviceIdentity(module, () -> {
            headsetSession++;
            routeTokens.clear();
            cachedHeadsetService = null;
            cachedHeadsetController = null;
        });
    }

    void install() throws ReflectiveOperationException {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread", false, classLoader);
            Object app = thread.getDeclaredMethod("currentApplication").invoke(null);
            if (app instanceof Application value) appContext = value.getApplicationContext();
        } catch (Throwable ignored) {}
        installWhenReady();
        Method method = Instrumentation.class.getDeclaredMethod(
                "callApplicationOnCreate", Application.class);
        method.setAccessible(true);
        module.hook(method)
                .setId("technicspods.fusion-route:application-on-create")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (chain.getArg(0) instanceof Application value) appContext = value.getApplicationContext();
                    installWhenReady();
                    return result;
                });
    }

    private synchronized void installWhenReady() {
        if (installed) return;
        if (appContext == null) return;
        String actualHost;
        try {
            PackageInfo host = appContext.getPackageManager().getPackageInfo(
                    appContext.getPackageName(), PackageManager.PackageInfoFlags.of(0L));
            actualHost = "sdk=" + android.os.Build.VERSION.SDK_INT
                    + ";rom=" + android.os.Build.VERSION.INCREMENTAL
                    + ";host=" + appContext.getPackageName()
                    + ";version=" + host.versionName + "/" + host.getLongVersionCode();
            obfuscatedProfile = RomCompatibilityProfile.allowsObfuscatedFusion(
                    android.os.Build.VERSION.SDK_INT, android.os.Build.VERSION.INCREMENTAL,
                    appContext.getPackageName(), host.getLongVersionCode(), host.versionName);
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            actualHost = "host=" + appContext.getPackageName() + ";version=unavailable";
            obfuscatedProfile = false;
        }
        try {
            Class<?> deviceInfo = Class.forName(DEVICE_INFO, false, classLoader);
            int count = 0;
            for (Method method : deviceInfo.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                boolean find = "find".equals(method.getName())
                        && (parameters.length == 1 || parameters.length == 2)
                        && parameters[0] == int.class;
                boolean support = "support".equals(method.getName())
                        && parameters.length == 1 && parameters[0] == int.class;
                if (Modifier.isStatic(method.getModifiers()) || (!find && !support)) {
                    continue;
                }
                method.setAccessible(true);
                module.hook(method)
                        .setId("technicspods.fusion-route:" + signature(method))
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> interceptLookup(method, chain));
                count++;
            }
            count += installServiceObserver();
            count += installHeadsetLifecycleObserver();
            int routeHooks = 0;
            if (obfuscatedProfile) {
                routeHooks = installPanelRoute();
                // The tested ROM's controller is discovered once by its verified shape.
                inspectControllerType(RomCompatibilityProfile.CONTROLLER, "verified");
            }
            count += routeHooks;
            installed = true;
            module.log(Log.INFO, TAG, "AZ100 fusion route ready;hooks=" + count);
            HostCapabilityReport.report(appContext, "fusion_service", count - routeHooks,
                    actualHost + ";generic exact-signature service recognition");
            HostCapabilityReport.report(appContext, "fusion_route", routeHooks,
                    actualHost + (obfuscatedProfile
                            ? ";MainCardView.k/q,wc.a,wc.b,fc.t.b0;rendering pending hardware"
                            : ";unknown compatibility profile;obfuscated route disabled"));
        } catch (ClassNotFoundException ignored) {
            module.log(Log.INFO, TAG, "MiLink circulation classes are not ready");
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to install AZ100 fusion route", error);
        }
    }

    private int installPanelRoute() {
        try {
            Class<?> mainCardView = Class.forName(MAIN_CARD_VIEW, false, classLoader);
            int count = 0;
            for (Method method : mainCardView.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if ("q".equals(method.getName()) && parameters.length == 7
                        && PANEL_INFO.equals(parameters[1].getName())
                        && DEVICE_INFO.equals(parameters[2].getName())) {
                    method.setAccessible(true);
                    module.hook(method)
                            .setId("technicspods.fusion-route:main-card-panel")
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> interceptPanel(chain));
                    count++;
                }
                if ("k".equals(method.getName()) && parameters.length == 6
                        && DEVICE_INFO.equals(parameters[0].getName())
                        && DISPLAY_CONFIG.equals(parameters[3].getName())) {
                    method.setAccessible(true);
                    module.hook(method)
                            .setId("technicspods.fusion-route:main-card-display-config")
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> interceptDisplayConfig(chain));
                    count++;
                }
            }
            return count;
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to install AZ100 panel route", error);
        }
        return 0;
    }

    /** MainCardView.k selects the concrete display configuration before q inflates it. */
    private Object interceptDisplayConfig(XposedInterface.Chain chain) throws Throwable {
        Object deviceInfo = chain.getArg(0);
        if (!isTargetDeviceInfo(deviceInfo)) return chain.proceed();

        Context context = chain.getThisObject() instanceof View view
                ? view.getContext() : null;
        Object display = findHeadsetDisplay(context);
        Object original = chain.getArg(3);
        if (display == null || original == null || !original.getClass().isInstance(display)) {
            logOnce("display-config-missed", "Could not resolve Headset display config for AZ100");
            return chain.proceed();
        }

        Object[] arguments = chain.getArgs().toArray();
        arguments[3] = display;
        logOnce("display-config-route",
                "Routed AZ100 MainCardView to Headset display config");
        return chain.proceed(arguments);
    }

    private Object interceptPanel(XposedInterface.Chain chain) throws Throwable {
        Object deviceInfo = chain.getArg(2);
        View cardView = chain.getArg(0) instanceof View view ? view : null;
        if (cardView != null) routeTokens.remove(cardView);
        if (!isTargetDeviceInfo(deviceInfo)) return chain.proceed();
        if (cardView != null && isHeadsetClickRouteReady()) {
            Object result = chain.proceed();
            scheduleHeadsetCardClick(cardView);
            return result;
        }

        Context context = cardView == null ? null : cardView.getContext();
        Object headsetPanel = findHeadsetPanel(context);
        if (headsetPanel == null || headsetPanel == chain.getArg(1)) return chain.proceed();

        Object[] arguments = chain.getArgs().toArray();
        if (headsetPanel != null && headsetPanel != chain.getArg(1)) {
            arguments[1] = headsetPanel;
            logOnce("panel-route", "Routed AZ100 fusion card to system Headset panel");
        }
        Object result = chain.proceed(arguments);
        if (cardView != null) scheduleHeadsetCardClick(cardView);
        return result;
    }

    private void scheduleHeadsetCardClick(View view) {
        Object token = new Object();
        routeTokens.put(view, token);
        int session = headsetSession;
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View value) {}
            @Override public void onViewDetachedFromWindow(View value) {
                routeTokens.remove(value);
                value.removeOnAttachStateChangeListener(this);
            }
        });
        scheduleHeadsetCardClick(view, 0, session, token);
    }

    private void scheduleHeadsetCardClick(View view, int attempt, int session, Object token) {
        if (headsetSession != session || routeTokens.get(view) != token
                || !view.isAttachedToWindow() || !headsetDiscoveryActive) return;
        if (performHeadsetCardClick()) {
            routeTokens.remove(view);
            logOnce("click-route:" + headsetSession,
                    "Routed AZ100 MainCardView request to HeadsetContentController");
            return;
        }
        if (attempt >= 30) return;
        view.postDelayed(() -> scheduleHeadsetCardClick(view, attempt + 1, session, token), 500L);
    }

    private synchronized Object findHeadsetPanel(Context context) {
        if (cachedHeadsetPanel != null) return cachedHeadsetPanel;
        if (context == null) return null;
        try {
            Object namedPanel = findNamedHeadsetPanel();
            if (namedPanel != null) return namedPanel;

            Class<?> panelType = Class.forName(PANEL_INFO, false, classLoader);
            ArrayList<Method> factories = new ArrayList<>();
            for (Method method : panelType.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 0
                        && method.getReturnType() == panelType) {
                    factories.add(method);
                }
            }
            factories.sort(Comparator.comparing(Method::getName));
            Map<Object, Boolean> seen = new IdentityHashMap<>();
            for (Method factory : factories) {
                factory.setAccessible(true);
                Object panel = factory.invoke(null);
                if (panel == null || seen.put(panel, Boolean.TRUE) != null) continue;
                Set<String> resources = panelResources(panel, context.getResources());
                boolean headset = resources.stream()
                        .anyMatch(Az100FusionRoutePolicy::isHeadsetPanelResource);
                if (headset) {
                    cachedHeadsetPanel = panel;
                    module.log(Log.INFO, TAG, "Found system Headset panel;factory="
                            + factory.getName() + ";resources=" + resources);
                    return panel;
                }
            }
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to resolve system Headset panel", error);
        }
        logOnce("panel-missed", "Could not identify system Headset panel resources");
        return null;
    }

    private synchronized Object findHeadsetDisplay(Context context) {
        if (cachedHeadsetDisplay != null) return cachedHeadsetDisplay;
        findNamedHeadsetPanel();
        if (cachedHeadsetDisplay != null) return cachedHeadsetDisplay;

        Object panel = findHeadsetPanel(context);
        if (panel == null) return null;
        try {
            Class<?> displayType = Class.forName(DISPLAY_CONFIG, false, classLoader);
            ArrayList<Method> factories = new ArrayList<>();
            for (Method method : displayType.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 0
                        && method.getReturnType() == displayType) {
                    factories.add(method);
                }
            }
            factories.sort(Comparator.comparing(Method::getName));
            Map<Object, Boolean> seen = new IdentityHashMap<>();
            for (Method factory : factories) {
                factory.setAccessible(true);
                Object display = factory.invoke(null);
                if (display == null || seen.put(display, Boolean.TRUE) != null) continue;
                Object displayPanel = invokeNoArgs(display, "t");
                if (displayPanel == panel || panel.equals(displayPanel)) {
                    cachedHeadsetDisplay = display;
                    module.log(Log.INFO, TAG, "Found Headset display config by panel;factory="
                            + factory.getName());
                    return display;
                }
            }
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to match Headset display config to panel", error);
        }
        return null;
    }

    private Object findNamedHeadsetPanel() {
        try {
            Class<?> displayType = Class.forName(DISPLAY_CONFIG, false, classLoader);
            ArrayList<Method> factories = new ArrayList<>();
            ArrayList<String> labels = new ArrayList<>();
            for (Method method : displayType.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 0
                        && method.getReturnType() == displayType) {
                    factories.add(method);
                }
            }
            factories.sort(Comparator.comparing(Method::getName));
            Map<Object, Boolean> seen = new IdentityHashMap<>();
            for (Method factory : factories) {
                factory.setAccessible(true);
                Object display = factory.invoke(null);
                if (display == null || seen.put(display, Boolean.TRUE) != null) continue;
                Object labelValue = invokeNoArgs(display, "s");
                String label = labelValue instanceof String text ? text : null;
                labels.add(factory.getName() + '=' + label);
                if (!Az100FusionRoutePolicy.isHeadsetPanelResource(label)) continue;
                Object panel = invokeNoArgs(display, "t");
                if (panel == null || !PANEL_INFO.equals(panel.getClass().getName())) continue;
                cachedHeadsetPanel = panel;
                cachedHeadsetDisplay = display;
                module.log(Log.INFO, TAG, "Found named system Headset panel;factory="
                        + factory.getName() + ";label=" + label);
                return panel;
            }
            logOnce("panel-labels", "System panel labels=" + labels);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to resolve named Headset panel", error);
        }
        return null;
    }

    private static Set<String> panelResources(Object panel, Resources resources) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Class<?> type = panel.getClass(); type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) continue;
                try {
                    field.setAccessible(true);
                    addResourceName(names, resources, field.getInt(panel));
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }
        for (String getter : new String[]{"r", "s"}) {
            Object value = invokeNoArgs(panel, getter);
            if (value instanceof Number number) {
                addResourceName(names, resources, number.intValue());
            }
        }
        return names;
    }

    private static void addResourceName(Set<String> output, Resources resources, int value) {
        if (value == 0) return;
        try {
            output.add(resources.getResourceName(value));
        } catch (Resources.NotFoundException ignored) {
        }
    }

    private int installServiceObserver() {
        try {
            Class<?> serviceInfo = Class.forName(SERVICE_INFO, false, classLoader);
            Method method = serviceInfo.getDeclaredMethod("toString");
            method.setAccessible(true);
            module.hook(method)
                    .setId("technicspods.fusion-route:service-observer")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object service = chain.getThisObject();
                        cacheIfTargetService(service);
                        if (isTargetService(service)) {
                            captureActiveHeadsetController();
                            if (diagnostics && obfuscatedProfile) inspectHeadsetServiceCallers();
                        }
                        return result;
                    });
            return 1;
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to observe MiLink headset services", error);
            return 0;
        }
    }

    private int installHeadsetLifecycleObserver() {
        try {
            Class<?> clientType = Class.forName(HEADSET_SERVICE_CLIENT, false, classLoader);
            int count = 0;
            for (Method method : clientType.getDeclaredMethods()) {
                String name = method.getName();
                boolean start = "startDiscovery".equals(name);
                boolean stop = "stopDiscovery".equals(name) || "release".equals(name);
                if (Modifier.isStatic(method.getModifiers()) || (!start && !stop)) continue;
                method.setAccessible(true);
                module.hook(method)
                        .setId("technicspods.fusion-route:headset-lifecycle:" + signature(method))
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            if (start) {
                                beginHeadsetSession();
                                try {
                                    return chain.proceed();
                                } catch (Throwable error) {
                                    endHeadsetSession();
                                    throw error;
                                }
                            }
                            try {
                                return chain.proceed();
                            } finally {
                                endHeadsetSession();
                            }
                        });
                count++;
            }
            return count;
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to observe Headset discovery lifecycle", error);
            return 0;
        }
    }

    private synchronized void beginHeadsetSession() {
        cachedHeadsetService = null;
        headsetDiscoveryActive = true;
        headsetSession++;
        module.log(Log.INFO, TAG, "Started fresh Headset discovery session=" + headsetSession);
    }

    private synchronized void endHeadsetSession() {
        if (!headsetDiscoveryActive && cachedHeadsetService == null) return;
        headsetDiscoveryActive = false;
        headsetSession++;
        routeTokens.clear();
        cachedHeadsetService = null;
        cachedHeadsetController = null;
        module.log(Log.INFO, TAG, "Cleared Headset discovery session=" + headsetSession);
    }

    private void captureActiveHeadsetController() {
        Object controller = activeController.get();
        if (!isHeadsetControllerCandidate(controller)
                || controller == cachedHeadsetController) return;
        cachedHeadsetController = controller;
        Method running = activeControllerMethod.get();
        module.log(Log.INFO, TAG, "Captured AZ100 HeadsetContentController;type="
                + controller.getClass().getName()
                + ";via=" + (running == null ? "unknown" : signature(running))
                + ";identity=" + System.identityHashCode(controller));
    }

    private void inspectHeadsetServiceCallers() {
        int inspected = 0;
        ArrayList<String> frames = new ArrayList<>();
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName();
            if (!isMiLinkApplicationFrame(className)) continue;
            frames.add(className + '#' + frame.getMethodName() + ':' + frame.getLineNumber());
            if (inspected < 3 && inspectControllerType(className, frame.getMethodName())) {
                inspected++;
            }
            if (frames.size() >= 10) break;
        }
        if (!frames.isEmpty()) {
            logOnce("headset-service-stack", "AZ100 Headset service callers=" + frames);
        }
    }

    private boolean isMiLinkApplicationFrame(String className) {
        if (className.equals(getClass().getName())
                || className.startsWith("com.miui.circulate.api.")
                || className.startsWith("java.")
                || className.startsWith("android.")
                || className.startsWith("androidx.")
                || className.startsWith("kotlin.")
                || className.startsWith("kotlinx.")
                || className.startsWith("io.github.libxposed.")) {
            return false;
        }
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            return type.getClassLoader() == classLoader;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private synchronized boolean inspectControllerType(String className, String callerMethod) {
        if (observedControllerTypes.size() >= 8) return false;
        if (!observedControllerTypes.add(className)) return true;
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            ArrayList<String> surface = new ArrayList<>();
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    surface.add("field " + field.getType().getName() + ' ' + field.getName());
                }
            }
            int hooks = 0;
            boolean headsetController = hasHeadsetControllerShape(type);
            if (!headsetController) return false;
            for (Method method : type.getDeclaredMethods()) {
                if (diagnostics) surface.add("method " + method.toGenericString());
                if (Modifier.isStatic(method.getModifiers())
                        || Modifier.isAbstract(method.getModifiers())
                        || Modifier.isNative(method.getModifiers())) {
                    continue;
                }
                if (headsetController && ("performCardClick".equals(method.getName())
                        || (RomCompatibilityProfile.CARD_CLICK.equals(method.getName())
                        && method.getParameterCount() == 0
                        && method.getReturnType() == Void.TYPE))) {
                    cachedPerformCardClick = method;
                }
                method.setAccessible(true);
                module.hook(method)
                        .setId("technicspods.fusion-route:headset-controller:"
                                + className + ':' + signature(method))
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> observeControllerCall(method, chain));
                hooks++;
            }
            surface.sort(Comparator.naturalOrder());
            if (diagnostics) module.log(Log.INFO, TAG, "Inspected AZ100 service caller;type=" + className
                    + ";caller=" + callerMethod + ";headsetController=" + headsetController
                    + ";hooks=" + hooks + ";surface=" + surface);
            if (cachedPerformCardClick != null
                    && cachedPerformCardClick.getDeclaringClass() == type) {
                module.log(Log.INFO, TAG, "Found named Headset click method="
                        + cachedPerformCardClick.toGenericString());
            }
            return true;
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to inspect AZ100 service caller " + className,
                    error);
            return false;
        }
    }

    private boolean isHeadsetControllerCandidate(Object value) {
        return value != null && hasHeadsetControllerShape(value.getClass());
    }

    private boolean hasHeadsetControllerShape(Class<?> type) {
        boolean hasManager = false;
        for (Field field : type.getDeclaredFields()) {
            if (field.getType().getName().equals(
                    "com.miui.circulate.world.headset.HeadsetContentManager")) {
                hasManager = true;
                break;
            }
        }
        if (!hasManager) return false;
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterCount() == 0
                    && method.getReturnType() == Void.TYPE
                    && ("performCardClick".equals(method.getName())
                    || RomCompatibilityProfile.CARD_CLICK.equals(method.getName()))) {
                return true;
            }
        }
        return false;
    }

    private Object observeControllerCall(Method method, XposedInterface.Chain chain)
            throws Throwable {
        Object previousController = activeController.get();
        Method previousMethod = activeControllerMethod.get();
        Object controller = chain.getThisObject();
        activeController.set(controller);
        activeControllerMethod.set(method);
        try {
            Object result = chain.proceed();
            if (controller == cachedHeadsetController) {
                logOnce("controller-call:" + headsetSession + ':' + signature(method),
                        "AZ100 Headset controller call=" + signature(method));
            }
            return result;
        } finally {
            if (previousController == null) {
                activeController.remove();
            } else {
                activeController.set(previousController);
            }
            if (previousMethod == null) {
                activeControllerMethod.remove();
            } else {
                activeControllerMethod.set(previousMethod);
            }
        }
    }

    private boolean performHeadsetCardClick() {
        Object controller = cachedHeadsetController;
        Method click = cachedPerformCardClick;
        if (!isHeadsetClickRouteReady()) {
            logOnce("click-route-not-ready:" + headsetSession,
                    "Headset click route not ready;controller="
                            + (controller == null ? "null" : controller.getClass().getName())
                            + ";method=" + (click == null ? "null" : click.toGenericString()));
            return false;
        }
        try {
            click.setAccessible(true);
            click.invoke(controller);
            return true;
        } catch (InvocationTargetException error) {
            module.log(Log.WARN, TAG, "Native Headset click method failed", error.getCause());
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Could not invoke native Headset click method", error);
        }
        return false;
    }

    private boolean isHeadsetClickRouteReady() {
        Object controller = cachedHeadsetController;
        Method click = cachedPerformCardClick;
        return controller != null && click != null
                && click.getDeclaringClass().isInstance(controller)
                && click.getParameterCount() == 0;
    }

    private Object interceptLookup(Method method, XposedInterface.Chain chain) throws Throwable {
        int protocol = (Integer) chain.getArg(0);
        Object deviceInfo = chain.getThisObject();
        Object original = chain.proceed();
        if (!isTargetDeviceInfo(deviceInfo)) return original;

        if (protocol == Az100FusionRoutePolicy.HEADSET_PROTOCOL
                && isTargetService(original)) {
            cacheIfTargetService(original);
            return original;
        }

        Object cached = cachedHeadsetService;
        boolean lookupEmpty = isEmpty(original, method.getReturnType());
        if (protocol != Az100FusionRoutePolicy.HEADSET_PROTOCOL || !lookupEmpty || cached == null) {
            return original;
        }

        Object patched = patchedResult(method, cached);
        if (patched == null) return original;

        logOnce("patch:" + method.getName(),
                "Patched AZ100 fusion service lookup;method=" + method.getName()
                        + ";service=" + protocol
                        + ";returnType=" + method.getReturnType().getName());
        return patched;
    }

    private Object patchedResult(Method method, Object cached) {
        Class<?> returnType = method.getReturnType();
        if ("support".equals(method.getName())
                && (returnType == boolean.class || returnType == Boolean.class)) {
            return true;
        }
        return returnType.isInstance(cached) ? cached : null;
    }

    private synchronized void cacheIfTargetService(Object service) {
        if (!headsetDiscoveryActive || !isTargetService(service)) return;
        if (cachedHeadsetService == service) return;
        cachedHeadsetService = service;
        logOnce("cache:" + headsetSession, "Cached current AZ100 Headset service;session="
                + headsetSession + ";identity=" + System.identityHashCode(service));
    }

    private boolean isTargetService(Object service) {
        if (service == null || !SERVICE_INFO.equals(service.getClass().getName())) return false;
        Integer protocol = integerProperty(service, "protocolType", "getProtocolType");
        if (protocol == null || protocol != Az100FusionRoutePolicy.HEADSET_PROTOCOL) return false;
        String serviceId = stringProperty(service, "serviceId", "getServiceId");
        return identity.matchesOwnerOrName(service, serviceId);
    }

    private boolean isTargetDeviceInfo(Object deviceInfo) {
        return identity.matchesOwnerOrName(deviceInfo, deviceName(deviceInfo));
    }

    private static String deviceName(Object deviceInfo) {
        if (deviceInfo == null) return null;
        String direct = directDeviceName(deviceInfo);
        if (Az100DeviceMatcher.matchesName(direct)) return direct;
        String description = String.valueOf(deviceInfo);
        return Az100DeviceMatcher.matchesName(description) ? description : null;
    }

    private static String directDeviceName(Object deviceInfo) {
        if (deviceInfo == null) return null;
        for (String field : new String[]{"devicesName", "deviceName", "name"}) {
            Object value = fieldValue(deviceInfo, field);
            if (value instanceof String text && Az100DeviceMatcher.matchesName(text)) return text;
        }
        for (String getter : new String[]{"getDevicesName", "getDeviceName", "getName"}) {
            Object value = invokeNoArgs(deviceInfo, getter);
            if (value instanceof String text && Az100DeviceMatcher.matchesName(text)) return text;
        }
        return null;
    }

    private static Integer integerProperty(Object target, String field, String getter) {
        Object value = fieldValue(target, field);
        if (!(value instanceof Number)) value = invokeNoArgs(target, getter);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String stringProperty(Object target, String field, String getter) {
        Object value = fieldValue(target, field);
        if (!(value instanceof String)) value = invokeNoArgs(target, getter);
        return value instanceof String text ? text : null;
    }

    private static Object fieldValue(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class;
                type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static Object invokeNoArgs(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class;
                type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static boolean isEmpty(Object result, Class<?> returnType) {
        if (result == null) return true;
        if ((returnType == boolean.class || returnType == Boolean.class)
                && Boolean.FALSE.equals(result)) return true;
        return (returnType == int.class || returnType == Integer.class)
                && result instanceof Integer value && value == 0;
    }

    private void logOnce(String key, String message) {
        if (diagnosticEvents.add(key)) module.log(Log.INFO, TAG, message);
    }

    private static String signature(Method method) {
        StringBuilder output = new StringBuilder(method.getDeclaringClass().getName())
                .append(':').append(method.getName()).append('(');
        List<Class<?>> parameters = List.of(method.getParameterTypes());
        for (int index = 0; index < parameters.size(); index++) {
            if (index > 0) output.append(',');
            output.append(parameters.get(index).getName());
        }
        return output.append(')').toString();
    }
}
