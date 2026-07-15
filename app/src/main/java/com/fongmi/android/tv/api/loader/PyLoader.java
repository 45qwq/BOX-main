package com.fongmi.android.tv.api.loader;
import com.github.catvod.utils.Logger;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private String recent;

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
    }

    public void clear() {
        for (Spider spider : spiders.values()) App.execute(spider::destroy);
        spiders.clear();
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext) {
        try {
            if (spiders.containsKey(key)) return spiders.get(key);
            // Python 解析（chaquo）已移除，.py 站点暂返回空实现
            Spider spider = new SpiderNull();
            spiders.put(key, spider);
            return spider;
        } catch (Throwable e) {
            Logger.e("Error", e);
            return new SpiderNull();
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            if (!params.containsKey("siteKey")) return spiders.get(recent).proxyLocal(params);
            String siteKey = params.get("siteKey");
            Spider spider = spiders.get(siteKey);
            if (spider == null) spider = VodConfig.get().getSite(siteKey).spider();
            return spider.proxyLocal(params);
        } catch (Throwable e) {
            Logger.e("Error", e);
            return null;
        }
    }
}
