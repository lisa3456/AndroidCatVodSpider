package com.github.catvod.spider;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import com.github.catvod.crawler.SpiderDebug;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.math.BigInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoProxyManager {

    private static final String TAG = "GoProxyManager";
    private static final String GO_FILE_NAME = "pvideo-arm64-v8a";
    private static final String SOURCE_PATH = "/storage/emulated/0/Download/PG/pvideo-arm64-v8a";

    private static GoProxyManager instance;
    private Application app;
    private Process goProcess;
    private ExecutorService executor;

    private GoProxyManager() {
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static GoProxyManager get() {
        if (instance == null) {
            synchronized (GoProxyManager.class) {
                if (instance == null) {
                    instance = new GoProxyManager();
                }
            }
        }
        return instance;
    }

    public static Context getContext() {
        return get().app;
    }

    public static void init(Context context) {
        get().app = (Application) context;
        SpiderDebug.log("GoProxyManager 初始化完成");
        get().startProxy();
    }

    private void startProxy() {
        executor.execute(() -> {
            if (app == null) {
                Log.e(TAG, "Context 未初始化");
                return;
            }
            doDeployAndStart(app);
        });
    }

    public static boolean deployAndStart(Context context) {
        if (context == null) {
            context = get().app;
        }
        if (context == null) {
            Log.e(TAG, "Context 为空");
            return false;
        }
        return get().doDeployAndStart(context);
    }

    private boolean doDeployAndStart(Context context) {
        try {
            File destDir = context.getFilesDir();
            File destFile = new File(destDir, GO_FILE_NAME);
            File srcFile = new File(SOURCE_PATH);

            boolean srcExists = srcFile.exists();
            boolean destExists = destFile.exists();

            // ===== 情况1：源文件不存在，目标文件不存在 → 退出 =====
            if (!srcExists && !destExists) {
                Log.e(TAG, "❌ 源文件和目标文件都不存在");
                SpiderDebug.log("❌ Go 代理启动失败：源文件不存在，且目标文件也不存在");
                return false;
            }

            // ===== 情况2：源文件不存在，目标文件存在 → 直接启动 =====
            if (!srcExists && destExists) {
                Log.d(TAG, "✅ 源文件不存在，目标文件存在，直接启动");
                if (!isRunning()) {
                    return startProcess(destFile);
                }
                Log.d(TAG, "✅ 代理已在运行");
                return true;
            }

            // ===== 情况3：源文件存在，目标文件不存在 → 复制并启动 =====
            if (srcExists && !destExists) {
                Log.d(TAG, "📦 源文件存在，目标文件不存在，复制并启动");
                if (!copyFile(srcFile, destFile)) {
                    Log.e(TAG, "复制文件失败");
                    return false;
                }
                if (!chmod755(destFile)) {
                    Log.e(TAG, "设置执行权限失败");
                    return false;
                }
                if (!startProcess(destFile)) {
                    Log.e(TAG, "启动代理失败");
                    return false;
                }
                SpiderDebug.log("✅ Go 代理已启动: ws://127.0.0.1:5266/alllive/danmaku");
                return true;
            }

            // ===== 情况4：源文件存在，目标文件存在 → 比较 MD5 =====
            if (srcExists && destExists) {
                String srcMD5 = getFileMD5(srcFile);
                String destMD5 = getFileMD5(destFile);

                if (srcMD5 == null || destMD5 == null) {
                    Log.w(TAG, "⚠️ MD5 计算失败，使用目标文件启动");
                    if (!isRunning()) {
                        return startProcess(destFile);
                    }
                    return true;
                }

                if (!srcMD5.equals(destMD5)) {
                    Log.d(TAG, "📦 MD5 不同，更新文件并启动");
                    if (destFile.exists()) {
                        destFile.delete();
                    }
                    if (!copyFile(srcFile, destFile)) {
                        Log.e(TAG, "复制文件失败");
                        return false;
                    }
                    if (!chmod755(destFile)) {
                        Log.e(TAG, "设置执行权限失败");
                        return false;
                    }
                    if (!startProcess(destFile)) {
                        Log.e(TAG, "启动代理失败");
                        return false;
                    }
                    SpiderDebug.log("✅ Go 代理已更新并启动: ws://127.0.0.1:5266/alllive/danmaku");
                    return true;
                } else {
                    Log.d(TAG, "✅ MD5 相同，文件未变化");
                    if (!isRunning()) {
                        Log.d(TAG, "代理未运行，重新启动...");
                        return startProcess(destFile);
                    }
                    Log.d(TAG, "✅ 代理已在运行");
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "部署失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String getFileMD5(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, length);
                }
            }
            byte[] digest = md.digest();
            return new BigInteger(1, digest).toString(16);
        } catch (Exception e) {
            Log.e(TAG, "计算 MD5 失败: " + e.getMessage());
            return null;
        }
    }

    private boolean copyFile(File src, File dest) {
        try {
            if (dest.exists()) dest.delete();
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                out.flush();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "复制文件异常: " + e.getMessage());
            return false;
        }
    }

    private boolean chmod755(File file) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"chmod", "755", file.getAbsolutePath()});
            return process.waitFor() == 0;
        } catch (Exception e) {
            Log.e(TAG, "chmod 异常: " + e.getMessage());
            return false;
        }
    }

    private boolean startProcess(File goFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "nohup " + goFile.getAbsolutePath() + " > /dev/null 2>&1 &");
            pb.redirectErrorStream(true);
            goProcess = pb.start();
            Thread.sleep(500);
            return isRunning();
        } catch (Exception e) {
            Log.e(TAG, "启动进程异常: " + e.getMessage());
            return false;
        }
    }

    public static boolean isRunning() {
        try {
            java.net.Socket socket = new java.net.Socket();
            java.net.InetSocketAddress address = new java.net.InetSocketAddress("127.0.0.1", 5266);
            socket.connect(address, 500);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean stopProxy() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"pkill", "-f", "pvideo-arm64-v8a"});
            int result = process.waitFor();
            if (result != 0) {
                process = Runtime.getRuntime().exec(new String[]{"killall", "pvideo-arm64-v8a"});
                result = process.waitFor();
            }
            return result == 0;
        } catch (Exception e) {
            Log.e(TAG, "停止进程异常: " + e.getMessage());
            return false;
        }
    }
}