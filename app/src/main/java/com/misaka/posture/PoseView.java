package com.misaka.posture;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.misaka.posture.core.Catalog;
import java.io.IOException;

final class PoseView extends View {
  private Bitmap atlas;
  private String key = "unknown";
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final int[] cells = {0, 1, 2, 4, 3, 5, 6, 7};
  private final Rect source = new Rect();
  private final RectF destination = new RectF();

  PoseView(Context context) {
    super(context);
    try (java.io.InputStream stream = context.getAssets().open("posture-atlas.png")) {
      atlas = BitmapFactory.decodeStream(stream);
    } catch (IOException ignored) {
    }
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    setContentDescription("当前无有效姿态数据");
  }

  void setPosture(String value) {
    if (key.equals(value)) return;
    key = value;
    setContentDescription(Catalog.indexOf(key) >= 0 ? "当前姿态：" + Catalog.nameOf(key) : "当前无有效姿态数据");
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int index = Catalog.indexOf(key);
    paint.setColor(Color.rgb(238, 240, 255));
    canvas.drawOval(
        getWidth() * .15f, getHeight() * .78f, getWidth() * .85f, getHeight() * .95f, paint);
    if (atlas == null || index < 0) {
      paint.setColor(Color.rgb(140, 149, 179));
      paint.setTextSize(16 * getResources().getDisplayMetrics().scaledDensity);
      paint.setTextAlign(Paint.Align.CENTER);
      canvas.drawText("等待姿态数据", getWidth() / 2f, getHeight() / 2f, paint);
      return;
    }
    int cell = cells[index], w = atlas.getWidth() / 4, h = atlas.getHeight() / 2;
    source.set(cell % 4 * w, cell / 4 * h, (cell % 4 + 1) * w, (cell / 4 + 1) * h);
    float scale = Math.min(getWidth() / (float) w, getHeight() / (float) h);
    float width = w * scale, height = h * scale;
    paint.setColor(Color.WHITE);
    destination.set(
        (getWidth() - width) / 2,
        (getHeight() - height) / 2,
        (getWidth() + width) / 2,
        (getHeight() + height) / 2);
    canvas.drawBitmap(atlas, source, destination, paint);
  }
}
