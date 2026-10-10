package com.misaka.posture.core;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** 从同一份快照生成汇总、时间线和会话 JSON。 */
public final class Report {
  private Report() {}

  public static Map<String, String> render(Tracker.Snapshot snapshot) {
    if (snapshot == null) throw new IllegalArgumentException("snapshot 不能为空");
    Map<String, String> result = new LinkedHashMap<>();
    result.put("summary.csv", summary(snapshot));
    result.put("timeline.csv", timeline(snapshot));
    result.put("session.json", session(snapshot));
    return result;
  }

  private static String summary(Tracker.Snapshot s) {
    StringBuilder out = new StringBuilder("会话启动UTC,模式,姿态代码,姿态名称,累计秒,占有效时长百分比,连续最长秒,出现段数,会话总秒\r\n");
    String stamp = utc(s.startedUtcMillis);
    for (Tracker.Row row : s.rows) {
      double percent = s.observedMillis == 0L ? 0.0 : row.millis * 100.0 / s.observedMillis;
      out.append(csv(stamp))
          .append(',')
          .append(csv(s.mode))
          .append(',')
          .append(row.key)
          .append(',')
          .append(csv(row.name))
          .append(',')
          .append(seconds(row.millis))
          .append(',')
          .append(number(percent))
          .append(',')
          .append(seconds(row.longestMillis))
          .append(',')
          .append(row.visits)
          .append(',')
          .append(seconds(s.elapsedMillis))
          .append("\r\n");
    }
    out.append(csv(stamp))
        .append(',')
        .append(csv(s.mode))
        .append(",unknown,\"未识别 / 无数据\",")
        .append(seconds(s.unknownMillis))
        .append(",,0,0,")
        .append(seconds(s.elapsedMillis))
        .append("\r\n");
    return out.toString();
  }

  private static String timeline(Tracker.Snapshot s) {
    StringBuilder out = new StringBuilder("模式,姿态代码,姿态名称,开始秒,结束秒,持续秒,开始UTC,结束UTC\r\n");
    for (Tracker.Segment segment : s.segments) {
      long duration = segment.endMillis - segment.startMillis;
      out.append(csv(s.mode))
          .append(',')
          .append(segment.key)
          .append(',')
          .append(csv(Catalog.nameOf(segment.key)))
          .append(',')
          .append(seconds(segment.startMillis))
          .append(',')
          .append(seconds(segment.endMillis))
          .append(',')
          .append(seconds(duration))
          .append(',')
          .append(csv(utc(s.startedUtcMillis + segment.startMillis)))
          .append(',')
          .append(csv(utc(s.startedUtcMillis + segment.endMillis)))
          .append("\r\n");
    }
    return out.toString();
  }

  private static String session(Tracker.Snapshot s) {
    return "{\r\n  \"started_utc\": "
        + json(utc(s.startedUtcMillis))
        + ",\r\n  \"mode\": "
        + json(s.mode)
        + ",\r\n  \"elapsed_seconds\": "
        + seconds(s.elapsedMillis)
        + ",\r\n  \"observed_seconds\": "
        + seconds(s.observedMillis)
        + ",\r\n  \"unknown_seconds\": "
        + seconds(s.unknownMillis)
        + ",\r\n  \"valid_frames\": "
        + s.validFrames
        + ",\r\n  \"posture_changes\": "
        + s.changes
        + ",\r\n"
        + "  \"stale_seconds\": 2.0,\r\n"
        + "  \"time_basis\": \"terminal_monotonic_receipt_time\"\r\n"
        + "}\r\n";
  }

  private static String seconds(long millis) {
    return number(millis / 1000.0);
  }

  private static String number(double value) {
    return String.format(java.util.Locale.ROOT, "%.6f", value);
  }

  private static String utc(long millis) {
    return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis));
  }

  private static String csv(String value) {
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  private static String json(String value) {
    StringBuilder out = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '"' || c == '\\') out.append('\\').append(c);
      else if (c < 0x20) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
      else out.append(c);
    }
    return out.append('"').toString();
  }
}
