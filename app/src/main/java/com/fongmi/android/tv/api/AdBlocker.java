package com.fongmi.android.tv.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 广告拦截器 - 内置常用广告域名库
 * 使用预编译正则提高性能，避免误拦截
 */
public class AdBlocker {

    /** 赌博类广告域名 */
    private static final String[] GAMBLING_RULES = {
            // 澳门博彩广告
            ".*\\..*葡京.*",
            ".*\\..*皇冠.*",
            ".*\\..*金沙.*",
            ".*\\..*威尼斯人.*",
            ".*\\..*永利.*",
            ".*xpj\\d*\\..*",
            ".*xinpujing.*",
            // 具体博彩广告域名
            "wan\\.51img1\\.com",
            "iqiyi\\.hbuioo\\.com",
            "vip\\.ffzyad\\.com",
            "https\\.wshdsm\\.com",
    };

    /** 通用广告联盟域名 */
    private static final String[] GENERAL_RULES = {
            // Google 广告
            ".*\\.doubleclick\\.net",
            "googleads\\.g\\.doubleclick\\.net",
            "adservice\\.google\\.com",
            "pagead2\\.googlesyndication\\.com",
            ".*\\.googlesyndication\\.com",
            ".*\\.googletagmanager\\.com",
            // 百度广告
            "cpro\\.baidu\\.com",
            "pos\\.baidu\\.com",
            "cbjs\\.baidu\\.com",
            "hm\\.baidu\\.com",
            ".*\\.union\\.baidu\\.com",
            "tongji\\.baidu\\.com",
            "push\\.zhanzhang\\.baidu\\.com",
            // 阿里广告
            ".*\\.tanx\\.com",
            ".*\\.mmstat\\.com",
            "mclick\\.simba\\.taobao\\.com",
            // 腾讯广告
            "mi\\.gdt\\.qq\\.com",
            "adsmind\\.gdtimg\\.com",
            "pgdt\\.gtimg\\.cn",
            ".*\\.beacon\\.qq\\.com",
            // 其他
            "union\\.meituan\\.com",
            "analytics\\.163\\.com",
            "g\\.163\\.com",
            "analytics\\.126\\.net",
            ".*\\.irs01\\.com",
            ".*\\.irs01\\.net",
    };

    /** 视频平台广告域名 */
    private static final String[] VIDEO_RULES = {
            // 优酷广告
            ".*\\.atm\\.youku\\.com",
            "stat\\.youku\\.com",
            "ad\\.api\\.3g\\.youku\\.com",
            // 爱奇艺广告
            "cupid\\.iqiyi\\.com",
            ".*\\.cupid\\.iqiyi\\.com",
            ".*\\.data\\.video\\.iqiyi\\.com",
            "msg\\.71\\.am",
            // 腾讯视频广告
            "btrace\\.video\\.qq\\.com",
            "mtrace\\.video\\.qq\\.com",
            "ad\\.video\\.qq\\.com",
            "vv\\.video\\.qq\\.com",
            // 芒果TV广告
            "da\\.mgtv\\.com",
            "ad\\.hunantv\\.com",
            // 其他视频平台
            "ark\\.letv\\.com",
            "stat\\.letv\\.com",
    };

    /** 弹窗广告域名 */
    private static final String[] POPUP_RULES = {
            "mimg\\.0c1q0l\\.cn",
            "www\\.92424\\.cn",
            "k\\.jinxiuzhilv\\.com",
            "ppl\\.xunzhuo\\.com",
            "xc\\.hubeijieshikj\\.cn",
            "ssl\\.kdd\\.cc",
            "afp\\.csbew\\.com",
            "aoodoo\\.feng\\.com",
            ".*\\.popin\\.cc",
            ".*\\.supersonicads\\.com",
            "adshow\\.58\\.com",
            "cpc\\.cmbchina\\.com",
    };

