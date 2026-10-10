package com.misaka.posture.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** 以单调时钟毫秒记录识别结果，并按需生成不可变快照。 */
public final class Tracker {
  public static final long STALE_MILLIS = 2000L;

  private final LongSupplier elapsedRealtimeMillis;
  private final long clockOriginMillis;
  private final long startedUtcMillis;
  private final String mode;
  private final List<Segment> completed = new ArrayList<>();
  private String currentKey = "unknown";
  private String lastKnownKey;
  private long currentStartMillis;
  private long lastDataMillis = -1L;
  private long lastEventMillis;
  private long validFrames;
  private int changes;

  public Tracker(LongSupplier elapsedRealtimeMillis, long startedUtcMillis, String mode) {
    if (elapsedRealtimeMillis == null)
      throw new IllegalArgumentException("elapsedRealtimeMillis 不能为空");
    this.elapsedRealtimeMillis = elapsedRealtimeMillis;
    this.clockOriginMillis = elapsedRealtimeMillis.getAsLong();
    this.startedUtcMillis = startedUtcMillis;
    this.mode = mode == null ? "live" : mode;
  }

  /** 以绝对单调时钟到达时刻接收一条已解析的姿态。 */
  public synchronized void accept(String key, long absoluteArrivalMillis) {
    int index = Catalog.indexOf(key);
    if (index < 0) throw new IllegalArgumentException("未知姿态名称");
    long at = clampToSession(absoluteArrivalMillis - clockOriginMillis);
    if (at < lastEventMillis) return;
    String normalizedKey = Catalog.KEYS[index];
    if (!"unknown".equals(currentKey) && at > lastDataMillis + STALE_MILLIS) {
      closeAt(lastDataMillis + STALE_MILLIS);
      currentKey = "unknown";
    }
    if (!currentKey.equals(normalizedKey)) {
      closeAt(at);
      currentKey = normalizedKey;
    }
    if (lastKnownKey != null && !lastKnownKey.equals(normalizedKey)) changes++;
    lastKnownKey = normalizedKey;
    lastEventMillis = at;
    lastDataMillis = at;
    validFrames++;
  }

  /** 结束当前有效区间；重复调用不会产生额外时长。 */
  public synchronized void invalidate() {
    long at = Math.max(lastEventMillis, nowMillis());
    if (!"unknown".equals(currentKey) && at > lastDataMillis + STALE_MILLIS) {
      closeAt(lastDataMillis + STALE_MILLIS);
      currentKey = "unknown";
    }
    if (!"unknown".equals(currentKey)) {
      closeAt(at);
      currentKey = "unknown";
    }
    lastEventMillis = at;
  }

  public synchronized List<Segment> segments() {
    return buildSegments(nowMillis());
  }

  /** 快照计算当前时长，但不会提交或改变计时状态。 */
  public synchronized Snapshot snapshot() {
    long now = nowMillis();
    boolean stale = !"unknown".equals(currentKey) && now >= lastDataMillis + STALE_MILLIS;
    String snapshotCurrent = stale ? "unknown" : currentKey;
    long since = stale ? lastDataMillis + STALE_MILLIS : currentStartMillis;
    List<Segment> allSegments = buildSegments(now);
    Map<String, MutableRow> totals = new LinkedHashMap<>();
    for (int i = 0; i < Catalog.KEYS.length; i++) {
      totals.put(Catalog.KEYS[i], new MutableRow(Catalog.KEYS[i], Catalog.NAMES[i]));
    }
    long observed = 0L;
    long unknown = 0L;
    for (Segment segment : allSegments) {
      long duration = segment.endMillis - segment.startMillis;
      if ("unknown".equals(segment.key)) {
        unknown += duration;
      } else {
        MutableRow row = totals.get(segment.key);
        row.millis += duration;
        row.longestMillis = Math.max(row.longestMillis, duration);
        row.visits++;
        observed += duration;
      }
    }
    List<Row> rows = new ArrayList<>(totals.size());
    for (MutableRow row : totals.values())
      rows.add(new Row(row.key, row.name, row.millis, row.longestMillis, row.visits));
    return new Snapshot(
        startedUtcMillis,
        mode,
        snapshotCurrent,
        now,
        observed,
        unknown,
        since,
        validFrames,
        changes,
        rows,
        allSegments);
  }

  private long nowMillis() {
    return Math.max(
        lastEventMillis, Math.max(0L, elapsedRealtimeMillis.getAsLong() - clockOriginMillis));
  }

  private long clampToSession(long value) {
    long now = nowMillis();
    return Math.max(0L, Math.min(now, value));
  }

  private void closeAt(long end) {
    if (end > currentStartMillis) completed.add(new Segment(currentKey, currentStartMillis, end));
    currentStartMillis = end;
  }

  private List<Segment> buildSegments(long at) {
    long now = Math.max(lastEventMillis, at);
    List<Segment> result = new ArrayList<>(completed);
    long boundary =
        "unknown".equals(currentKey) ? now : Math.min(now, lastDataMillis + STALE_MILLIS);
    if (boundary > currentStartMillis)
      result.add(new Segment(currentKey, currentStartMillis, boundary));
    if (boundary < now) result.add(new Segment("unknown", boundary, now));
    return Collections.unmodifiableList(result);
  }

  private static final class MutableRow {
    final String key;
    final String name;
    long millis;
    long longestMillis;
    int visits;

    MutableRow(String key, String name) {
      this.key = key;
      this.name = name;
    }
  }

  public static final class Snapshot {
    public final long startedUtcMillis;
    public final String mode;
    public final String currentKey;
    public final long elapsedMillis;
    public final long observedMillis;
    public final long unknownMillis;
    public final long currentSinceMillis;
    public final long validFrames;
    public final int changes;
    public final List<Row> rows;
    public final List<Segment> segments;

    private Snapshot(
        long startedUtcMillis,
        String mode,
        String currentKey,
        long elapsedMillis,
        long observedMillis,
        long unknownMillis,
        long currentSinceMillis,
        long validFrames,
        int changes,
        List<Row> rows,
        List<Segment> segments) {
      this.startedUtcMillis = startedUtcMillis;
      this.mode = mode;
      this.currentKey = currentKey;
      this.elapsedMillis = elapsedMillis;
      this.observedMillis = observedMillis;
      this.unknownMillis = unknownMillis;
      this.currentSinceMillis = currentSinceMillis;
      this.validFrames = validFrames;
      this.changes = changes;
      this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
      this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    }
  }

  public static final class Row {
    public final String key;
    public final String name;
    public final long millis;
    public final long longestMillis;
    public final int visits;

    private Row(String key, String name, long millis, long longestMillis, int visits) {
      this.key = key;
      this.name = name;
      this.millis = millis;
      this.longestMillis = longestMillis;
      this.visits = visits;
    }
  }

  public static final class Segment {
    public final String key;
    public final long startMillis;
    public final long endMillis;

    private Segment(String key, long startMillis, long endMillis) {
      this.key = key;
      this.startMillis = startMillis;
      this.endMillis = endMillis;
    }
  }
}
