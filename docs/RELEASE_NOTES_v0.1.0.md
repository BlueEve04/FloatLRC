# 浮言 FloatLRC v0.1.0

**发布日期：2026-11-03**

首个公开框架版本。浮言通过 SuperLyric 接收系统级歌词，并以悬浮窗显示在任意应用之上；界面使用 MIUIX 构建。

## 下载

- `FloatLRC-v0.1.0.apk`（约 8.0 MB）
- SHA-256：`ef1664b40eb66919f769f13ef7163b14668244780f08c4c52c8c64f5db6f4f46`

## 亮点

- SuperLyricApi 4.0 接收端，兼容 3.x legacy 服务端
- `TYPE_APPLICATION_OVERLAY` 悬浮歌词：当前行、翻译、逐字/逐行填色
- 本地进度插值，解决歌词快照不连续导致的填色卡顿
- 拖动移动、位置持久化、锁定后触摸穿透、点击回到主界面
- Android 14+ `specialUse` 前台服务与常驻通知
- MIUIX 设置界面：开关、滑块、主题、权限引导、连接状态
- Debug 构建内置演示歌词，不依赖 SuperLyric 即可预览

## 安装要求

1. Android 8.0（API 26）及以上。
2. 安装并激活 SuperLyric 系统服务（LSPosed）。
3. 首次启动授予悬浮窗权限与通知权限。

## 兼容性

- 已在 FiiO JM21（Android 13）上使用 Poweramp + SuperLyric 3.x legacy 服务验证。
- 3.x 服务端通常不提供逐字时间，此时为整行填色。
- 4.0 服务端的逐字路径已实现，但尚未在 4.0 服务端上实机验证时间语义。

## 已知问题

- 3.x 不提供播放状态，暂停后本地插值仍会前进，直到下一帧拉回。
- 暂不支持多行歌词滚动、通知栏播放控制、开机自启。
- MIUIX 仍处于实验阶段，后续升级可能需要适配 API 变化。

## 校验

```text
ef1664b40eb66919f769f13ef7163b14668244780f08c4c52c8c64f5db6f4f46  FloatLRC-v0.1.0.apk
```

完整变更见仓库提交历史；问题反馈请前往 [Issues](https://github.com/BlueEve04/FloatLRC/issues)。
