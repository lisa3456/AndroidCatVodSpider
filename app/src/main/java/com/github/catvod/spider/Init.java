package com.github.catvod.spider;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.github.catvod.spider.GoProxyManager;
import com.github.catvod.crawler.SpiderDebug;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Init {

    private final ExecutorService executor;
    private final Handler handler;
    private Application app;

    private static class Loader {
        static volatile Init INSTANCE = new Init();
    }

    public static Init get() {
        return Loader.INSTANCE;
    }

    public Init() {
        this.handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newFixedThreadPool(5);
    }

    public static Application context() {
        return get().app;
    }

    public static void init(Context context) {
        get().app = ((Application) context);
        SpiderDebug.log("自定義爬蟲代碼載入成功！");
        execute(() -> {
            try {
                // 检查是否已在运行
                if (GoProxyManager.isRunning()) {
                    SpiderDebug.log("✅ Go 代理已在运行");
                    return;
                }
                // 启动代理
                boolean started = GoProxyManager.deployAndStart(context);
                if (started) {
                    SpiderDebug.log("✅ Go 代理已自动启动");
                } else {
                    SpiderDebug.log("⚠️ Go 代理启动失败，请检查文件");
                }
            } catch (Exception e) {
                SpiderDebug.log("❌ Go 代理启动异常: " + e.getMessage());
            }
        });
    }

    public static void execute(Runnable runnable) {
        get().executor.execute(runnable);
    }

    public static void run(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void run(Runnable runnable, int delay) {
        get().handler.postDelayed(runnable, delay);
    }

    public static void checkPermission() {
        try {
            Activity activity = Init.getActivity();
            if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return;
            }
    
            // 检查是否已经授予了读权限和写权限
            boolean hasReadPermission = activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            boolean hasWritePermission = activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    
            // 如果已经授予了读权限和写权限，直接返回
            if (hasReadPermission && hasWritePermission) {
                return;
            }
    
            // 申请读权限和写权限
            List<String> permissionsToRequest = new ArrayList<>();
            if (!hasReadPermission) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (!hasWritePermission) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
    
            if (!permissionsToRequest.isEmpty()) {
                activity.requestPermissions(permissionsToRequest.toArray(new String [permissionsToRequest.size()]), 9999);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static Activity getActivity() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
        Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
        activitiesField.setAccessible(true);
        Map<?, ?> activities = (Map<?, ?>) activitiesField.get(activityThread);
        for (Object activityRecord : activities.values()) {
            Class<?> activityRecordClass = activityRecord.getClass();
            Field pausedField = activityRecordClass.getDeclaredField("paused");
            pausedField.setAccessible(true);
            if (!pausedField.getBoolean(activityRecord)) {
                Field activityField = activityRecordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Activity activity = (Activity) activityField.get(activityRecord);
                SpiderDebug.log(activity.getComponentName().getClassName());
                return activity;
            }
        }
        return null;
    }
}
