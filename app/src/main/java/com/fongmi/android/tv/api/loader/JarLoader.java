package com.fongmi.android.tv.api.loader;

import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;        // Android 动态加载 Dex/Jar 的核心类

public class JarLoader {

    // 缓存已加载的 DexClassLoader，Key 是 Jar 的标识（通常是 MD5），Value 是加载器实例
    private final ConcurrentHashMap<String, DexClassLoader> loaders;

    // 缓存各个 Jar 包中的代理方法（Proxy.proxy），用于网络代理请求
    private final ConcurrentHashMap<String, Method> methods;

    // 缓存已经实例化的爬虫对象（Spider），Key 是 "JarMD5 + 爬虫Key"
    private final ConcurrentHashMap<String, Spider> spiders;

    // 针对每个不同的 Jar 包配置一把细粒度的锁，防止多线程同时重复下载和加载同一个 Jar
    private final ConcurrentHashMap<String, Object> locks;

    // 标记当前“最近正在使用”或者“当前激活”的 Jar 包的 Key
    private volatile String recent;

    // 构造函数：初始化所有线程安全的容器
    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        spiders = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
    }

    /**
     * 清理缓存：销毁所有爬虫实例，清空类加载器，释放内存
     */
    public void clear() {
        // 调用每个爬虫的 destroy 方法释放资源
        spiders.values().forEach(Spider::destroy);
        loaders.clear();
        methods.clear();
        spiders.clear();
        locks.clear();
        recent = null;
    }

    // 设置当前最新的活跃 Jar 键值
    public void setRecent(String recent) {
        this.recent = recent;
    }

    /**
     * 执行具体的 Jar 文件加载
     * @param key  Jar 的唯一标识
     * @param file 对应的本地 Jar/Dex 文件对象
     */
    private void load(String key, File file) {
        if (Thread.interrupted()) return;                           // 线程中断则退出

        // 文件不存在，或者将其设置为只读失败（Android 安全机制要求），则退出
        if (!Path.exists(file) || !file.setReadOnly()) return;

        // 获取 Android 释放优化后 dex 文件的缓存目录（通常在应用私有沙盒内）
        String cachePath = Path.jar().getAbsolutePath();

        // 实例化 Android 的类加载器：加载外部 jar，并指定父加载器为当前 App 的加载器
        DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, App.get().getClassLoader());
        // 触发调用该 Jar 内的初始化方法
        invokeInit(loader);
        // 触发调用并缓存该 Jar 内的代理方法
        invokeProxy(key, loader);
        // 将加载成功的加载器存入缓存
        loaders.put(key, loader);
    }

    /**
     * 利用反射调用 Jar 包中 com.github.catvod.spider.Init.init(Context) 方法
     * 用于初始化插件环境
     */
    private void invokeInit(DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method method = clz.getMethod("init", Context.class);
            method.invoke(clz, App.get());                          // 传入全局 Application Context
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 利用反射获取 Jar 包中 com.github.catvod.spider.Proxy.proxy(Map) 方法并缓存
     * 用于后续的局部网络请求代理
     */
    private void invokeProxy(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * 解析并准备加载 Jar 包（支持 http/file/assets 以及带有 md5 校验的地址）
     * @param key  自定义的键（通常是 jar 地址的 MD5）
     * @param jar  Jar 的配置地址（例如 "http://.../custom.jar;md5;xxxx"）
     */
    public void parseJar(String key, String jar) {
        if (loaders.containsKey(key)) return;                       // 已经加载过了，直接返回
        // 如果是内置 assets 路径，转换为可访问的链接或路径
        if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
        // 分段锁优化：利用 computeIfAbsent 为当前 key 生成一把专属锁，避免全局锁导致所有 Jar 加载阻塞
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            if (loaders.containsKey(key)) return;                   // 双重检查锁（DCL）

            // 解析是否带有 MD5 校验码（格式：url;md5;值）
            String[] texts = jar.split(";md5;");
            String md5 = texts.length > 1 ? texts[1].trim() : "";

            // 如果 md5 的字段是个 http 链接，说明需要请求这个链接获取真正的 md5 文本
            if (md5.startsWith("http")) md5 = OkHttp.string(md5).trim();
            jar = texts[0];                                         // 真正的 jar 路径或链接

            // 判断选择加载/下载模式
            if (!md5.isEmpty() && Util.equals(jar, md5)) {
                // 本地已存在该 MD5 对应的文件，直接加载
                load(key, Path.jar(jar));
            } else if (jar.startsWith("http")) {
                // 远程 HTTP 链接，先下载到本地，再加载
                load(key, Download.create(jar, Path.jar(jar)).get());
            } else if (jar.startsWith("file")) {
                // 本地绝对路径文件，直接加载
                load(key, Path.local(jar));
            }
        }
    }

    /**
     * 根据传入的 jar 配置直接获取对应的 DexClassLoader
     */
    public DexClassLoader dex(String jar) {
        try {
            String jaKey = Util.md5(jar);
            parseJar(jaKey, jar);
            return loaders.get(jaKey);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 动态获取并实例化一个爬虫（Spider）对象
     * @param key  站点名/Key
     * @param api  具体的爬虫类名后缀（例如：csp_Bili -> 提取出 Bili）
     * @param ext  扩展参数（传给爬虫 init 方法）
     * @param jar  该爬虫所属的 jar 包地址
     */
    public Spider getSpider(String key, String api, String ext, String jar) {
        String jaKey = Util.md5(jar);                           // 算出 Jar 的全局唯一 Key
        String spKey = jaKey + key;                             // 算出该站爬虫的复合 Key
        // 如果缓存里有就直接用，没有就去动态加载并创建
        return spiders.computeIfAbsent(spKey, k -> {
            try {
                parseJar(jaKey, jar);                           // 确保 Jar 包已经被加载
                DexClassLoader loader = loaders.get(jaKey);
                if (loader == null) return new SpiderNull();    // 加载失败返回空对象防空指针
                // 关键点：根据规则动态拼接出全类名，例如 "com.github.catvod.spider.Bili"
                // 并利用反射调用 .newInstance() 实例化
                Spider spider = (Spider) loader.loadClass("com.github.catvod.spider." + api.split("csp_")[1]).newInstance();

                // 初始化爬虫属性
                spider.siteKey = key;                           // 传入 Context 和配置的扩展规则
                spider.init(App.get(), ext);
                return spider;
            } catch (Throwable e) {
                e.printStackTrace();
                return new SpiderNull();                        // 异常降级处理
            }
        });
    }

    // 检查并获取当前激活的 ClassLoader，不存在则抛出异常
    private DexClassLoader requireRecentLoader() {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) throw new IllegalStateException("No jar loaded for recent key: " + recent);
        return loader;
    }

    /**
     * 动态调用 Json 解析器
     * 通过反射调用指定 Jar 里的 com.github.catvod.parser.Json[Key] 的 parse 方法
     */
    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Json" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
        return (JSONObject) method.invoke(null, jxs, url);
    }

    /**
     * 动态调用混合解析器（Mix）
     * 通过反射调用指定 Jar 里的 com.github.catvod.parser.Mix[Key] 的 parse 方法
     */
    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Mix" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
        return (JSONObject) method.invoke(null, jxs, name, flag, url);
    }

    /**
     * 核心路由代理请求：当本地建立起一个 Local Server 转发视频流量时，
     * 流量会经过此方法路由给 Jar 包中的 Proxy 类去处理（比如去破解防盗链、加解密音视频流）
     */
    public Object[] proxy(Map<String, String> params) throws Exception {
        // 先尝试用“当前最近激活”的 Jar 的代理方法去处理
        Method method = recent != null ? methods.get(recent) : null;
        Object[] result = proxyInvoke(method, params);
        if (result != null) return result;

        // 如果当前激活的 Jar 没办法处理（返回 null），则遍历其它已经加载的 Jar 尝试处理
        return tryOthers(params);
    }

    // 遍历其它非 recent 的缓存方法，找到第一个能成功返回结果的
    private Object[] tryOthers(Map<String, String> p) {
        return methods.entrySet().stream().filter(e -> !e.getKey().equals(recent)).map(e -> proxyInvoke(e.getValue(), p)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    // 反射执行 Proxy 方法的具体封装
    private Object[] proxyInvoke(Method method, Map<String, String> params) {
        try {
            return method == null ? null : (Object[]) method.invoke(null, params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
