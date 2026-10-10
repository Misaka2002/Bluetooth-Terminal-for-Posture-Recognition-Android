# 签名与发行指南

[English](signing.en.md) · [返回 README](../README.md)

## 长期签名密钥是什么

安卓安装 APK 时会检查它的数字签名，安装更新时还会核对新旧版本的签名身份。对于这个通过 GitHub 分发 APK 的项目，最直接的做法是：第一次正式发行时创建自己的密钥，以后的更新继续使用它，同时保持应用包名并递增版本号。

密钥通常保存在一个 `.jks` 文件中，也叫 keystore。文件包含私钥和证书：私钥用于签名，必须保密；证书包含公钥和发布者信息，会随 APK 一起分发。签名用于验证发布身份与文件完整性，不会加密用户的姿态记录。

你需要长期保存密钥文件、密钥别名（alias）和密码。密钥本身不需要购买，也不需要向 Google 注册。对于自行保管密钥、直接分发 APK 的情况，丢失私钥后通常无法再给已有安装提供正常覆盖更新。

这次审核 APK 使用的是构建工具自动生成的调试密钥。它适合开发测试，不适合作为长期公开发行的身份。

## 用 Android Studio 创建

建议第一次使用图形界面创建，密码由你自己输入。

1. 在 Android Studio 中打开本项目，选择 **Build → Generate Signed Bundle / APK**。
2. 选择 **APK**，点击 **Next**，选择 `app` 模块。
3. 在 **Key store path** 旁点击 **Create new**。
4. 将文件保存到源码目录之外，例如 `D:\AndroidSigningKeys\posture-terminal-release.jks`。请先创建对应文件夹。
5. 设置 keystore 密码。别名可以填写 `posture-release`；密钥密码与 keystore 密码使用同一个，便于兼容 Android Studio 的签名流程。有效期建议设为 **30 年**，官方建议至少 25 年。
6. 填写证书信息。这些信息会包含在 APK 的证书中，请使用合适的发布者信息，不填写无关的敏感个人资料。
7. 点击 **OK** 完成创建。若只想创建密钥，可退出后续签名向导；若要生成 APK，则继续选择 **release** 构建并完成签名。

创建后，把密钥文件做一份独立的安全备份，例如加密保存到另一块离线存储设备。密码保存在密码管理器中，记录别名及对应项目。不要把文件、密码或密码截图放进 GitHub，也不需要把密码发给协作者。

项目的 `.gitignore` 已排除常见密钥文件以及 `keystore.properties`、`signing.properties`，但最稳妥的做法仍然是将密钥放在源码目录之外。

## 不用 Android Studio 也能创建

如果使用当前电脑的 Zulu JDK 21，可在 PowerShell 中运行以下命令。路径只是示例，请确认目录和文件名适合自己；不要覆盖已经使用过的密钥。

```powershell
New-Item -ItemType Directory -Path 'D:\AndroidSigningKeys' -Force

& 'D:\Zulu\Zulu21\bin\keytool.exe' -genkeypair `
    -keystore 'D:\AndroidSigningKeys\posture-terminal-release.jks' `
    -storetype JKS `
    -alias posture-release `
    -keyalg RSA `
    -keysize 3072 `
    -validity 10950
```

`10950` 是有效天数，约 30 年。命令会交互式询问密码和证书信息；输入密码时不显示字符是正常现象。最后询问密钥密码时，可以按回车使用同一个密码。不要将密码追加到命令中，以免留在命令历史里。

查看证书与 SHA-256 指纹可以使用：

```powershell
& 'D:\Zulu\Zulu21\bin\keytool.exe' -list -v `
    -keystore 'D:\AndroidSigningKeys\posture-terminal-release.jks' `
    -alias posture-release
```

这一步查看证书信息，不导出私钥。若在其他电脑操作，将 `keytool.exe` 路径替换为该电脑 JDK 的位置即可。命令行创建密钥后，也可以在 Android Studio 签名向导中选择已有 keystore 来生成正式 APK。

## 本项目正式发行时要注意什么

当前应用包名为 `com.misaka.posture`，`versionCode` 为 `1`，`versionName` 为 `0.1.0`，配置位于 `app/build.gradle`。后续正式更新应保持包名和签名身份，并递增 `versionCode`。仅修改显示用的 `versionName` 不够。

目前项目没有把正式密钥或密码写入 Gradle。Android Studio 的签名向导可以在本机选择密钥完成发行；直接运行 `:app:assembleRelease` 不会自动获得你的正式签名，未签名产物不能直接作为可安装发行版上传。

现在手机上的调试版与未来正式版使用不同签名，通常无法直接覆盖安装。请先在应用中导出重要会话，再卸载调试版，安装正式版。卸载会清除应用私有记录；当前应用没有从 ZIP 导回历史会话的功能，导出文件用于留存和外部查看。以后使用同一正式密钥签名的更新就可以正常覆盖安装。

如果将来发布到 Google Play，再了解 Play App Signing 与上传密钥的区别。本次 GitHub APK 发行不需要先配置 Google Play。

## 参考资料

- [Android 官方：签署应用](https://developer.android.com/studio/publish/app-signing)
- [Oracle JDK 17：keytool 命令](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)
