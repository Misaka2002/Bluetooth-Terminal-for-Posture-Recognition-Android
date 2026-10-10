# 姿态素材

`app/src/main/assets/posture-atlas.png` 复用 Windows 姿态观察台中由 Codex image_gen 生成的透明 PNG。图集为 1536 × 1024，4 列、2 行，嵌入 APK 后由 `PoseView` 裁切显示。

左右方向以人物自身为准。按行从 0 开始的图格对应关系：

| 类别 | 图格 |
| --- | ---: |
| upright | 0 |
| forward_lean | 1 |
| backward_lean | 2 |
| left_lean | 4 |
| right_lean | 3 |
| forward_hunch | 5 |
| left_hunch | 6 |
| right_hunch | 7 |

`docs/images/android-demo.png` 是在 Robolectric 的 Android 原生图形运行时渲染的演示截图，不是手机或蓝牙实测截图。
