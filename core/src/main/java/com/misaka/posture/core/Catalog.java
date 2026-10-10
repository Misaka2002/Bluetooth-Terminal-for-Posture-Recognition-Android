package com.misaka.posture.core;

/** 姿态代码与显示名称的固定映射。 */
public final class Catalog {
  public static final String[] KEYS = {
    "upright", "forward_lean", "backward_lean", "left_lean",
    "right_lean", "forward_hunch", "left_hunch", "right_hunch"
  };
  public static final String[] NAMES = {"正坐", "前倾", "后仰", "左倾", "右倾", "前弯含胸", "左弯含胸", "右弯含胸"};

  private Catalog() {}

  public static String nameOf(String key) {
    int index = indexOf(key);
    return index < 0 ? "未识别 / 无数据" : NAMES[index];
  }

  public static int indexOf(String key) {
    if (key == null) return -1;
    for (int i = 0; i < KEYS.length; i++) {
      if (KEYS[i].equalsIgnoreCase(key)) return i;
    }
    return -1;
  }
}
