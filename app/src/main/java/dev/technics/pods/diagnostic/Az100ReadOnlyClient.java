package dev.technics.pods.diagnostic;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.technics.pods.protocol.Az100BatteryUpdate;
import dev.technics.pods.protocol.Az100ControlResponses;
import dev.technics.pods.protocol.Az100ReadFrames;
import dev.technics.pods.protocol.Az100ReadResponses;
import dev.technics.pods.protocol.Az100RelayState;
import dev.technics.pods.protocol.PanasonicCommand;
import dev.technics.pods.protocol.RaceFrame;
import dev.technics.pods.protocol.RaceStreamDecoder;

/** A one-shot, user-triggered diagnostic client containing read requests only. */
public final class Az100ReadOnlyClient {
    public static final UUID RFCOMM_UUID = UUID.fromString("00000000-0000-0000-0099-aabbccddeeff");
    private static final long CONNECT_TIMEOUT_MILLIS = 12_000L;
    private static final long RESPONSE_TIMEOUT_MILLIS = 2_500L;

    private final Context context;
    private final BluetoothDevice targetDevice;
    private volatile BluetoothSocket activeSocket;
    private volatile boolean cancelled;
    private final ControlDeadline deadline = new ControlDeadline(SystemClock::elapsedRealtime, 45_000L);

    public Az100ReadOnlyClient(Context context, BluetoothDevice targetDevice) {
        this.context = context.getApplicationContext();
        this.targetDevice = java.util.Objects.requireNonNull(targetDevice);
    }

    @SuppressLint("MissingPermission")
    public Az100DiagnosticResult run() throws DiagnosticException {
        if (cancelled) throw new DiagnosticException("只读诊断已取消");
        long started = SystemClock.elapsedRealtime();
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) throw new DiagnosticException("此设备没有可用的蓝牙适配器");
        if (!adapter.isEnabled()) throw new DiagnosticException("请先打开蓝牙");

        BluetoothDevice device = targetDevice;

