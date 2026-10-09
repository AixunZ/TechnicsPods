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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.technics.pods.protocol.Az100ControlFrames;
import dev.technics.pods.protocol.Az100ControlResponses;
import dev.technics.pods.protocol.Az100ReadFrames;
import dev.technics.pods.protocol.Az100ReadResponses;
import dev.technics.pods.protocol.Az100StateSnapshot;
import dev.technics.pods.protocol.PanasonicCommand;
import dev.technics.pods.protocol.RaceFrame;
import dev.technics.pods.protocol.RaceStreamDecoder;

/** Exclusive, one-shot outside-control write with mandatory read-back and recovery. */
public final class Az100OutsideControlClient {
    private static final long CONNECT_TIMEOUT_MILLIS = 12_000L;
    private static final long RESPONSE_TIMEOUT_MILLIS = 2_500L;

    private final Context context;
    private final BluetoothDevice targetDevice;
    private final ControlDeadline deadline = new ControlDeadline(SystemClock::elapsedRealtime, 15_000L);

    public Az100OutsideControlClient(Context context, BluetoothDevice targetDevice) {
        this.context = context.getApplicationContext();
        this.targetDevice = java.util.Objects.requireNonNull(targetDevice);
    }

    @SuppressLint("MissingPermission")
    public Az100OutsideControlResult run(int requestedMode, Integer requestedNoiseLevel)
            throws ControlException {
        long started = SystemClock.elapsedRealtime();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> timeout = timer.schedule(this::cancel, 15_000L, TimeUnit.MILLISECONDS);
        BluetoothSocket socket = null;
        try {
            deadline.check();
            BluetoothManager manager = context.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) throw new ControlException("请先打开蓝牙");
            BluetoothDevice device = targetDevice;
            if (requestedMode != -1) validateMode(requestedMode);
            socket = device.createRfcommSocketToServiceRecord(Az100ReadOnlyClient.RFCOMM_UUID);
            deadline.attach(socket);
            connectWithTimeout(socket);
            deadline.check();
            Session session = new Session(socket.getInputStream(), socket.getOutputStream());
            Az100ReadResponses.requireSuccessfulModelResponse(session.exchange(
                    Az100ReadFrames.getModelId(),
                    frame -> isResponse(frame, PanasonicCommand.GET_MODEL_ID)
            ));
            int effectiveMode = requestedMode == -1 ? readOutsideControl(session).mode() : requestedMode;
            Az100OutsideControlResult result = Az100OutsideControlTransaction.run(
                    new Az100OutsideControlTransaction.Transport() {
                        @Override public Az100ControlResponses.OutsideControl read() throws Exception {
                            return readOutsideControl(session);
                        }
                        @Override public void write(int mode, int noise, int ambient) throws Exception {
                            Az100OutsideControlClient.write(session, mode, noise, ambient);
                        }
                    }, deadline, effectiveMode, requestedNoiseLevel);
            deadline.complete();
            return new Az100OutsideControlResult(SystemClock.elapsedRealtime() - started,
                    result.requestedMode(), result.requestedNoiseLevel(), result.ambientLevel(),
                    result.readBackMode(), result.readBackNoiseLevel(), result.changed());
        } catch (Exception error) {
            throw error instanceof ControlException ? (ControlException) error
                    : new ControlException(controlFailureMessage(error)
                            + "；状态未确认，请重新读取耳机状态");
        } finally {
            timeout.cancel(false);
            timer.shutdownNow();
            closeQuietly(socket);
        }
    }

    public void cancel() {
        deadline.cancel();
    }
    @SuppressLint("MissingPermission")
    private static BluetoothDevice findBondedAz100(Set<BluetoothDevice> bondedDevices)
            throws ControlException {
        BluetoothDevice match = null;
        for (BluetoothDevice candidate : bondedDevices) {
            if (!Az100DeviceMatcher.matchesName(candidate.getName())) continue;
            if (match != null) throw new ControlException("发现多个已配对的 EAH-AZ100，无法安全选择");
            match = candidate;
        }
        return match;
    }

    private static void validateMode(int mode) throws ControlException {
        if (mode < Az100StateSnapshot.OUTSIDE_CONTROL_OFF
                || mode > Az100StateSnapshot.OUTSIDE_CONTROL_AMBIENT) {
            throw new ControlException("无效的外音模式");
        }
    }

    private void connectWithTimeout(BluetoothSocket socket) throws IOException, ControlException {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> timeout = timer.schedule(
                () -> closeQuietly(socket),
                CONNECT_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS
        );
        try {
            socket.connect();
            if (!socket.isConnected()) throw new ControlException("RFCOMM 连接未建立");
        } finally {
            timeout.cancel(false);
            timer.shutdownNow();
        }
    }

    private static void write(
            Session session,
            int mode,
            int noiseLevel,
            int ambientLevel
    ) throws IOException, TimeoutException {
        RaceFrame acknowledgement = session.exchange(
                Az100ControlFrames.setOutsideControl(mode, noiseLevel, ambientLevel),
                frame -> isResponse(frame, PanasonicCommand.SET_OUTSIDE_CONTROL)
        );
        Az100ControlResponses.requireSuccessfulOutsideControlSet(acknowledgement);
    }

    private static Az100ControlResponses.OutsideControl readOutsideControl(Session session)
            throws IOException, TimeoutException {
        return Az100ControlResponses.outsideControl(session.exchange(
                Az100ReadFrames.getOutsideControl(),
                frame -> isResponse(frame, PanasonicCommand.GET_OUTSIDE_CONTROL)
        ));
    }

    private static String controlFailureMessage(Exception error) {
        if (error instanceof SecurityException) return "缺少附近设备/蓝牙连接权限";
        if (error instanceof IOException) return "连接或控制写入失败；请确认官方 App 已强制停止";
        if (error instanceof TimeoutException || error instanceof IllegalArgumentException) {
            return "控制回验超时或返回值无效；请确认官方 App 已强制停止";
        }
        return "外音控制失败；请确认官方 App 已强制停止";
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

    public static final class ControlException extends Exception {
        public ControlException(String safeMessage) {
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
                        throw new IOException("Outside control interrupted", error);
                    }
                    continue;
                }
                byte[] chunk = new byte[Math.min(available, 1024)];
                int count = input.read(chunk);
                if (count < 0) throw new IOException("RFCOMM stream ended");
                if (count == 0) continue;
                byte[] exact = count == chunk.length
                        ? chunk
                        : java.util.Arrays.copyOf(chunk, count);
                received.addAll(decoder.feed(exact));
                while (received.size() > 100) received.removeFirst();
            }
            throw new TimeoutException("AZ100 outside-control response timed out");
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
