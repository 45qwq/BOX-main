package com.fongmi.android.tv.db;

import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.Backup;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Path;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 数据库备份/还原管理器
 *
 * 从 AppDatabase 中分离，职责单一化
 */
public class BackupManager {

    private static volatile BackupManager instance;

    public static BackupManager get() {
        if (instance == null) {
            synchronized (BackupManager.class) {
                if (instance == null) instance = new BackupManager();
            }
        }
        return instance;
    }

    private BackupManager() {
    }

    public void backup() {
        backup(new com.fongmi.android.tv.impl.Callback());
    }

    public void backup(com.fongmi.android.tv.impl.Callback callback) {
        App.execute(() -> {
            File file = new File(Path.tv(), "tv-" + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()) + ".bk");
            Backup backup = Backup.create();
            if (backup.getConfig().isEmpty()) {
                App.post(callback::error);
            } else {
                Path.write(file, backup.toString().getBytes());
                FileUtil.gzipCompress(file);
                App.post(callback::success);
                cleanOld();
            }
        });
    }

    public void restore(File file, com.fongmi.android.tv.impl.Callback callback) {
        App.execute(() -> {
            File restore = Path.cache("restore");
            FileUtil.gzipDecompress(file, restore);
            Backup backup = Backup.objectFrom(Path.read(restore));
            if (backup.getConfig().isEmpty()) {
                App.post(callback::error);
            } else {
                backup.restore();
                Path.clear(restore);
                App.post(callback::success);
            }
        });
    }

    /**
     * 清理旧备份文件，仅保留最近 BACKUP_KEEP_COUNT 个
     */
    private void cleanOld() {
        List<File> items = new ArrayList<>();
        File[] files = Path.tv().listFiles();
        if (files == null) files = new File[0];
        for (File file : files) if (file.getName().startsWith("tv") && file.getName().endsWith(".bk.gz")) items.add(file);
        if (!items.isEmpty()) Collections.sort(items, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        if (items.size() > Constant.BACKUP_KEEP_COUNT)
            for (int i = Constant.BACKUP_KEEP_COUNT; i < items.size(); i++) Path.clear(items.get(i));
    }
}
