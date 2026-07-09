<h1 align="center">XMBOX - Android 视频播放器</h1>

<div align="center">

![Platform](https://img.shields.io/badge/platform-Android-green.svg)
![MinSDK](https://img.shields.io/badge/minSDK-24-orange.svg)
![TargetSDK](https://img.shields.io/badge/targetSDK-36-blue.svg)
![License](https://img.shields.io/badge/license-GPL--3.0-orange.svg)

一个操作方便、界面简洁的 Android 视频播放器，需自行添源，支持手机和平板。

</div>

## 项目简介

XMBOX 是一个基于 Media3/ExoPlayer 的 Android 视频点播应用，支持多种视频源协议（Jar/JS/Py）、弹幕、投屏、下载、WebDAV 同步等功能。项目采用 Java + Kotlin 混合开发，面向手机和平板双平台。

## 功能特性

### 视频播放

- **Media3/ExoPlayer 内核** — 支持 HLS/DASH/SmoothStreaming/RTSP/RTMP 等流媒体协议
- **ffmpeg 软解扩展** — nextlib-media3ext 提供硬解失败自动降级软解能力
- **4K 播放优化** — AUTO 模式硬解优先，失败自动回退 ffmpeg 多线程软解；4K 视频使用 30-120s 大缓冲
- **Seek 秒开** — 关键帧跳转（PREVIOUS_SYNC）+ 减小播放缓冲（1.5s），跳转后快速起播
- **Anime4K 实时超分** — GPU GLSL shader 实时渲染（双边滤波降噪 + 边缘检测 + 自适应锐化），仅对 720p 以下视频自动启用，支持低/中/高三档强度
- **弹幕** — DanmakuView 弹幕显示，支持弹幕加载开关和样式调整
- **画中画** — 后台播放和 PiP 模式支持
- **字幕** — 外挂字幕加载，支持推送字幕
- **DRM** — Widevine DRM 支持

### 视频源

- **多协议源加载** — Jar（csp_）、JavaScript（.js）、Python（.py）三种爬虫协议
- **本地代理** — 内置 HTTP 代理服务，支持 Jar/JS/Py 代理请求分发
- **智能搜索** — 全局搜索、快速搜索、换源功能
- **解析** — 多解析接口支持，JSON 解析和混合解析
- **NewPipe Extractor** — 集成 NewPipe 提取器，支持 YouTube 等平台

### 下载管理

- **多协议下载** — M3U8 分片下载、HTTP/HTTPS 直链下载
- **独立网络栈** — 下载使用独立 OkHttpClient，绕过 catvod 自定义 DNS 和拦截器
- **签名 URL 探测** — 含 x-expires/x-signature 的链接用 Range: bytes=0-0 探测大小，避免消耗签名
- **分片完整性监控** — M3U8 失败分片超 20% 自动终止，防止合并后文件损坏
- **多集选择** — 支持单集/多集/全选下载，下载列表实时显示速度和状态
- **动态线程池** — 同时下载数量 1-5 可调，修改后即时生效
- **SAF 存储适配** — 支持系统文件夹选择器自定义下载路径，不可写时自动回退到应用专属目录

### 投屏

- **DLNA** — 基于 Cling 2.1.1，支持 DMC（控制器）和 DMR（接收器）
- **投屏控制** — 支持投屏播放控制

### 数据同步

- **WebDAV 同步** — 支持观看记录、收藏、设置云端同步
- **双模式** — 账号模式（WebDAV 账号密码）和同步码模式（无需账号）
- **自动同步** — AutoSyncManager 自动合并本地和远程数据

### 广告拦截

- **AI 广告检测** — AIAdDetector 自动学习并记录广告域名，持续优化拦截规则
- **广告拦截** — AdBlocker 基于特征识别过滤广告请求

### 其他功能

- **壁纸** — 自定义应用背景壁纸
- **定时关闭** — 定时器倒计时显示
- **生物识别** — 指纹/面容解锁
- **二维码** — ZXing 扫码和生成
- **崩溃报告** — CustomCrash 崩溃捕获和报告
- **检查更新** — GitHub Release 检查更新和安装

## 项目架构

### 模块结构

```
XMBOX/
├── app/                # 主应用模块
│   ├── src/main/       # 通用代码（Java + Kotlin）
│   ├── src/mobile/     # 手机版特定代码
│   └── src/tablet/     # 平板版特定代码
├── catvod/             # 视频点播核心（爬虫框架、代理、工具）
├── quickjs/            # JavaScript 引擎（QuickJS）
├── danmaku/            # 弹幕引擎
├── dlna-core/          # DLNA 核心协议
├── dlna-dmc/           # DLNA 投屏控制器
├── dlna-dmr/           # DLNA 投屏接收器
├── forcetech/          # ForceTech 视频提取
├── hook/               # Hook 功能模块
└── jianpian/           # 简片视频提取
```

### app 主要包结构

```
com.fongmi.android.tv/
├── api/                # API 层
│   ├── config/         # 配置管理（VodConfig, WallConfig）
│   ├── loader/         # 源加载器（Jar/JS/Py）
│   ├── AIAdDetector    # AI 广告检测
│   └── AdBlocker       # 广告拦截
├── bean/               # 数据模型（Vod, Site, Episode, History, Download 等）
├── cast/               # 投屏控制
├── db/                 # Room 数据库（AppDatabase, DAO, Migrations）
├── download/           # 下载管理器
├── player/             # 播放器
│   ├── effect/         # 视频特效（Anime4K 超分）
│   ├── exo/            # ExoPlayer 工具（ExoUtil, MediaSourceFactory, CacheManager）
│   ├── extractor/      # 视频提取器（Force, JianPian, Push, Strm, Youtube）
│   └── danmaku/        # 弹幕播放器
├── service/            # 服务（DownloadService, PlaybackService）
├── ui/
│   ├── activity/       # Activity（Home, Video, Download, History, Collect 等）
│   ├── adapter/        # 列表适配器
│   ├── custom/         # 自定义 View（CustomSwitch, CustomSeekView 等）
│   ├── dialog/         # 对话框（控制、设置、搜索等）
│   ├── fragment/       # Fragment（Episode, Setting, Type）
│   └── holder/         # ViewHolder
├── utils/              # 工具类（ThreadPools, FileUtil, WebDAVSyncManager 等）
├── App.kt              # 应用入口
├── Setting.java        # 全局设置
└── Constant.java       # 常量
```

### 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 | Java + Kotlin | 17 / 2.0.21 |
| 构建 | AGP + Gradle | 8.12.0 |
| 播放器 | Media3/ExoPlayer | 1.10.1 |
| 软解扩展 | nextlib-media3ext | 1.8.0-0.9.0 |
| 数据库 | Room | 2.8.0 |
| 依赖注入 | Hilt | 2.54 |
| 网络 | OkHttp | 4.12.0 |
| 图片加载 | Glide | 4.16.0 |
| 事件总线 | EventBus（编译期索引） | 3.3.1 |
| DLNA | Cling | 2.1.1 |
| 视频 | NewPipe Extractor | v0.24.8 |
| UI | Material Components + ViewBinding | 1.13.0 |
| 动画 | Lottie | 6.7.1 |
| 权限 | PermissionX | 1.7.1 |
| 二维码 | ZXing | 3.5.3 |
| SMB | smbj | 0.13.0 |
| WebDAV | Sardine | 0.9 |

### Flavor 差异

项目通过 `mode` 和 `abi` 两个维度划分 Flavor：

- **mode**: `mobile`（手机版）/ `tablet`（平板版）
- **abi**: `arm64_v8a` / `armeabi_v7a`

mobile 和 tablet 的主要差异在 `VideoActivity`（播放页面交互）、`SettingPlayerActivity`（播放器设置）和布局文件。两者共享 `src/main` 下的所有通用代码。

## 构建指南

### 环境要求

- Android Studio（最新版本）
- JDK 17
- Android SDK API 36
- Gradle 8.12.0

### 快速开始

1. **克隆项目**

```bash
git clone https://github.com/yourusername/XMBOX.git
cd XMBOX
```

2. **配置签名**（可选）

```bash
# 将签名文件放到 keystore/ 目录
# 或修改 app/build.gradle.kts 中的 signingConfigs
```

3. **构建项目**

```bash
# 构建 Debug 版本
./gradlew assembleMobileArm64_v8aDebug assembleTabletArm64_v8aDebug

# 构建 Release 版本
./gradlew assembleMobileArm64_v8aRelease assembleTabletArm64_v8aRelease

# 构建所有版本
./gradlew assembleRelease
```

4. **生成的 APK 位置**

```
app/build/outputs/apk/
├── mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
├── mobileArm64_v8a/release/app-mobile-arm64_v8a-release.apk
├── mobileArmeabi_v7a/debug/app-mobile-armeabi_v7a-debug.apk
├── mobileArmeabi_v7a/release/app-mobile-armeabi_v7a-release.apk
├── tabletArm64_v8a/debug/app-tablet-arm64_v8a-debug.apk
├── tabletArm64_v8a/release/app-tablet-arm64_v8a-release.apk
├── tabletArmeabi_v7a/debug/app-tablet-armeabi_v7a-debug.apk
└── tabletArmeabi_v7a/release/app-tablet-armeabi_v7a-release.apk
```

### 构建配置说明

- `compileSdk = 36`，`minSdk = 24`，`targetSdk = 36`
- Java 17 编译，Kotlin jvmTarget = 17
- 开启 ViewBinding、BuildConfig
- Release 开启代码混淆和资源压缩
- v1/v2/v3/v4 多重签名
- EventBus 编译期生成 `EventIndex`
- Room schema 导出到 `app/schemas/`

## 系统要求

- Android 7.0（API 24）及以上
- ARM64-V8A：推荐新设备使用，性能更优
- ARM V7A：兼容老设备

## 许可协议

本项目基于 [GPL-3.0](LICENSE.md) 协议开源。

## 免责声明

1. 本项目仅为技术性多媒体播放器外壳，自身不包含、不预装任何音视频资源内容
2. 用户通过本软件播放的内容均来源于用户自行配置的第三方来源
3. 本软件仅供学习和技术交流使用，不得用于商业用途
4. 使用本项目产生的一切后果由使用者自行承担
5. 请确保在当地法律法规允许的范围内使用本软件

## 致谢

- 基于 [FongMi/TV](https://github.com/FongMi/TV) 项目开发
- 感谢 [CatVodTVOfficial](https://github.com/CatVodTVOfficial) 提供的核心技术
- 感谢所有为项目做出贡献的开发者

---

<div align="center">

**如果这个项目对你有帮助，请给一个 Star**

</div>
