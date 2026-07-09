package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.db.AppDatabase;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Download {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_MERGING = 3;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_FAILED = -1;

    @PrimaryKey
    @NonNull
    @SerializedName("id")
    private String id;
    @SerializedName("vodPic")
    private String vodPic;
    @SerializedName("vodName")
    private String vodName;
    @SerializedName("vodId")
    private String vodId;
    @SerializedName("url")
    private String url;
    @SerializedName("header")
    private String header;
    @SerializedName("createTime")
    private long createTime;
    @SerializedName("progress")
    private int progress;
    @SerializedName("status")
    private String status;
    @SerializedName("statusInt")
    private int statusInt;
    @SerializedName("duration")
    private long duration;
    @SerializedName("speed")
    private long speed;
    @SerializedName("filePath")
    private String filePath;

    @SerializedName("segmentInfo")
    private String segmentInfo;
    @SerializedName("errorMsg")
    private String errorMsg;

    public static Download objectFrom(String str) {
        try {
            return new Gson().fromJson(str, Download.class);
        } catch (Exception e) {
            return new Download();
        }
    }

    public Download() {
        this.createTime = System.currentTimeMillis();
        this.progress = 0;
        this.status = "pending";
    }

    @androidx.room.Ignore
    public Download(@NonNull String id, String vodPic, String vodName, String url) {
        this.id = id;
        this.vodPic = vodPic;
        this.vodName = vodName;
        this.url = url;
        this.createTime = System.currentTimeMillis();
        this.progress = 0;
        this.status = "pending";
    }

    @androidx.room.Ignore
    public Download(@NonNull String id, String vodPic, String vodName, String url, String header) {
        this.id = id;
        this.vodPic = vodPic;
        this.vodName = vodName;
        this.url = url;
        this.header = header;
        this.createTime = System.currentTimeMillis();
        this.progress = 0;
        this.status = "pending";
    }

    @NonNull
    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getVodPic() {
        return vodPic == null ? "" : vodPic;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public String getVodName() {
        return vodName == null ? "" : vodName;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public String getVodId() {
        return vodId == null ? "" : vodId;
    }

    public void setVodId(String vodId) {
        this.vodId = vodId;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHeader() {
        return header == null ? "" : header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getStatus() {
        return status == null ? "pending" : status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getStatusInt() {
        return statusInt;
    }

    public void setStatusInt(int statusInt) {
        this.statusInt = statusInt;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(long speed) {
        this.speed = speed;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getSegmentInfo() {
        return segmentInfo;
    }

    public void setSegmentInfo(String segmentInfo) {
        this.segmentInfo = segmentInfo;
    }

    public String getErrorMsg() {
        return errorMsg == null ? "" : errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Download save() {
        AppDatabase.get().getDownloadDao().insertOrUpdate(this);
        return this;
    }

    public void delete() {
        AppDatabase.get().getDownloadDao().delete(this);
    }

    public String generateFileName() {
        return generateFileName(this.vodName, this.url);
    }

    public static String generateDownloadFileName(String vodName, String url) {
        String cleanName = vodName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String extension = ".mp4";
        boolean isM3U8 = url != null && (url.toLowerCase().contains(".m3u8") || url.toLowerCase().contains("m3u8") || url.toLowerCase().contains("playlist"));
        if (isM3U8) {
            extension = ".mp4";
        } else if (url != null && url.contains(".")) {
            String urlExt = url.substring(url.lastIndexOf("."));
            if (urlExt.contains("?")) urlExt = urlExt.substring(0, urlExt.indexOf("?"));
            if (urlExt.matches("\\.(mp4|mkv|avi|mov|wmv|flv|webm|ts)")) {
                extension = urlExt;
            }
        }
        return cleanName + extension;
    }

    private String generateFileName(String vodName, String url) {
        return generateDownloadFileName(vodName, url);
    }

    public static void clear() {
        AppDatabase.get().getDownloadDao().clear();
    }

    public void deleteVideoFile() {
        try {
            // 优先使用存储的文件路径
            if (filePath != null && !filePath.isEmpty()) {
                File videoFile = new File(filePath);
                if (videoFile.exists()) {
                    videoFile.delete();
                    return;
                }
            }
            // 降级：按文件名搜索下载目录
            File downloadDir = getDownloadDir();
            String fileName = generateFileName(this.vodName, this.url);
            File videoFile = new File(downloadDir, fileName);
            if (videoFile.exists()) {
                videoFile.delete();
            }
        } catch (Exception e) {
            com.github.catvod.utils.Logger.e("Download: 删除视频文件出错", e);
        }
    }

    public void deleteWithFile() {
        deleteVideoFile();
        delete();
    }

    public static void clearWithFiles() {
        List<Download> all = getAll();
        for (Download d : all) {
            d.deleteVideoFile();
        }
        clear();
    }

    public static List<Download> getAll() {
        return AppDatabase.get().getDownloadDao().getAll();
    }

    public static Download find(String id) {
        return AppDatabase.get().getDownloadDao().find(id);
    }

    public static Download findByVodName(String name) {
        return AppDatabase.get().getDownloadDao().findByVodName(name);
    }

    public static File getDownloadDir() {
        String customPath = Setting.getDownloadPath();
        if (customPath != null && !customPath.isEmpty()) {
            File dir = new File(customPath);
            if (!dir.exists()) dir.mkdirs();
            return dir;
        }
        return com.github.catvod.utils.Path.download();
    }

    /**
     * 扫描下载目录中的已下载文件，与数据库记录合并
     * 返回完整的下载列表（文件中有的 + 数据库中正在下载/失败的）
     */
    public static List<Download> getMergedDownloads() {
        List<Download> result = new ArrayList<>();
        // 1. 扫描本地目录中的文件
        File downloadDir = getDownloadDir();
        if (downloadDir.exists() && downloadDir.isDirectory()) {
            File[] files = downloadDir.listFiles((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".mp4") || lower.endsWith(".ts") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".webm");
            });
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    // 去掉扩展名作为 vodName
                    int dot = name.lastIndexOf('.');
                    String vodName = dot > 0 ? name.substring(0, dot) : name;
                    // 尝试匹配数据库记录
                    Download dbDownload = findByVodName(vodName);
                    if (dbDownload != null) {
                        dbDownload.setFilePath(file.getAbsolutePath());
                        result.add(dbDownload);
                    } else {
                        // 新建一个完成状态的 Download 对象
                        Download d = new Download();
                        d.setId("file_" + file.getAbsolutePath());
                        d.setVodName(vodName);
                        d.setProgress(100);
                        d.setStatus("completed");
                        d.setSpeed(0);
                        d.setFilePath(file.getAbsolutePath());
                        d.setCreateTime(file.lastModified());
                        result.add(d);
                    }
                }
            }
        }
        // 2. 添加数据库中正在下载/失败/等待的记录
        List<Download> allDb = getAll();
        for (Download db : allDb) {
            boolean exists = false;
            for (Download r : result) {
                if (r.getId().equals(db.getId()) || r.getVodName().equals(db.getVodName())) {
                    exists = true;
                    break;
                }
            }
            if (!exists && !"completed".equals(db.getStatus())) {
                result.add(db);
            }
        }
        // 按创建时间排序
        java.util.Collections.sort(result, (a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Download)) return false;
        Download download = (Download) obj;
        return getId().equals(download.getId());
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