        List<String> warnings = new ArrayList<>();
        BluetoothSocket socket = null;
        ScheduledExecutorService deadlineTimer = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> totalTimeout = deadlineTimer.schedule(this::cancel, 45_000L, TimeUnit.MILLISECONDS);
        try {
            socket = device.createRfcommSocketToServiceRecord(RFCOMM_UUID);
            activeSocket = socket;
            deadline.attach(socket);
            if (cancelled) throw new DiagnosticException("只读诊断已取消");
            connectWithTimeout(socket);
            if (cancelled) throw new DiagnosticException("只读诊断已取消");

            Session session = new Session(socket.getInputStream(), socket.getOutputStream());
            RaceFrame model = session.exchange(
                    Az100ReadFrames.getModelId(),
                    frame -> isResponse(frame, PanasonicCommand.GET_MODEL_ID)
            );
            Az100ReadResponses.requireSuccessfulModelResponse(model);

            Boolean partnerAvailable = null;
            Integer leftBattery = null;
            Integer rightBattery = null;
            Integer cradleBattery = null;
            Integer outsideMode = null;
            Integer noiseLevel = null;
            Integer ambientLevel = null;
            Integer ambientMode = null;
            Boolean adaptiveAnc = null;
            String leftFirmware = null;
            String rightFirmware = null;

            try {
                RaceFrame relay = session.exchange(
                        Az100ReadFrames.getRelayDestinations(),
                        frame -> isResponse(frame, PanasonicCommand.RELAY_GET_AVAILABLE_DESTINATION)
                );
                partnerAvailable = Az100RelayState.from(relay).partnerAvailable();
            } catch (TimeoutException | IllegalArgumentException error) {
                warnings.add("另一只耳机状态");
            }

            try {
                Az100ControlResponses.OutsideControl outside = Az100ControlResponses.outsideControl(
                        session.exchange(
                                Az100ReadFrames.getOutsideControl(),
                                frame -> isResponse(frame, PanasonicCommand.GET_OUTSIDE_CONTROL)
                        )
                );
                outsideMode = outside.mode();
                noiseLevel = outside.noiseLevel();
                ambientLevel = outside.ambientLevel();
            } catch (TimeoutException | IllegalArgumentException error) {
                warnings.add("外音控制");
            }

            try {
                ambientMode = Az100ControlResponses.ambientMode(session.exchange(
                        Az100ReadFrames.getAmbientMode(),
                        frame -> isResponse(frame, PanasonicCommand.GET_AMBIENT_MODE)
                )).mode();
            } catch (TimeoutException | IllegalArgumentException error) {
                warnings.add("环境声子模式");
            }

            try {
                cradleBattery = Az100ReadResponses.cradleBattery(session.exchange(
                        Az100ReadFrames.getCradleBattery(),
                        frame -> isResponse(frame, PanasonicCommand.GET_CRADLE_BATTERY)
                ));
            } catch (TimeoutException | IllegalArgumentException error) {
                warnings.add("充电盒电量");
            }

            try {
                adaptiveAnc = Az100ControlResponses.adaptiveAnc(session.exchange(
                        Az100ReadFrames.getAdaptiveAnc(),
                        frame -> isResponse(frame, PanasonicCommand.GET_ADAPTIVE_ANC)
                ));
            } catch (TimeoutException | IllegalArgumentException error) {
                warnings.add("自适应降噪");
            }

            try {
                leftBattery = readBattery(session, Az100BatteryUpdate.LEFT);
            } catch (TimeoutException | IllegalArgumentException error) {
                warnings.add("左耳电量");
            }
            try {
                rightBattery = readBattery(session, Az100BatteryUpdate.RIGHT);
            } catch (TimeoutException | IllegalArgumentException error) {
                warnings.add("右耳电量");
            }
            try {
                leftFirmware = readFirmware(session, Az100BatteryUpdate.LEFT);
            } catch (TimeoutException | IllegalArgumentException error) {
                warnings.add("左耳固件");
            }
            try {
                rightFirmware = readFirmware(session, Az100BatteryUpdate.RIGHT);
            } catch (TimeoutException | IllegalArgumentException error) {
                warnings.add("右耳固件");
            }

            deadline.complete();
            return new Az100DiagnosticResult(
                    SystemClock.elapsedRealtime() - started,
                    leftBattery,
                    rightBattery,
                    cradleBattery,
                    outsideMode,
                    noiseLevel,
                    ambientLevel,
                    ambientMode,
                    adaptiveAnc,
                    partnerAvailable,
                    leftFirmware,
                    rightFirmware,
                    warnings
            );
        } catch (DiagnosticException error) {
            throw error;
        } catch (SecurityException error) {
            throw new DiagnosticException("缺少附近设备/蓝牙连接权限");
        } catch (IOException error) {
            throw new DiagnosticException("连接失败；请强制停止官方 App，并确认至少一只耳机已取出盒子");
        } catch (TimeoutException | IllegalArgumentException error) {
            throw new DiagnosticException("耳机已连接，但只读协议握手失败");
        } finally {
            totalTimeout.cancel(false);
            deadlineTimer.shutdownNow();
            closeQuietly(socket);
            activeSocket = null;
        }
    }

    public void cancel() {
        cancelled = true;
        deadline.cancel();
        closeQuietly(activeSocket);
    }

    @SuppressLint("MissingPermission")
    private static BluetoothDevice findBondedAz100(Set<BluetoothDevice> bondedDevices)
            throws DiagnosticException {
        BluetoothDevice match = null;
        for (BluetoothDevice candidate : bondedDevices) {
            if (!Az100DeviceMatcher.matchesName(candidate.getName())) continue;
            if (match != null) throw new DiagnosticException("发现多个已配对的 EAH-AZ100，无法安全选择");
            match = candidate;
        }
        return match;
    }

    private void connectWithTimeout(BluetoothSocket socket) throws IOException, DiagnosticException {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> timeout = timer.schedule(
                () -> closeQuietly(socket),
                CONNECT_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS
        );
        try {
            socket.connect();
            if (!socket.isConnected()) throw new DiagnosticException("RFCOMM 连接未建立");
        } finally {
            timeout.cancel(false);
            timer.shutdownNow();
        }
    }

    private static int readBattery(Session session, int side)
            throws IOException, TimeoutException {
        RaceFrame frame = session.exchange(
                Az100ReadFrames.getEarbudBattery(side),
                candidate -> candidate.type() == RaceFrame.TYPE_INDICATION
                        && candidate.commandId() == PanasonicCommand.TWS_GET_BATTERY
                        && hasSide(candidate, side)
        );
        return Az100BatteryUpdate.from(frame).percent();
    }

    private static String readFirmware(Session session, int side)
            throws IOException, TimeoutException {
        RaceFrame frame = session.exchange(
                Az100ReadFrames.getFirmwareVersion(side),
                candidate -> candidate.type() == RaceFrame.TYPE_INDICATION
                        && candidate.commandId() == PanasonicCommand.FOTA_GET_VERSION
                        && hasSide(candidate, side)
        );
        return Az100ReadResponses.firmwareVersion(frame).version();
    }

    private static boolean hasSide(RaceFrame frame, int side) {
        byte[] payload = frame.payload();
        return payload.length >= 2 && (payload[1] & 0xFF) == side;
    }

    private static boolean isResponse(RaceFrame frame, int commandId) {
        return frame.type() == RaceFrame.TYPE_RESPONSE && frame.commandId() == commandId;
    }

    private static void closeQuietly(BluetoothSocket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public static final class DiagnosticException extends Exception {
        public DiagnosticException(String safeMessage) {
            super(safeMessage);
        }
    }

    private interface FrameMatcher {
        boolean matches(RaceFrame frame);
    }

    private final class Session {
        private final InputStream input;
        private final OutputStream output;
        private final RaceStreamDecoder decoder = new RaceStreamDecoder();
        private final ArrayDeque<RaceFrame> received = new ArrayDeque<>();

        Session(InputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        RaceFrame exchange(byte[] request, FrameMatcher matcher)
                throws IOException, TimeoutException {
            deadline.check();
            output.write(request);
            output.flush();
            long responseDeadline = SystemClock.elapsedRealtime() + RESPONSE_TIMEOUT_MILLIS;
            while (SystemClock.elapsedRealtime() < responseDeadline) {
                deadline.check();
                RaceFrame matched = removeFirstMatching(matcher);
                if (matched != null) return matched;

                int available = input.available();
                if (available <= 0) {
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Diagnostic interrupted", error);
                    }
                    continue;
                }

                byte[] chunk = new byte[Math.min(available, 1024)];
                int count = input.read(chunk);
                if (count < 0) throw new IOException("RFCOMM stream ended");
                if (count == 0) continue;
                byte[] exact = count == chunk.length ? chunk : java.util.Arrays.copyOf(chunk, count);
                received.addAll(decoder.feed(exact));
                while (received.size() > 100) received.removeFirst();
            }
            throw new TimeoutException("AZ100 read response timed out");
        }

        private RaceFrame removeFirstMatching(FrameMatcher matcher) {
            for (java.util.Iterator<RaceFrame> iterator = received.iterator(); iterator.hasNext(); ) {
                RaceFrame frame = iterator.next();
                if (!matcher.matches(frame)) continue;
                iterator.remove();
                return frame;
            }
            return null;
        }
    }
}
