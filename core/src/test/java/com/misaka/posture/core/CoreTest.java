package com.misaka.posture.core;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class CoreTest {
  @Test
  public void parsesEightClassesAndRejectsAmbiguousOrInvalidLines() {
    assertEquals(8, Catalog.KEYS.length);
    for (int i = 0; i < Catalog.KEYS.length; i++) {
      assertEquals(Catalog.KEYS[i], Parser.parse("pOsTuRe=" + Catalog.KEYS[i].toUpperCase()));
      assertEquals(Catalog.KEYS[i], Parser.parse("LABEL=" + Catalog.KEYS[i]));
    }
    assertEquals("upright", Parser.parse("upright"));
    assertNull(Parser.parse("ID=4"));
    assertNull(Parser.parse("POSTURE=upright LABEL=left_lean"));
    assertNull(Parser.parse("POSTURE=unknown"));
    assertNull(Parser.parse("xPOSTURE=upright"));
    assertNull(Parser.parse("POSTURE=upright-suffix"));
  }

  @Test
  public void framesCrLfFragmentsAndDropsWholeOverlongLine() {
    LineFramer framer = new LineFramer();
    assertTrue(framer.feed("first\r").isEmpty());
    List<String> completed = framer.feed("\nsecond\n");
    assertEquals(2, completed.size());
    assertEquals("first", completed.get(0));
    assertEquals("second", completed.get(1));
    assertEquals(1024, framer.feed(repeat('y', 1024) + "\n").get(0).length());
  }

  @Test
  public void overlongLineIsDiscardedUntilLfAndClearResetsPending() {
    LineFramer framer = new LineFramer();
    assertTrue(framer.feed(repeat('x', 1025) + "\nkept\n").contains("kept"));
    assertEquals(1L, framer.droppedLines());
    framer.feed("partial");
    framer.clear();
    assertEquals("next", framer.feed("next\n").get(0));
    assertEquals(1L, framer.droppedLines());
  }

  @Test
  public void trackerMergesSwitchesTimesOutAndRecoversWithoutSnapshotMutation() {
    AtomicLong clock = new AtomicLong(10000L);
    Tracker tracker = new Tracker(clock::get, 1_800_000_000_000L, "live");
    tracker.accept("upright", 10000L);
    clock.set(11000L);
    tracker.accept("upright", 11000L);
    clock.set(12000L);
    Tracker.Snapshot before = tracker.snapshot();
    assertEquals(2000L, before.observedMillis);
    assertEquals(0L, before.unknownMillis);
    assertEquals(1, before.rows.get(0).visits);
    clock.set(14001L);
    Tracker.Snapshot stale = tracker.snapshot();
    assertEquals("unknown", stale.currentKey);
    assertEquals(3000L, stale.observedMillis);
    assertEquals(1001L, stale.unknownMillis);
    assertEquals(2000L, before.observedMillis);
    tracker.accept("left_lean", 14001L);
    Tracker.Snapshot recovered = tracker.snapshot();
    assertEquals(1, recovered.changes);
    assertEquals(1L, recovered.validFrames - before.validFrames);
    assertEquals(3000L, recovered.observedMillis);
    assertEquals(1001L, recovered.unknownMillis);
    assertEquals(recovered.elapsedMillis, recovered.observedMillis + recovered.unknownMillis);
  }

  @Test
  public void invalidationEndsIntervalAndLateEventsAreIgnored() {
    AtomicLong clock = new AtomicLong(500L);
    Tracker tracker = new Tracker(clock::get, 0L, "test");
    tracker.accept("upright", 500L);
    clock.set(900L);
    tracker.accept("forward_lean", 800L);
    long frames = tracker.snapshot().validFrames;
    tracker.accept("left_lean", 700L);
    assertEquals(frames, tracker.snapshot().validFrames);
    tracker.invalidate();
    Tracker.Snapshot s = tracker.snapshot();
    assertEquals("unknown", s.currentKey);
    assertEquals(400L, s.observedMillis);
    assertEquals(0L, s.unknownMillis);
  }

  @Test
  public void delayedReceiptCanCorrectBoundaryProjectedByEarlierSnapshot() {
    AtomicLong clock = new AtomicLong(10000L);
    Tracker tracker = new Tracker(clock::get, 0L, "test");
    tracker.accept("upright", 10000L);
    clock.set(11000L);
    tracker.accept("upright", 11000L);

    clock.set(14000L);
    Tracker.Snapshot projected = tracker.snapshot();
    assertEquals(3000L, projected.observedMillis);
    assertEquals(1000L, projected.unknownMillis);
    assertEquals("unknown", projected.currentKey);

    // 该完整行在 2500 ms 到达，虽晚于上一条数据但早于快照时刻，仍可校正边界。
    tracker.accept("forward_lean", 12500L);
    Tracker.Snapshot corrected = tracker.snapshot();
    assertEquals(4000L, corrected.observedMillis);
    assertEquals(0L, corrected.unknownMillis);
    assertEquals(2, corrected.segments.size());
    assertEquals("upright", corrected.segments.get(0).key);
    assertEquals(2500L, corrected.segments.get(0).endMillis);
    assertEquals("forward_lean", corrected.segments.get(1).key);
    assertEquals(2500L, corrected.segments.get(1).startMillis);
    // 先前发出的快照保持不变，迟到事件只影响后续快照。
    assertEquals(3000L, projected.observedMillis);
    assertEquals(1000L, projected.unknownMillis);
  }

  @Test
  public void sameClassRecoveryAfterTimeoutKeepsUnknownGapAndExactBoundaryIsContinuous() {
    AtomicLong clock = new AtomicLong(0L);
    Tracker tracker = new Tracker(clock::get, 0L, "test");
    tracker.accept("upright", 0L);
    clock.set(1000L);
    tracker.accept("upright", 1000L);
    clock.set(3501L);
    tracker.accept("upright", 3501L);
    clock.set(3700L);

    Tracker.Snapshot recovered = tracker.snapshot();
    assertEquals(3, recovered.segments.size());
    assertEquals("upright", recovered.segments.get(0).key);
    assertEquals(3000L, recovered.segments.get(0).endMillis);
    assertEquals("unknown", recovered.segments.get(1).key);
    assertEquals(3000L, recovered.segments.get(1).startMillis);
    assertEquals(3501L, recovered.segments.get(1).endMillis);
    assertEquals("upright", recovered.segments.get(2).key);
    assertEquals(3501L, recovered.segments.get(2).startMillis);
    assertEquals(501L, recovered.unknownMillis);
    assertEquals(recovered.elapsedMillis, recovered.observedMillis + recovered.unknownMillis);

    AtomicLong boundaryClock = new AtomicLong(0L);
    Tracker boundary = new Tracker(boundaryClock::get, 0L, "test");
    boundary.accept("upright", 0L);
    boundaryClock.set(2000L);
    Tracker.Snapshot atDeadline = boundary.snapshot();
    assertEquals("unknown", atDeadline.currentKey);
    assertEquals(2000L, atDeadline.observedMillis);
    assertEquals(0L, atDeadline.unknownMillis);
    // 快照在 >= 2 秒时标为 unknown；恰好 2 秒到达的同类帧按无间隙连续处理。
    boundary.accept("upright", 2000L);
    Tracker.Snapshot exactArrival = boundary.snapshot();
    assertEquals("upright", exactArrival.currentKey);
    assertEquals(1, exactArrival.segments.size());
    assertEquals(2000L, exactArrival.observedMillis);
    assertEquals(0L, exactArrival.unknownMillis);
  }

  @Test
  public void randomizedTimelineAlwaysConservesIntegerMilliseconds() {
    Random random = new Random(57L);
    AtomicLong clock = new AtomicLong(1_000_000L);
    Tracker tracker = new Tracker(clock::get, 1_700_000_000_000L, "random");
    for (int i = 0; i < 500; i++) {
      long step = random.nextInt(5000);
      clock.addAndGet(step);
      if (i % 11 == 0) tracker.invalidate();
      tracker.accept(Catalog.KEYS[random.nextInt(Catalog.KEYS.length)], clock.get());
      Tracker.Snapshot snapshot = tracker.snapshot();
      assertEquals(snapshot.elapsedMillis, snapshot.observedMillis + snapshot.unknownMillis);
      long timeline = 0L;
      for (Tracker.Segment segment : snapshot.segments)
        timeline += segment.endMillis - segment.startMillis;
      assertEquals(snapshot.elapsedMillis, timeline);
    }
  }

  @Test
  public void reportUsesOneSnapshotForSummaryTimelineAndSession() {
    AtomicLong clock = new AtomicLong(0L);
    Tracker tracker = new Tracker(clock::get, 1_700_000_000_000L, "live");
    tracker.accept("upright", 0L);
    clock.set(1000L);
    tracker.accept("forward_lean", 1000L);
    clock.set(1500L);
    Map<String, String> files = Report.render(tracker.snapshot());
    assertEquals(3, files.size());
    assertTrue(files.containsKey("summary.csv"));
    assertTrue(files.get("summary.csv").contains("正坐"));
    assertTrue(files.get("timeline.csv").contains("2023-11-14T22:13:20Z"));
    assertTrue(files.get("session.json").contains("\"observed_seconds\": 1.500000"));
  }

  private static String repeat(char c, int count) {
    StringBuilder out = new StringBuilder(count);
    for (int i = 0; i < count; i++) out.append(c);
    return out.toString();
  }
}
