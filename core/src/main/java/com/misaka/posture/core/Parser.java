package com.misaka.posture.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析完整协议行中的姿态代码。 */
public final class Parser {
  private static final Pattern FIELD =
      Pattern.compile(
          "(?:^|[,;\\s])(?:POSTURE|LABEL)\\s*=\\s*([a-zA-Z_]+)(?=$|[,;\\s])",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private Parser() {}

  /** 返回已知姿态代码；格式无效、字段重复或仅含 ID 时返回 null。 */
  public static String parse(String line) {
    if (line == null || line.trim().isEmpty()) return null;
    Matcher matcher = FIELD.matcher(line);
    if (!matcher.find()) {
      String bare = line.trim().toLowerCase(Locale.ROOT);
      return Catalog.indexOf(bare) >= 0 ? bare : null;
    }
    String value = matcher.group(1).toLowerCase(Locale.ROOT);
    if (matcher.find() || Catalog.indexOf(value) < 0) return null;
    return value;
  }
}
