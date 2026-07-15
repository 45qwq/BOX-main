package com.fongmi.android.tv.api.config;
import com.github.catvod.utils.Logger;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.HttpUrl;
import okhttp3.Response;

public class VodConfig {

    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<Parse> parses;
    private List<String> flags;
    private List<String> ads;
    private Config config;
    private Parse parse;
    private String wall;
    private Site home;
    private volatile boolean isLoading = false; // 添加加载状态标记
    // 加载代际序号：每次发起新加载自增，过期的旧加载回调据此丢弃，避免并发污染单例状态
    private volatile long generation = 0;
    // 单线程串行执行器：所有源加载排队执行，杜绝多线程并发读写单例集合
    private final ExecutorService loadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VodConfig-Loader");
        t.setDaemon(true);
        return t;
    });

    private VodConfig() {
        // 在构造函数中初始化列表，防止空指针异常
        // 使用线程安全容器，配合单线程加载器实现双保险
        this.ads = Collections.synchronizedList(new ArrayList<>());
        this.doh = Collections.synchronizedList(new ArrayList<>());
        this.rules = Collections.synchronizedList(new ArrayList<>());
        this.sites = Collections.synchronizedList(new ArrayList<>());
        this.flags = Collections.synchronizedList(new ArrayList<>());
        this.parses = Collections.synchronizedList(new ArrayList<>());
    }

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    public static void load(Config config, Callback callback) {
        // 参数检查
        if (config == null || callback == null) {
            Logger.e("VodConfig: Invalid parameters: config=" + config + ", callback=" + callback);
            if (callback != null) {
                App.post(() -> callback.error("配置参数无效"));
            }
            return;
        }

        VodConfig instance = get();
        // 本次加载的代际，之后回调时比对，过期的旧加载直接丢弃，避免污染新状态
        // 代际自增必须在 synchronized 内，保证并发换源时每个请求拿到唯一且递增的代际
        final long myGen;
        synchronized (instance) {
            myGen = ++instance.generation;
            instance.isLoading = true;
            // 取消之前仍在排队的同名请求，仅保留本次最新加载
            try {
                OkHttp.cancel("vod");
            } catch (Exception e) {
                Logger.e("VodConfig: Error cancelling previous load", e);
                Logger.e("Error", e);
            }
            // 清除上次崩溃持久化标记：本次是正常换源，说明用户已重新操作，旧崩溃状态作废
            try {
                com.github.catvod.utils.Prefers.remove("crash");
            } catch (Exception ignore) {
            }
        }

        // 投入单线程串行执行器：即使频繁换源，也会排队依次执行，绝不会并发修改单例集合
        instance.loadExecutor.execute(() -> {
            if (myGen != instance.generation) return; // 已被更新的加载取代，丢弃本次结果
            try {
                instance.clear().config(config).load(callback, myGen);
            } catch (Throwable e) {
                instance.isLoading = false;
                Logger.e("VodConfig: Exception during load", e);
                Logger.e("Error", e);
                if (myGen == instance.generation) {
                    App.post(() -> callback.error("配置加载失败: " + e.getMessage()));
                }
            }
        });
    }

    public VodConfig init() {
        this.wall = null;
        this.home = null;
        this.parse = null;
        this.config = Config.vod();
        this.ads = new ArrayList<>();
        this.doh = new ArrayList<>();
        this.rules = new ArrayList<>();
        this.sites = new ArrayList<>();
        this.flags = new ArrayList<>();
        this.parses = new ArrayList<>();
        return this;
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        this.wall = null;
        this.home = null;
        this.parse = null;
        if (this.ads != null) this.ads.clear();
        if (this.doh != null) this.doh.clear();
        if (this.rules != null) this.rules.clear();
        if (this.sites != null) this.sites.clear();
        if (this.flags != null) this.flags.clear();
        if (this.parses != null) this.parses.clear();
        BaseLoader.get().clear();
        return this;
    }

    public void load(Callback callback, long gen) {
        // 通过单线程串行执行器派发，保证所有加载（含 depot 递归）严格排队，绝不并发
        loadExecutor.execute(() -> loadConfig(callback, gen));
    }

    /**
     * 启动时的同步式加载入口：沿用当前代际（不新开一代），由 init() 链式调用。
     * 代际从 0 开始，启动加载属于"第 0 代"，后续用户换源通过 load(Config,Callback) 自增代际取代它。
     */
    public void load(Callback callback) {
        loadExecutor.execute(() -> loadConfig(callback, generation));
    }

    private void loadConfig(Callback callback, long gen) {
        // 代际过期（期间用户又换了源）则整体放弃，避免回调污染最新状态
        if (gen != generation) return;
        try {
            OkHttp.cancel("vod");
            // 异步获取 JSON：避免在单线程 executor 中同步阻塞网络请求
            // 使用 OkHttp.enqueue + 回调再 post 回 loadExecutor 继续处理，不阻塞换源队列
            String finalUrl = UrlUtil.convert(config.getUrl());
            OkHttp.newCall(finalUrl, "vod").enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    if (gen != generation) return;
                    loadExecutor.execute(() -> {
                        if (gen != generation) return;
                        if (TextUtils.isEmpty(config.getUrl())) {
                            isLoading = false;
                            App.post(() -> callback.error(""));
                        } else {
                            loadCache(callback, e, gen);
                        }
                        Logger.e("Error", e);
                    });
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response res) throws IOException {
                    if (gen != generation) return;
                    try {
                        HttpUrl httpUrl = res.request().url();
                        int size = HttpUrl.parse(finalUrl).querySize();
                        String effectiveUrl = httpUrl.querySize() == size ? httpUrl.toString() : finalUrl;
                        String raw = res.body() != null ? res.body().string() : "";
                        res.close();
                        if (gen != generation) return;
                        loadExecutor.execute(() -> {
                            if (gen != generation) return;
                            try {
                                // 预校验：先拿原始文本，非 JSON 对象（如 HTML/纯文本）直接走失败，
                                // 避免 Json.parse(...).getAsJsonObject() 抛 IllegalStateException 导致进程崩溃
                                if (!Json.isObj(raw)) {
                                    Logger.e("VodConfig: response is not a JSON object, url=" + effectiveUrl);
                                    onLoadFailed(callback, "接口返回内容不是有效的配置（可能不是影视源或已失效）", null, gen);
                                    return;
                                }
                                checkJson(Json.parse(raw).getAsJsonObject(), callback, gen);
                            } catch (Throwable e) {
                                if (gen != generation) return;
                                if (TextUtils.isEmpty(config.getUrl())) {
                                    isLoading = false;
                                    App.post(() -> callback.error(""));
                                } else {
                                    loadCache(callback, e, gen);
                                }
                                Logger.e("Error", e);
                            }
                        });
                    } catch (Throwable e) {
                        if (gen != generation) return;
                        loadExecutor.execute(() -> {
                            if (gen != generation) return;
                            if (TextUtils.isEmpty(config.getUrl())) {
                                isLoading = false;
                                App.post(() -> callback.error(""));
                            } else {
                                loadCache(callback, e, gen);
                            }
                            Logger.e("Error", e);
                        });
                    }
                }
            });
        } catch (Throwable e) {
            if (gen != generation) return;
            if (TextUtils.isEmpty(config.getUrl())) {
                isLoading = false;
                App.post(() -> callback.error(""));
            } else {
                loadCache(callback, e, gen);
            }
            Logger.e("Error", e);
        }
    }

    // 加载失败统一兜底：仅提示，绝不写坏数据进数据库，绝不退出应用
    private void onLoadFailed(Callback callback, String msg, Throwable e, long gen) {
        if (gen != generation) return; // 已被更新的加载取代，放弃本次结果
        isLoading = false;
        // 不把坏数据写入 config.json，避免下次启动读到坏数据陷入崩溃死循环
        App.post(() -> callback.error(TextUtils.isEmpty(msg) ? "配置加载失败" : msg));
    }

    private void loadCache(Callback callback, Throwable e, long gen) {
        if (gen != generation) return;
        if (!TextUtils.isEmpty(config.getJson()) && Json.isObj(config.getJson())) {
            checkJson(Json.parse(config.getJson()).getAsJsonObject(), callback, gen);
        } else {
            isLoading = false;
            App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
        }
    }

    private void checkJson(JsonObject object, Callback callback, long gen) {
        if (gen != generation) return;
        if (object.has("msg")) {
            App.post(() -> callback.error(object.get("msg").getAsString()));
        } else if (object.has("urls")) {
            parseDepot(object, callback, gen);
        } else {
            parseConfig(object, callback, gen);
        }
    }

    private void parseDepot(JsonObject object, Callback callback, long gen) {
        if (gen != generation) return;
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, 0));
        Config.delete(config.getUrl());
        config = configs.get(0);
        // 递归加载：仍走单线程串行执行器，保持严格串行，并继续传递代际
        loadExecutor.execute(() -> loadConfig(callback, gen));
    }

    private void parseConfig(JsonObject object, Callback callback, long gen) {
        if (gen != generation) return; // 代际过期，放弃
        try {
            initSite(object);
            initParse(object);
            initOther(object);
            String notice = Json.safeString(object, "notice");
            config.logo(Json.safeString(object, "logo"));
            // 写库前再次校验代际：若期间用户又换了源，绝不把旧源数据持久化
            if (gen != generation) return;
            config.json(object.toString()).update();

            // 重置加载状态
            isLoading = false;

            // 只调用一次success回调，优先显示通知消息
            if (!TextUtils.isEmpty(notice)) {
                App.post(() -> callback.success(notice));
            } else {
                App.post(callback::success);
            }
        } catch (Throwable e) {
            Logger.e("Error", e);
            // 重置加载状态
            isLoading = false;
            App.post(() -> callback.error(Notify.getError(R.string.error_config_parse, e)));
        }
    }

    private void initSite(JsonObject object) {
        if (object.has("video")) {
            initSite(object.getAsJsonObject("video"));
            return;
        }
        String spider = Json.safeString(object, "spider");
        try {
            BaseLoader.get().parseJar(spider, true);
        } catch (Throwable e) {
            Logger.e("VodConfig: Failed to parse spider jar: " + spider, e);
            Logger.e("Error", e);
        }
        
        for (JsonElement element : Json.safeListElement(object, "sites")) {
            try {
                Site site = Site.objectFrom(element);
                if (sites.contains(site)) continue;
                site.setApi(UrlUtil.convert(site.getApi()));
                site.setExt(UrlUtil.convert(site.getExt()));
                site.setJar(parseJar(site, spider));
                sites.add(site.trans().sync());
            } catch (Throwable e) {
                Logger.e("VodConfig: Failed to add site: " + element, e);
                Logger.e("Error", e);
                // 继续处理下一个站点
            }
        }
        for (Site site : sites) {
            if (site.getKey().equals(config.getHome())) {
                setHome(site);
            }
        }
    }

    private void initParse(JsonObject object) {
        for (JsonElement element : Json.safeListElement(object, "parses")) {
            Parse parse = Parse.objectFrom(element);
            if (parse.getName().equals(config.getParse()) && parse.getType() > 1) setParse(parse);
            if (!parses.contains(parse)) parses.add(parse);
        }
    }

    private void initOther(JsonObject object) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        if (home == null) setHome(sites.isEmpty() ? new Site() : sites.get(0));
        if (parse == null) setParse(parses.isEmpty() ? new Parse() : parses.get(0));
        setRules(Rule.arrayFrom(object.getAsJsonArray("rules")));
        setDoh(Doh.arrayFrom(object.getAsJsonArray("doh")));
        setHeaders(Json.safeListElement(object, "headers"));
        setFlags(Json.safeListString(object, "flags"));
        setHosts(Json.safeListString(object, "hosts"));
        setProxy(Json.safeListString(object, "proxy"));
        setWall(Json.safeString(object, "wallpaper"));
        setAds(Json.safeListString(object, "ads"));
    }

    private String parseJar(Site site, String spider) {
        return site.getJar().isEmpty() ? spider : site.getJar();
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    public void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    public List<Parse> getParses(int type) {
        List<Parse> items = new ArrayList<>();
        for (Parse item : getParses()) if (item.getType() == type) items.add(item);
        return items;
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = new ArrayList<>();
        for (Parse item : getParses(type)) if (item.getExt().getFlag().contains(flag)) items.add(item);
        if (items.isEmpty()) items.addAll(getParses(type));
        return items;
    }

    public void setHeaders(List<JsonElement> items) {
        OkHttp.responseInterceptor().setHeaders(items);
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        this.flags.addAll(flags);
    }

    public void setHosts(List<String> hosts) {
        OkHttp.dns().addAll(hosts);
    }

    public void setProxy(List<String> hosts) {
        OkHttp.selector().addAll(hosts);
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
    }

    public Config getConfig() {
        return config == null ? Config.vod() : config;
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        int index = getParses().indexOf(Parse.get(name));
        return index == -1 ? null : getParses().get(index);
    }

    public Site getSite(String key) {
        int index = getSites().indexOf(Site.get(key));
        return index == -1 ? new Site() : getSites().get(index);
    }

    public void setParse(Parse parse) {
        this.parse = parse;
        this.parse.setActivated(true);
        config.parse(parse.getName()).save();
        for (Parse item : getParses()) item.setActivated(parse);
    }

    public void setHome(Site home) {
        if (home == null) {
            // 如果传入null，使用默认站点或创建空站点
            home = sites.isEmpty() ? new Site() : sites.get(0);
        }
        this.home = home;
        this.home.setActivated(true);
        
        // 安全地保存配置，防止空指针异常
        try {
            if (home.getKey() != null && config != null) {
                config.home(home.getKey()).save();
            }
        } catch (Exception e) {
            Logger.e("Error", e);
        }
        
        // 安全地更新所有站点的激活状态
        try {
            for (Site item : getSites()) {
                if (item != null) {
                    item.setActivated(home);
                }
            }
        } catch (Exception e) {
            Logger.e("Error", e);
        }
    }

    private void setWall(String wall) {
        this.wall = wall;
        boolean load = !TextUtils.isEmpty(wall) && WallConfig.get().needSync(wall);
        if (load) WallConfig.get().config(Config.find(wall, config.getName(), 2).update());
    }
}