    /** 恶意网站和钓鱼网站 */
    private static final String[] MALICIOUS_RULES = {
            ".*\\.571xz\\.com",
            ".*\\.haoyuemh\\.com",
            ".*\\.duomeng\\.cn",
            ".*\\.shuzilm\\.cn",
            ".*\\.madthumbs\\.com",
    };

    /** 统计跟踪域名 */
    private static final String[] TRACKING_RULES = {
            ".*\\.cnzz\\.com",
            ".*\\.umeng\\.com",
            ".*\\.umtrack\\.com",
            ".*\\.google-analytics\\.com",
            "ssl\\.google-analytics\\.com",
    };

    /** 现代广告 SDK 域名 */
    private static final String[] SDK_RULES = {
            ".*\\.admob\\.com",
            ".*\\.adcolony\\.com",
            ".*\\.applovin\\.com",
            ".*\\.chartboost\\.com",
            ".*\\.inmobi\\.com",
            ".*\\.mopub\\.com",
            ".*\\.unityads\\.unity3d\\.com",
            ".*\\.vungle\\.com",
            ".*\\.ironsrc\\.com",
            ".*\\.tapjoy\\.com",
            ".*\\.flurry\\.com",
            ".*\\.mintegral\\.com",
            ".*\\.moloco\\.com",
            ".*\\.pangle\\.io",
            ".*\\.adtop\\.cn",
            ".*\\.adview\\.cn",
            ".*\\.youmi\\.net",
            ".*\\.domob\\.cn",
            ".*\\.mediav\\.com",
            ".*\\.baidumobads\\.cn",
    };

    /** 合并所有规则 */
    private static final String[] ALL_RULES = merge(
            GAMBLING_RULES, GENERAL_RULES, VIDEO_RULES,
            POPUP_RULES, MALICIOUS_RULES, TRACKING_RULES, SDK_RULES
    );

    /** 预编译正则列表，避免每次请求都编译 */
    private static final List<Pattern> COMPILED_PATTERNS = compile(ALL_RULES);

    private static String[] merge(String[]... arrays) {
        int total = 0;
        for (String[] arr : arrays) total += arr.length;
        String[] result = new String[total];
        int offset = 0;
        for (String[] arr : arrays) {
            System.arraycopy(arr, 0, result, offset, arr.length);
            offset += arr.length;
        }
        return result;
    }

    private static List<Pattern> compile(String[] rules) {
        List<Pattern> patterns = new ArrayList<>(rules.length);
        for (String rule : rules) {
            patterns.add(Pattern.compile(rule, Pattern.CASE_INSENSITIVE));
        }
        return patterns;
    }

    /**
     * 检查是否应该拦截该域名
     *
     * @param host 要检查的域名
     * @return true=应该拦截, false=不拦截
     */
    public static boolean shouldBlock(String host) {
        if (host == null || host.isEmpty()) return false;
        String lowerHost = host.toLowerCase();
        for (Pattern pattern : COMPILED_PATTERNS) {
            if (pattern.matcher(lowerHost).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取所有内置广告域名规则
     */
    public static List<String> getAllAdHosts() {
        return Arrays.asList(ALL_RULES);
    }

    public static List<String> getGamblingAdHosts() {
        return Arrays.asList(GAMBLING_RULES);
    }

    public static List<String> getGeneralAdHosts() {
        return Arrays.asList(GENERAL_RULES);
    }

    public static List<String> getVideoAdHosts() {
        return Arrays.asList(VIDEO_RULES);
    }

    public static List<String> getPopupAdHosts() {
        return Arrays.asList(POPUP_RULES);
    }

    public static List<String> getMaliciousAdHosts() {
        return Arrays.asList(MALICIOUS_RULES);
    }

    public static List<String> getTrackingAdHosts() {
        return Arrays.asList(TRACKING_RULES);
    }

    public static List<String> getSdkAdHosts() {
        return Arrays.asList(SDK_RULES);
    }
}
