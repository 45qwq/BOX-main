package com.fongmi.android.tv.bean;

/**
 * 内置订阅源
 *
 * 应用首次安装时自动添加到数据库
 */
public class DefaultConfig {

    /**
     * 内置订阅源列表
     * 格式：URL, 名称
     */
    public static final String[][] SOURCES = {
        {"https://www.iyouhun.com/tv/dc", "游魂多仓"},
        {"https://www.iyouhun.com/tv/yh", "游魂多仓（备）"},
        {"http://fty.xxooo.cf/tv", "摸鱼儿"},
        {"http://我不是.摸鱼儿.top", "摸鱼儿.top"},
        {"http://www.饭太硬.net/tv", "饭太硬接口"},
        {"http://xhztv.top/4k.json", "小盒子4K"}
    };

    /**
     * 首次启动时插入内置订阅源
     * 仅当数据库中没有任何 type=0 的配置时执行
     */
    public static void initIfNeeded() {
        java.util.List<Config> existing = Config.getAll(0);
        if (!existing.isEmpty()) return;

        for (String[] source : SOURCES) {
            Config.create(0, source[0], source[1]);
        }
    }
}
