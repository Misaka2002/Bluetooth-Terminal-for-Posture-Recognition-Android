package com.misaka.posture;

import android.content.Context;
import android.util.AtomicFile;
import com.misaka.posture.core.Report;
import com.misaka.posture.core.Tracker;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

final class SessionStore {
  final File directory;
  private final BufferedWriter log;

  SessionStore(Context context, String mode) throws IOException {
    String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
    directory =
        new File(
            context.getFilesDir(),
            "sessions/" + stamp + "_" + mode + "_" + UUID.randomUUID().toString().substring(0, 8));
    if (!directory.mkdirs()) throw new IOException("无法创建会话目录");
    log =
        new BufferedWriter(
            new OutputStreamWriter(
                new FileOutputStream(new File(directory, "received.log"), true),
                StandardCharsets.UTF_8));
  }

  void append(long utcMillis, String line) throws IOException {
    log.write(
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT)
                .format(new Date(utcMillis))
            + " "
            + line);
    log.newLine();
  }

  void flush() throws IOException {
    log.flush();
  }

  void save(Tracker.Snapshot snapshot) throws IOException {
    flush();
    for (Map.Entry<String, String> entry : Report.render(snapshot).entrySet()) {
      AtomicFile file = new AtomicFile(new File(directory, entry.getKey()));
      FileOutputStream output = null;
      try {
        output = file.startWrite();
        if (entry.getKey().endsWith(".csv"))
          output.write(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
        output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        file.finishWrite(output);
      } catch (IOException error) {
        if (output != null) file.failWrite(output);
        throw error;
      }
    }
  }

  void close() throws IOException {
    log.close();
  }

  static File[] history(Context context) {
    File[] dirs = new File(context.getFilesDir(), "sessions").listFiles(File::isDirectory);
    if (dirs == null) return new File[0];
    Arrays.sort(dirs, (a, b) -> b.getName().compareTo(a.getName()));
    return dirs;
  }

  static File zip(Context context, File directory) throws IOException {
    File exports = new File(context.getCacheDir(), "exports");
    if (!exports.isDirectory() && !exports.mkdirs()) throw new IOException("无法创建导出目录");
    File target =
        new File(
            exports,
            directory.getName() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".zip");
    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(target))) {
      for (String name :
          new String[] {"summary.csv", "timeline.csv", "session.json", "received.log"}) {
        File file = new File(directory, name);
        if (!file.isFile()) throw new IOException("会话文件不完整：" + name);
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream in = new FileInputStream(file)) {
          byte[] bytes = new byte[8192];
          int count;
          while ((count = in.read(bytes)) != -1) zip.write(bytes, 0, count);
        }
        zip.closeEntry();
      }
    } catch (IOException error) {
      target.delete();
      throw error;
    }
    return target;
  }
}
