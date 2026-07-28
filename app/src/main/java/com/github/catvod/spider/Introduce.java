package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Notify;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.github.catvod.spider.GoProxyManager;

public class Introduce extends Spider {

    private static final String PIC = "https://androidcatvodspider.netlify.app/wechat.png";

    @Override
    public void init(Context context, String extend) throws Exception {
        GoProxyManager.init(context);
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("go", "Go代理管理"));
        return Result.string(classes, new ArrayList<>());
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        List<Vod> vodList = new ArrayList<>();
        
        if (tid.equals("go")) {
            // 操作按钮
            vodList.add(new Vod("go_start", "▶️ 启动 Go 代理", PIC));
            vodList.add(new Vod("go_stop", "⏹️ 停止 Go 代理", PIC));
            vodList.add(new Vod("go_copy", "📋 复制 Go 文件", PIC));
            vodList.add(new Vod("go_delete", "🗑️ 删除 Go 文件", PIC));
            vodList.add(new Vod("go_restart", "🔄 重启 Go 代理", PIC));
            
            // 状态显示
            boolean isRunning = GoProxyManager.isRunning();
            String statusText = isRunning ? "🟢 运行中" : "🔴 未运行";
            vodList.add(new Vod("status", "📊" + statusText, PIC));
        }
        
        return Result.get().vod(vodList).page().string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        Context ctx = GoProxyManager.getContext();
        File destFile = ctx != null ? new File(ctx.getFilesDir(), "pvideo-arm64-v8a") : null;
        File srcFile = new File("/storage/emulated/0/Download/PG/pvideo-arm64-v8a");
        
        String resultMsg = "";
        
        switch (vodId) {
            case "go_start":
                GoProxyManager.stopProxy();
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                boolean started = GoProxyManager.deployAndStart(ctx);
                boolean isRunning = GoProxyManager.isRunning();
                resultMsg = (started || isRunning) ? "✅ Go 代理启动成功" : "❌ 启动失败，请检查源文件";
                Notify.show(resultMsg);
                break;
                
            case "go_stop":
                boolean stopped = GoProxyManager.stopProxy();
                resultMsg = stopped ? "✅ Go 代理已停止" : "❌ 停止失败";
                Notify.show(resultMsg);
                break;
                
            case "go_copy":
                if (!srcFile.exists()) {
                    resultMsg = "❌ 源文件不存在";
                    Notify.show(resultMsg);
                    break;
                }
                if (destFile != null && destFile.exists()) {
                    destFile.delete();
                }
                boolean copied = GoProxyManager.deployAndStart(ctx);
                resultMsg = copied ? "✅ Go 文件复制成功" : "❌ 复制失败";
                Notify.show(resultMsg);
                break;
                
            case "go_delete":
                if (destFile != null && destFile.exists()) {
                    GoProxyManager.stopProxy();
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    boolean deleted = destFile.delete();
                    resultMsg = deleted ? "✅ Go 文件已删除" : "❌ 删除失败";
                } else {
                    resultMsg = "⚠️ 文件不存在";
                }
                Notify.show(resultMsg);
                break;
                
            case "go_restart":
                GoProxyManager.stopProxy();
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                boolean restarted = GoProxyManager.deployAndStart(ctx);
                boolean running = GoProxyManager.isRunning();
                resultMsg = (restarted || running) ? "✅ Go 代理重启成功" : "❌ 重启失败";
                Notify.show(resultMsg);
                break;
                
            default:
                resultMsg = "📊 " + (GoProxyManager.isRunning() ? "🟢 运行中" : "🔴 未运行");
                Notify.show(resultMsg);
                break;
        }
        
        Vod item = new Vod();
        item.setVodId(vodId);
        item.setVodName(resultMsg);
        item.setVodPic(PIC);
        item.setVodPlayFrom("操作结果");
        item.setVodPlayUrl("");
        
        return Result.string(item);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return Result.get().url("").string();
    }
}