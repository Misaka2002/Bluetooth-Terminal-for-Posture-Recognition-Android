package com.misaka.posture;

import static org.junit.Assert.*;

import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.*;
import android.widget.TextView;
import com.misaka.posture.core.Tracker;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
@LooperMode(LooperMode.Mode.PAUSED)
public class AppTest {
  @Test
  public void disconnectKeepsSessionAvailableForActivityRecreation() {
    ServiceController<PostureService> controller =
        Robolectric.buildService(PostureService.class).create().startCommand(0, 1);
    PostureService service = controller.get();
    try {
      long started = service.state().snapshot.startedUtcMillis;
      service.disconnect();
      assertFalse(Shadows.shadowOf(service).isStoppedBySelf());
      assertEquals(started, service.state().snapshot.startedUtcMillis);
      assertEquals("unknown", service.state().snapshot.currentKey);
    } finally {
      controller.destroy();
    }
  }

  @Test
  public void phoneLayoutKeepsWidthWhenLogIsToggledAndButtonsFit() {
    ServiceController<PostureService> serviceController =
        Robolectric.buildService(PostureService.class).create();
    android.content.Context context = RuntimeEnvironment.getApplication();
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .setComponentNameAndServiceForBindService(
            new android.content.ComponentName(context, PostureService.class),
            serviceController.get().onBind(new android.content.Intent()));
    try (ActivityController<MainActivity> controller =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity activity = controller.get();
      View content = activity.findViewById(android.R.id.content);
      int width = 360 * (int) activity.getResources().getDisplayMetrics().density;
      measure(content, width, 800);
      TextView toggle = find(content, "显示原始终端");
      assertNotNull(toggle);
      int before = toggle.getWidth();
      toggle.performClick();
      measure(content, width, 800);
      assertEquals(before, toggle.getWidth());
      assertEquals("隐藏原始终端", toggle.getText().toString());
      toggle.performClick();
      measure(content, width, 800);
      assertEquals(before, toggle.getWidth());
      assertNotNull(find(content, "原始终端"));
      checkButtons(content, activity.getResources().getDisplayMetrics().density);
    } finally {
      serviceController.destroy();
    }
  }

  @Test
  public void sessionRotationSeparatesDemoAndPreservesPreviousRecords() {
    ServiceController<PostureService> controller =
        Robolectric.buildService(PostureService.class).create();
    PostureService service = controller.get();
    try {
      File[] before = SessionStore.history(service);
      assertEquals("live", service.state().snapshot.mode);
      service.startDemo();
      assertEquals("demo", service.state().snapshot.mode);
      assertFalse(service.state().connected);
      service.stopDemo();
      assertEquals("live", service.state().snapshot.mode);
      assertEquals(before.length + 2, SessionStore.history(service).length);
      for (File dir : SessionStore.history(service))
        assertTrue(new File(dir, "session.json").isFile());
    } finally {
      controller.destroy();
    }
  }

  @Test
  public void savedZipContainsConsistentReportsAndBomAndProviderIsReadOnly() throws Exception {
    android.content.Context context = RuntimeEnvironment.getApplication();
    AtomicLong clock = new AtomicLong();
    Tracker tracker = new Tracker(clock::get, 1700000000000L, "live");
    tracker.accept("upright", 0);
    clock.set(500);
    SessionStore store = new SessionStore(context, "live");
    store.append(1700000000000L, "POSTURE=upright");
    store.save(tracker.snapshot());
    store.close();
    File zip = SessionStore.zip(context, store.directory);
    try (ZipFile archive = new ZipFile(zip)) {
      assertEquals(4, archive.size());
      InputStream csv = archive.getInputStream(archive.getEntry("summary.csv"));
      assertEquals(0xef, csv.read());
      assertEquals(0xbb, csv.read());
      assertEquals(0xbf, csv.read());
      csv.close();
      assertNotNull(archive.getEntry("timeline.csv"));
      assertNotNull(archive.getEntry("received.log"));
    }
    ExportProvider provider = Robolectric.buildContentProvider(ExportProvider.class).create().get();
    Uri uri = Uri.parse("content://" + context.getPackageName() + ".exports/" + zip.getName());
    try (ParcelFileDescriptor handle = provider.openFile(uri, "r")) {
      assertNotNull(handle);
    }
    try {
      provider.openFile(uri, "w");
      fail("写入不应允许");
    } catch (FileNotFoundException expected) {
    }
    try {
      provider.openFile(
          Uri.parse("content://" + context.getPackageName() + ".exports/%2e%2e%2fsecret.zip"), "r");
      fail("不应允许路径穿越");
    } catch (FileNotFoundException expected) {
    }
  }

  private static void measure(View view, int width, int height) {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
  }

  private static TextView find(View view, String text) {
    if (view instanceof TextView && text.equals(((TextView) view).getText().toString()))
      return (TextView) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        TextView found = find(group.getChildAt(i), text);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static void checkButtons(View view, float density) {
    if (view instanceof TextView && view.isClickable() && view.getVisibility() == View.VISIBLE) {
      assertTrue("触摸区域不足：" + ((TextView) view).getText(), view.getHeight() >= 48 * density);
      assertTrue(view.getWidth() > 0);
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (group.getChildAt(i).getVisibility() == View.VISIBLE)
          checkButtons(group.getChildAt(i), density);
    }
  }
}
