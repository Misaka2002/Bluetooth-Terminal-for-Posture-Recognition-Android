package com.misaka.posture;

import static org.junit.Assert.*;

import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.ScrollView;
import java.io.*;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowSystemClock;

/** 使用 Android 原生图形运行时生成演示截图，不依赖蓝牙或实机。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PreviewTest {
  @Test
  public void renderDemoScreen() throws Exception {
    ServiceController<PostureService> serviceController =
        Robolectric.buildService(PostureService.class).create();
    PostureService service = serviceController.get();
    service.startDemo();
    java.lang.reflect.Method tick = PostureService.class.getDeclaredMethod("tick");
    tick.setAccessible(true);
    for (int i = 0; i < 16; i++) {
      ShadowSystemClock.advanceBy(Duration.ofMillis(500));
      tick.invoke(service);
    }
    Context context = RuntimeEnvironment.getApplication();
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .setComponentNameAndServiceForBindService(
            new ComponentName(context, PostureService.class), service.onBind(new Intent()));
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      ViewGroup content = controller.get().findViewById(android.R.id.content);
      content.measure(
          View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
      content.layout(0, 0, 720, 1600);
      ScrollView scroll = (ScrollView) content.getChildAt(0);
      View root = scroll.getChildAt(0);
      assertTrue(root.getHeight() > 0);
      assertEquals("demo", service.state().snapshot.mode);
      Bitmap image =
          Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(image);
      canvas.drawColor(0xfff5f6fc);
      root.draw(canvas);
      File output = new File("build/previews/android-demo.png");
      output.getParentFile().mkdirs();
      try (OutputStream stream = new FileOutputStream(output)) {
        assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
      }
    } finally {
      serviceController.destroy();
    }
  }
}
