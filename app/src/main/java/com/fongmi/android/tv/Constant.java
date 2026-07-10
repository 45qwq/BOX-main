package com.fongmi.android.tv;

import java.util.concurrent.TimeUnit;

public class Constant {

    public static final long INTERVAL_SEEK = TimeUnit.SECONDS.toMillis(10);
    public static final long INTERVAL_HIDE = TimeUnit.SECONDS.toMillis(5);
    public static final long INTERVAL_TRAFFIC = TimeUnit.SECONDS.toMillis(1);
    public static final long TIMEOUT_VOD = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_XML = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_PLAY = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_SYNC = TimeUnit.SECONDS.toMillis(2);
    public static final long TIMEOUT_DANMAKU = TimeUnit.SECONDS.toMillis(30);
    public static final long TIMEOUT_PARSE_DEF = TimeUnit.SECONDS.toMillis(15);
    public static final long TIMEOUT_PARSE_WEB = TimeUnit.SECONDS.toMillis(15);
    public static final long HISTORY_TIME = TimeUnit.DAYS.toMillis(60);
    public static final long OPED_LIMIT = TimeUnit.MINUTES.toMillis(5);
    public static final int THREAD_POOL = 10;

    // 缓存清理
    public static final long CACHE_THRESHOLD = 200L * 1024 * 1024; // 200MB
    public static final long CACHE_CLEAN_INTERVAL = 30 * 60 * 1000L; // 30 分钟
    public static final int BACKUP_KEEP_COUNT = 7; // 保留备份数
    public static final long AUTO_UPDATE_DELAY = 2000L; // 自动检查更新延迟

    // 播放器
    public static final int ANIME4K_WIDTH_THRESHOLD = 1280; // 超分启用的视频宽度阈值
    public static final float PLAYER_SPEED_MIN = 0.25f;
    public static final float PLAYER_SPEED_MAX = 5.0f;
    public static final int PLAYER_RETRY_MAX = 2; // 播放器错误重试次数上限

    // 默认配置
    public static final int DEFAULT_WALL = 6; // 默认壁纸索引
}
