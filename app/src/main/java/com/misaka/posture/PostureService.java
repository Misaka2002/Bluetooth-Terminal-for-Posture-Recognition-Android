package com.misaka.posture;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import com.misaka.posture.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class PostureService extends Service {
  private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
  private final Object lock = new Object();
  private final ArrayDeque<String> lines = new ArrayDeque<>();
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
  private final ExecutorService workers = Executors.newCachedThreadPool();
  private Tracker tracker;
  private SessionStore store;
  private BluetoothSocket socket;
  private PowerManager.WakeLock wake;
  private String status = "未连接", deviceName = "未选择设备", error = "";
  private boolean demo, connecting, connected, destroyed, foreground;
  private long generation, sessionSerial, sessionCutoff, connectAt, savedAt, wakeAt;
  private int demoTick;

  public final class LocalBinder extends Binder {
    public PostureService service() {
      return PostureService.this;
    }
  }

  public interface ExportCallback {
    void complete(File file, String error);
  }

  public static final class State {
    public Tracker.Snapshot snapshot;
    public String status, device, error, log;
    public boolean demo, connecting, connected;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    wake =
        ((PowerManager) getSystemService(POWER_SERVICE))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":receive");
    wake.setReferenceCounted(false);
    NotificationChannel channel =
        new NotificationChannel("recording", "姿态记录", NotificationManager.IMPORTANCE_LOW);
    getSystemService(NotificationManager.class).createNotificationChannel(channel);
    synchronized (lock) {
      newSession(false);
    }
    timer.scheduleWithFixedDelay(this::tick, 500, 500, TimeUnit.MILLISECONDS);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return new LocalBinder();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "connect".equals(intent.getAction())) {
      synchronized (lock) {
        try {
          Notification notification = notification();
          if (Build.VERSION.SDK_INT >= 29)
            startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
          else startForeground(7, notification);
          foreground = true;
        } catch (RuntimeException e) {
          error = "无法开始后台接收：" + e.getMessage();
          return START_NOT_STICKY;
        }
      }
      connect(intent.getStringExtra("address"), intent.getStringExtra("name"));
    }
    if (intent != null && "stop".equals(intent.getAction())) {
      disconnect();
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  private Notification notification() {
    Intent open = new Intent(this, MainActivity.class);
    PendingIntent content =
        PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, PostureService.class).setAction("stop"),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this, "recording")
        .setSmallIcon(R.drawable.ic_posture)
        .setContentTitle("姿态观察台正在记录")
        .setContentText(deviceName + " · " + status)
        .setContentIntent(content)
        .setOngoing(true)
        .addAction(new Notification.Action.Builder(null, "断开", stop).build())
        .build();
  }

  private void newSession(boolean isDemo) {
    save();
    if (store != null)
      try {
        store.close();
      } catch (IOException e) {
        error = "关闭日志失败：" + e.getMessage();
      }
    store = null;
    demo = isDemo;
    demoTick = 0;
    lines.clear();
    sessionSerial++;
    sessionCutoff = SystemClock.elapsedRealtime();
    tracker =
        new Tracker(
            SystemClock::elapsedRealtime, System.currentTimeMillis(), demo ? "demo" : "live");
    try {
      store = new SessionStore(this, demo ? "demo" : "live");
      store.save(tracker.snapshot());
    } catch (IOException e) {
      error = "创建会话失败：" + e.getMessage();
    }
    savedAt = SystemClock.elapsedRealtime();
  }

  private void save() {
    if (tracker != null && store != null)
      try {
        store.save(tracker.snapshot());
      } catch (IOException e) {
        error = "保存失败：" + e.getMessage();
      }
  }

  private void append(String text) {
    lines.addLast(text);
    while (lines.size() > 200) lines.removeFirst();
    if (store != null)
      try {
        store.append(System.currentTimeMillis(), text);
      } catch (IOException e) {
        error = "接收日志写入失败：" + e.getMessage();
      }
  }

  private void closeSocket() {
    generation++;
    connecting = false;
    connected = false;
    if (socket != null)
      try {
        socket.close();
      } catch (IOException ignored) {
      }
    socket = null;
    if (wake.isHeld()) wake.release();
  }

  private void stopForegroundRecording() {
    if (foreground) {
      stopForeground(STOP_FOREGROUND_REMOVE);
      foreground = false;
    }
  }

  public void disconnect() {
    synchronized (lock) {
      closeSocket();
      tracker.invalidate();
      status = "已断开";
      save();
      stopForegroundRecording();
      // 断开只结束接收；保留已启动的服务，界面重建时继续同一统计会话。
    }
  }

  public void startDemo() {
    synchronized (lock) {
      closeSocket();
      stopForegroundRecording();
      newSession(true);
      status = "演示中";
      deviceName = "模拟数据";
    }
  }

  public void stopDemo() {
    synchronized (lock) {
      closeSocket();
      newSession(false);
      status = "未连接";
      deviceName = "未选择设备";
    }
  }

  public void reset() {
    synchronized (lock) {
      newSession(demo);
    }
  }

  public void clearLog() {
    synchronized (lock) {
      lines.clear();
    }
  }

  public State state() {
    synchronized (lock) {
      State s = new State();
      s.snapshot = tracker.snapshot();
      s.status = status;
      s.device = deviceName;
      s.error = error;
      s.demo = demo;
      s.connecting = connecting;
      s.connected = connected;
      s.log = String.join("\n", lines);
      return s;
    }
  }

  private void connect(String address, String name) {
    final long epoch;
    synchronized (lock) {
      closeSocket();
      tracker.invalidate();
      if (demo) newSession(false);
      deviceName = name == null ? "蓝牙设备" : name;
      status = "正在连接";
      error = "";
      connecting = true;
      connectAt = SystemClock.elapsedRealtime();
      epoch = generation;
      wake.acquire(10 * 60 * 1000L);
      wakeAt = connectAt;
    }
    workers.execute(
        () -> {
          BluetoothSocket active = null;
          try {
            BluetoothManager manager = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) throw new IOException("蓝牙未开启或不受支持");
            BluetoothDevice device = adapter.getRemoteDevice(address);
            active = device.createRfcommSocketToServiceRecord(SPP);
            synchronized (lock) {
              if (destroyed || generation != epoch) {
                active.close();
                return;
              }
              socket = active;
            }
            active.connect();
            synchronized (lock) {
              if (destroyed || generation != epoch) return;
              connecting = false;
              connected = true;
              status = "已连接";
              append("[连接] " + deviceName);
              getSystemService(NotificationManager.class).notify(7, notification());
            }
            // Reader 保留跨字节边界的 UTF-8 状态，行缓冲独属于当前连接。
            LineFramer framer = new LineFramer();
            long readSession;
            synchronized (lock) {
              readSession = sessionSerial;
            }
            try (Reader reader =
                new InputStreamReader(active.getInputStream(), StandardCharsets.UTF_8)) {
              char[] buffer = new char[512];
              int count;
              while ((count = reader.read(buffer)) != -1) {
                long arrived = SystemClock.elapsedRealtime();
                synchronized (lock) {
                  if (destroyed || generation != epoch) return;
                  String chunk = new String(buffer, 0, count);
                  // 新会话丢弃旧半行的尾部，避免两段会话拼成一条记录。
                  if (readSession != sessionSerial) {
                    framer.clear();
                    int newline = chunk.indexOf('\n');
                    if (newline < 0) continue;
                    chunk = chunk.substring(newline + 1);
                    readSession = sessionSerial;
                  }
                  if (arrived < sessionCutoff) continue;
                  long dropped = framer.droppedLines();
                  List<String> complete = framer.feed(chunk);
                  if (framer.droppedLines() > dropped) append("[提示] 已丢弃超过 1024 字符的数据行");
                  for (String line : complete) {
                    append(line);
                    String key = Parser.parse(line);
                    if (key != null) tracker.accept(key, arrived);
                  }
                }
              }
              throw new IOException("设备已断开连接");
            }
          } catch (SecurityException e) {
            connectionFailed(epoch, "蓝牙权限已被撤回，请重新允许附近的设备权限");
          } catch (IOException | RuntimeException e) {
            connectionFailed(epoch, e.getMessage() == null ? "蓝牙连接失败" : e.getMessage());
          } finally {
            if (active != null)
              try {
                active.close();
              } catch (IOException ignored) {
              }
          }
        });
  }

  private void connectionFailed(long epoch, String message) {
    synchronized (lock) {
      if (!destroyed && generation == epoch) {
        closeSocket();
        tracker.invalidate();
        status = "连接中断";
        error = message;
        append("[断开] " + error);
        save();
        stopForegroundRecording();
      }
    }
  }

  private void tick() {
    synchronized (lock) {
      if (destroyed) return;
      long now = SystemClock.elapsedRealtime();
      if (connecting && now - connectAt > 15000) {
        closeSocket();
        tracker.invalidate();
        status = "连接超时";
        error = "请确认设备已配对、已供电且未被其他终端占用";
        save();
        stopForegroundRecording();
      }
      if (connected && now - wakeAt >= 60000) {
        wake.acquire(10 * 60 * 1000L);
        wakeAt = now;
      }
      if (demo) {
        String key = Catalog.KEYS[(demoTick++ / 10) % Catalog.KEYS.length];
        tracker.accept(key, now);
        append("[演示] POSTURE=" + key);
      }
      if (store != null)
        try {
          store.flush();
        } catch (IOException e) {
          error = "刷新日志失败：" + e.getMessage();
        }
      if (now - savedAt >= 30000) {
        save();
        savedAt = now;
      }
    }
  }

  public void export(File historical, ExportCallback callback) {
    workers.execute(
        () -> {
          File result = null;
          String failure = null;
          File directory = historical;
          synchronized (lock) {
            try {
              if (directory == null || (store != null && directory.equals(store.directory))) {
                if (store == null) throw new IOException("当前会话未能创建");
                store.save(tracker.snapshot());
                directory = store.directory;
              }
              result = SessionStore.zip(this, directory);
            } catch (IOException e) {
              failure = "导出失败：" + e.getMessage();
            }
          }
          File output = result;
          String message = failure;
          new Handler(Looper.getMainLooper()).post(() -> callback.complete(output, message));
        });
  }

  public void saveNow() {
    workers.execute(
        () -> {
          synchronized (lock) {
            save();
          }
        });
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    synchronized (lock) {
      save();
      if (!connected && !connecting) stopSelf();
    }
  }

  @Override
  public void onDestroy() {
    synchronized (lock) {
      destroyed = true;
      closeSocket();
      tracker.invalidate();
      save();
      if (store != null)
        try {
          store.close();
        } catch (IOException ignored) {
        }
      stopForegroundRecording();
    }
    timer.shutdownNow();
    workers.shutdownNow();
    super.onDestroy();
  }
}
