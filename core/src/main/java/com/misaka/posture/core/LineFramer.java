package com.misaka.posture.core;

import java.util.ArrayList;
import java.util.List;

/** 将串口文本分成 LF 结尾的完整行。 */
public final class LineFramer {
  private static final int MAX_LINE_LENGTH = 1024;
  private final StringBuilder pending = new StringBuilder();
  private boolean discarding;
  private long droppedLines;

  public List<String> feed(String chunk) {
    List<String> lines = new ArrayList<>();
    if (chunk == null) return lines;
    for (int i = 0; i < chunk.length(); i++) {
      char c = chunk.charAt(i);
      if (c == '\n') {
        if (!discarding) {
          int length = pending.length();
          if (length > 0 && pending.charAt(length - 1) == '\r') pending.setLength(length - 1);
          lines.add(pending.toString());
        }
        pending.setLength(0);
        discarding = false;
      } else if (!discarding) {
        if (pending.length() >= MAX_LINE_LENGTH) {
          pending.setLength(0);
          discarding = true;
          droppedLines++;
        } else {
          pending.append(c);
        }
      }
    }
    return lines;
  }

  public void clear() {
    pending.setLength(0);
    discarding = false;
  }

  public long droppedLines() {
    return droppedLines;
  }
}
