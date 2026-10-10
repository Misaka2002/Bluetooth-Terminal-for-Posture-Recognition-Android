package com.misaka.posture;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** 只允许被分享的单个 ZIP 以只读方式访问，不暴露会话目录。 */
public final class ExportProvider extends ContentProvider {
  @Override
  public boolean onCreate() {
    return true;
  }

  private File resolve(Uri uri) throws FileNotFoundException {
    if (!"content".equals(uri.getScheme())
        || !uri.getAuthority().equals(getContext().getPackageName() + ".exports")
        || uri.getPathSegments().size() != 1) throw new FileNotFoundException("无效导出路径");
    String name = uri.getLastPathSegment();
    if (name == null || !name.matches("[A-Za-z0-9_.-]+\\.zip"))
      throw new FileNotFoundException("无效文件名");
    File file = new File(new File(getContext().getCacheDir(), "exports"), name);
    if (!file.isFile()) throw new FileNotFoundException("导出文件不存在");
    return file;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("只允许读取");
    return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
    try {
      File file = resolve(uri);
      String[] columns =
          projection == null
              ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
              : projection;
      MatrixCursor cursor = new MatrixCursor(columns);
      Object[] values = new Object[columns.length];
      for (int i = 0; i < columns.length; i++)
        values[i] =
            OpenableColumns.DISPLAY_NAME.equals(columns[i])
                ? file.getName()
                : OpenableColumns.SIZE.equals(columns[i]) ? file.length() : null;
      cursor.addRow(values);
      return cursor;
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  @Override
  public String getType(Uri uri) {
    return "application/zip";
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String selection, String[] args) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] args) {
    throw new UnsupportedOperationException();
  }
}
