package com.misaka.posture;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.misaka.posture.core.*;
import java.io.*;
import java.util.*;

public final class MainActivity extends Activity {
  private static final int CONNECT_PERMISSION = 10, NOTIFICATION_PERMISSION = 11, SAVE_ZIP = 12;
  private static final int INK = 0xff202943,
      MUTED = 0xff737e9a,
      ACCENT = 0xff4f63eb,
      BG = 0xfff5f6fc;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private PostureService service;
  private boolean bound, showLog, exporting;
  private Spinner devices;
  private final List<BluetoothDevice> paired = new ArrayList<>();
  private TextView connection, stateTitle, stateNote, sessionNote, metrics, error, log;
  private TextView connect, demo, toggle, export, reset;
  private final TextView[] rowTime = new TextView[8], rowDetail = new TextView[8];
  private final ProgressBar[] rowProgress = new ProgressBar[8];
  private LinearLayout logCard;
  private PoseView pose;
  private File pendingExport;
  private final ServiceConnection binding =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
          service = ((PostureService.LocalBinder) binder).service();
          refresh();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          service = null;
        }
      };
  private final Runnable render =
      new Runnable() {
        @Override
        public void run() {
          refresh();
          handler.postDelayed(this, 500);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    if (saved != null) {
      showLog = saved.getBoolean("showLog");
      String name = saved.getString("export");
      if (name != null && name.matches("[A-Za-z0-9_.-]+\\.zip"))
        pendingExport = new File(getCacheDir(), "exports/" + name);
    }
    buildUi();
    startService(new Intent(this, PostureService.class));
    bound = bindService(new Intent(this, PostureService.class), binding, BIND_AUTO_CREATE);
  }

  @Override
  protected void onStart() {
    super.onStart();
    handler.post(render);
    loadDevices();
  }

  @Override
  protected void onStop() {
    handler.removeCallbacks(render);
    if (service != null) service.saveNow();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if (bound) unbindService(binding);
    service = null;
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putBoolean("showLog", showLog);
    if (pendingExport != null) out.putString("export", pendingExport.getName());
    super.onSaveInstanceState(out);
  }

  private int dp(float v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, int size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    t.setIncludeFontPadding(true);
    return t;
  }

  private GradientDrawable shape(int color, int border) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(20));
    if (border != 0) d.setStroke(dp(1), border);
    return d;
  }

  private LinearLayout vertical() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private LinearLayout card() {
    LinearLayout v = vertical();
    v.setPadding(dp(18), dp(18), dp(18), dp(18));
    v.setBackground(shape(Color.WHITE, 0xffe8ecf5));
    return v;
  }

  private void addCard(LinearLayout parent, View child) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.bottomMargin = dp(14);
    parent.addView(child, p);
  }

  private void space(LinearLayout parent, int height) {
    parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
  }

  private TextView button(String label, boolean primary, Runnable action) {
    TextView b = text(label, 14, primary ? Color.WHITE : ACCENT, true);
    b.setGravity(Gravity.CENTER);
    b.setMinHeight(dp(50));
    b.setPadding(dp(12), dp(12), dp(12), dp(12));
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x244f63eb),
            shape(primary ? ACCENT : 0xffeef0ff, primary ? 0 : 0xffdce1ff),
            null));
    b.setFocusable(true);
    b.setClickable(true);
    b.setOnClickListener(v -> action.run());
    b.setAccessibilityDelegate(
        new View.AccessibilityDelegate() {
          @Override
          public void onInitializeAccessibilityNodeInfo(
              View host, android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(Button.class.getName());
          }
        });
    return b;
  }

  private void actions(LinearLayout parent, View... buttons) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setBaselineAligned(false);
    for (int i = 0; i < buttons.length; i++) {
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
      if (i > 0) p.leftMargin = dp(8);
      row.addView(buttons[i], p);
    }
    parent.addView(row);
  }

  private void buildUi() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(BG);
    LinearLayout root = vertical();
    root.setPadding(dp(18), dp(18), dp(18), dp(24));
    scroll.addView(root);
    // 适配 Android 15 的边到边布局，系统栏与正文保持间距。
    scroll.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          view.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    root.addView(text("POSTURE / 姿态观察台", 12, ACCENT, true));
    space(root, 5);
    root.addView(text("每一种姿态，都有记录", 26, INK, true));
    space(root, 8);
    sessionNote = text("从本次会话开始计时", 13, MUTED, false);
    root.addView(sessionNote);
    space(root, 20);
    LinearLayout device = card();
    device.addView(text("连接设备", 18, INK, true));
    space(device, 8);
    connection = text("先在手机蓝牙设置中配对 HC-06", 14, MUTED, false);
    device.addView(connection);
    space(device, 8);
    devices = new Spinner(this);
    devices.setContentDescription("已配对的蓝牙设备");
    device.addView(devices, new LinearLayout.LayoutParams(-1, dp(52)));
    space(device, 12);
    connect = button("连接设备", true, this::connectClicked);
    actions(
        device,
        connect,
        button("蓝牙设置", false, () -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))));
    space(device, 10);
    TextView reload = button("刷新已配对设备", false, this::requestDevices);
    device.addView(reload);
    addCard(root, device);
    LinearLayout hero = card();
    stateTitle = text("未识别 / 无数据", 24, INK, true);
    hero.addView(stateTitle);
    space(hero, 5);
    stateNote = text("收到姿态数据后自动更新", 14, MUTED, false);
    hero.addView(stateNote);
    pose = new PoseView(this);
    hero.addView(pose, new LinearLayout.LayoutParams(-1, dp(220)));
    metrics = text("", 16, INK, true);
    hero.addView(metrics);
    space(hero, 10);
    hero.addView(text("按收到完整数据行的时刻计时；2 秒无有效数据后转为未识别。", 13, MUTED, false));
    addCard(root, hero);
    root.addView(text("姿态时长", 20, INK, true));
    space(root, 5);
    root.addView(text("占比以有效姿态总时长计算", 13, MUTED, false));
    space(root, 12);
    for (int pair = 0; pair < 4; pair++) {
      LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setBaselineAligned(false);
      for (int j = 0; j < 2; j++) {
        int i = pair * 2 + j;
        LinearLayout item = card();
        item.setPadding(dp(12), dp(14), dp(12), dp(14));
        item.addView(text(Catalog.NAMES[i], 16, INK, true));
        space(item, 8);
        rowTime[i] = text("00:00", 24, ACCENT, true);
        item.addView(rowTime[i]);
        space(item, 8);
        rowProgress[i] = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        rowProgress[i].setMax(1000);
        rowProgress[i].setProgressTintList(ColorStateList.valueOf(ACCENT));
        item.addView(rowProgress[i], new LinearLayout.LayoutParams(-1, dp(6)));
        space(item, 8);
        rowDetail[i] = text("最长 00:00\n0 段 · 0%", 12, MUTED, false);
        item.addView(rowDetail[i]);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        if (j > 0) p.leftMargin = dp(10);
        row.addView(item, p);
      }
      addCard(root, row);
    }
    LinearLayout tools = card();
    tools.addView(text("记录与工具", 18, INK, true));
    space(tools, 12);
    demo =
        button(
            "演示模式",
            false,
            () -> {
              if (service != null) {
                if (service.state().demo) service.stopDemo();
                else service.startDemo();
                refresh();
              }
            });
    reset =
        button(
            "新统计会话",
            false,
            () -> {
              if (service != null)
                new AlertDialog.Builder(this)
                    .setTitle("开始新的统计会话？")
                    .setMessage("当前记录会先保存；新会话从零开始计时。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton(
                        "开始",
                        (d, w) -> {
                          service.reset();
                          refresh();
                        })
                    .show();
            });
    actions(tools, demo, reset);
    space(tools, 10);
    export = button("导出记录", true, () -> exportSession(null));
    actions(tools, export, button("历史记录", false, this::history));
    space(tools, 10);
    toggle =
        button(
            showLog ? "隐藏原始终端" : "显示原始终端",
            false,
            () -> {
              showLog = !showLog;
              logCard.setVisibility(showLog ? View.VISIBLE : View.GONE);
              toggle.setText(showLog ? "隐藏原始终端" : "显示原始终端");
            });
    tools.addView(toggle);
    error = text("", 14, 0xffb5464b, false);
    space(tools, 8);
    tools.addView(error);
    addCard(root, tools);
    logCard = card();
    logCard.addView(text("原始终端", 18, INK, true));
    space(logCard, 8);
    log = text("", 12, MUTED, false);
    log.setTypeface(Typeface.MONOSPACE);
    log.setTextIsSelectable(true);
    logCard.addView(log);
    space(logCard, 10);
    logCard.addView(
        button(
            "清屏",
            false,
            () -> {
              if (service != null) {
                service.clearLog();
                refresh();
              }
            }));
    logCard.setVisibility(showLog ? View.VISIBLE : View.GONE);
    addCard(root, logCard);
    root.addView(text("记录保存在本机 · 演示与设备会话分开保存", 12, MUTED, false));
    setContentView(scroll);
  }

  private String duration(long millis) {
    long seconds = millis / 1000;
    return seconds >= 3600
        ? String.format(
            Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
  }

  private void refresh() {
    if (service == null) return;
    PostureService.State s = service.state();
    Tracker.Snapshot snap = s.snapshot;
    connection.setText(getString(R.string.connection_status, s.device, s.status));
    stateTitle.setText(Catalog.nameOf(snap.currentKey));
    pose.setPosture(snap.currentKey);
    stateNote.setText(
        getString(
            R.string.current_duration,
            s.demo ? "演示数据" : "设备数据",
            duration(snap.elapsedMillis - snap.currentSinceMillis)));
    sessionNote.setText(
        getString(R.string.session_duration, duration(snap.elapsedMillis), snap.validFrames));
    long upright = snap.rows.get(0).millis;
    metrics.setText(
        String.format(
            Locale.ROOT,
            "正坐 %.0f%%   ·   切换 %d 次\n未识别 %s",
            snap.observedMillis == 0 ? 0 : upright * 100.0 / snap.observedMillis,
            snap.changes,
            duration(snap.unknownMillis)));
    for (int i = 0; i < 8; i++) {
      Tracker.Row row = snap.rows.get(i);
      double percent = snap.observedMillis == 0 ? 0 : row.millis * 100.0 / snap.observedMillis;
      rowTime[i].setText(duration(row.millis));
      rowDetail[i].setText(
          String.format(
              Locale.ROOT,
              "最长 %s\n%d 段 · %.1f%%",
              duration(row.longestMillis),
              row.visits,
              percent));
      rowProgress[i].setProgress((int) (percent * 10));
    }
    connect.setText(s.connecting ? "取消连接" : s.connected ? "断开连接" : "连接设备");
    demo.setText(s.demo ? "退出演示" : "演示模式");
    error.setText(s.error);
    error.setVisibility(s.error.isEmpty() ? View.GONE : View.VISIBLE);
    if (showLog) log.setText(s.log);
  }

  private boolean permitted() {
    return Build.VERSION.SDK_INT < 31
        || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED;
  }

  private void requestDevices() {
    if (!permitted())
      requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, CONNECT_PERMISSION);
    else loadDevices();
  }

  private void loadDevices() {
    if (devices == null) return;
    String previous = null;
    int pos = devices.getSelectedItemPosition();
    if (pos >= 0 && pos < paired.size()) previous = paired.get(pos).getAddress();
    paired.clear();
    List<String> names = new ArrayList<>();
    if (permitted())
      try {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter != null && adapter.isEnabled()) {
          paired.addAll(adapter.getBondedDevices());
          paired.sort(
              Comparator.comparing(d -> d.getName() == null ? d.getAddress() : d.getName()));
          for (BluetoothDevice d : paired)
            names.add((d.getName() == null ? "蓝牙设备" : d.getName()) + " · " + d.getAddress());
        }
      } catch (SecurityException e) {
        paired.clear();
      }
    if (names.isEmpty()) names.add(permitted() ? "没有已配对设备，请先打开蓝牙并配对" : "点“刷新”允许访问已配对设备");
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    devices.setAdapter(adapter);
    if (previous != null)
      for (int i = 0; i < paired.size(); i++)
        if (previous.equals(paired.get(i).getAddress())) devices.setSelection(i);
  }

  private void connectClicked() {
    if (service == null) return;
    PostureService.State state = service.state();
    if (state.connected || state.connecting) {
      service.disconnect();
      refresh();
      return;
    }
    if (!permitted()) {
      requestDevices();
      return;
    }
    if (paired.isEmpty()) {
      toast("请先在蓝牙设置中配对设备，再刷新列表");
      return;
    }
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(
          new String[] {Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
      return;
    }
    startConnection();
  }

  private void startConnection() {
    int position = devices.getSelectedItemPosition();
    if (position < 0 || position >= paired.size()) return;
    try {
      BluetoothDevice device = paired.get(position);
      startForegroundService(
          new Intent(this, PostureService.class)
              .setAction("connect")
              .putExtra("address", device.getAddress())
              .putExtra("name", device.getName()));
    } catch (SecurityException e) {
      toast("蓝牙权限已被撤回，请重新允许附近的设备权限");
    } catch (RuntimeException e) {
      toast("无法连接：" + e.getMessage());
    }
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == CONNECT_PERMISSION) {
      loadDevices();
      if (!permitted()) toast("未获得蓝牙权限，仍可使用演示模式");
    }
    if (request == NOTIFICATION_PERMISSION) {
      if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)
        toast("通知未启用，可在系统设置中开启；后台记录仍可运行");
      startConnection();
    }
  }

  private void history() {
    File[] dirs = SessionStore.history(this);
    if (dirs.length == 0) {
      toast("暂无历史会话");
      return;
    }
    String[] names = new String[dirs.length];
    for (int i = 0; i < dirs.length; i++) names[i] = dirs[i].getName();
    new AlertDialog.Builder(this)
        .setTitle("选择要导出的会话")
        .setItems(names, (d, w) -> exportSession(dirs[w]))
        .setNegativeButton("取消", null)
        .show();
  }

  private void exportSession(File historical) {
    if (service == null || exporting) return;
    exporting = true;
    export.setEnabled(false);
    export.setText("正在准备…");
    service.export(
        historical,
        (file, failure) -> {
          exporting = false;
          if (isFinishing() || isDestroyed()) return;
          export.setEnabled(true);
          export.setText("导出记录");
          if (failure != null) {
            toast(failure);
            return;
          }
          pendingExport = file;
          new AlertDialog.Builder(this)
              .setTitle("导出完整会话")
              .setMessage("ZIP 包含汇总 CSV、时间线 CSV、会话信息和接收日志。")
              .setPositiveButton(
                  "保存 ZIP",
                  (d, w) -> {
                    Intent pick =
                        new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/zip")
                            .putExtra(Intent.EXTRA_TITLE, file.getName());
                    startActivityForResult(pick, SAVE_ZIP);
                  })
              .setNeutralButton(
                  "分享",
                  (d, w) -> {
                    Uri uri =
                        Uri.parse("content://" + getPackageName() + ".exports/" + file.getName());
                    Intent share =
                        new Intent(Intent.ACTION_SEND)
                            .setType("application/zip")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    share.setClipData(ClipData.newRawUri("会话记录", uri));
                    startActivity(Intent.createChooser(share, "分享姿态记录"));
                  })
              .setNegativeButton("取消", null)
              .show();
        });
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == SAVE_ZIP
        && result == RESULT_OK
        && data != null
        && data.getData() != null
        && pendingExport != null) {
      File source = pendingExport;
      Uri uri = data.getData();
      new Thread(
              () -> {
                try (InputStream in = new FileInputStream(source);
                    OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                  if (out == null) throw new IOException("无法打开目标文件");
                  byte[] buffer = new byte[8192];
                  int count;
                  while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                  handler.post(() -> toast("会话 ZIP 已保存"));
                } catch (IOException e) {
                  handler.post(() -> toast("保存失败：" + e.getMessage()));
                }
              },
              "export-copy")
          .start();
    }
  }

  private void toast(String value) {
    Toast.makeText(this, value, Toast.LENGTH_LONG).show();
  }
}
