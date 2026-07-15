package com.fongmi.android.tv.download;

import com.github.catvod.utils.Logger;

import java.util.EnumSet;
import java.util.Set;

/**
 * 下载任务状态机
 * 规范状态转换：PENDING → QUEUED → DOWNLOADING → (MERGING) → COMPLETED
 *                                              ↓              ↓
 *                                            PAUSED → PENDING  FAILED
 *                                             FAILED → PENDING (retry)
 *
 * 错误类型分类：
 *   RETRYABLE   - 可重试错误（网络超时、429、503 等）
 *   NON_RETRYABLE - 不可重试错误（404、403、500、文件不存在等）
 */
public class DownloadStateMachine {

    /** 下载任务状态枚举 */
    public enum Status {
        PENDING("等待中"),
        QUEUED("排队中"),
        DOWNLOADING("下载中"),
        MERGING("合并中"),
        COMPLETED("已完成"),
        FAILED("失败"),
        PAUSED("已暂停"),
        CANCELLED("已取消");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        /** 获取中文描述 */
        public static String getDisplayName(Status status) {
            return status != null ? status.displayName : "未知";
        }
    }

    // ============ 错误类型 ============
    public enum ErrorType {
        RETRYABLE,      // 可重试（网络超时、限流等）
        NON_RETRYABLE   // 不可重试（404、403、文件损坏等）
    }

    // 所有有效状态
    public static final Set<Status> VALID_STATUSES = EnumSet.allOf(Status.class);

    // 活跃状态（正在运行或等待运行）
    public static final Set<Status> ACTIVE_STATUSES = EnumSet.of(
            Status.PENDING, Status.QUEUED, Status.DOWNLOADING, Status.MERGING
    );

    // 终态（不再变化）
    public static final Set<Status> FINAL_STATUSES = EnumSet.of(
            Status.COMPLETED, Status.CANCELLED
    );

    /**
     * 验证状态转换是否合法
     * @return true 如果转换合法
     */
    public static boolean isValidTransition(Status from, Status to) {
        if (from == null || to == null) return false;
        if (from == to) return true;

        switch (from) {
            case PENDING:
                return to == Status.QUEUED || to == Status.DOWNLOADING
                        || to == Status.FAILED || to == Status.CANCELLED;
            case QUEUED:
                return to == Status.DOWNLOADING || to == Status.CANCELLED
                        || to == Status.FAILED;
            case DOWNLOADING:
                return to == Status.MERGING || to == Status.COMPLETED
                        || to == Status.FAILED || to == Status.PAUSED
                        || to == Status.CANCELLED;
            case MERGING:
                return to == Status.COMPLETED || to == Status.FAILED
                        || to == Status.CANCELLED;
            case PAUSED:
                return to == Status.PENDING || to == Status.CANCELLED;
            case FAILED:
                return to == Status.PENDING || to == Status.CANCELLED;
            case COMPLETED:
            case CANCELLED:
                return false; // 终态不可转换
            default:
                return false;
        }
    }

    /**
     * 安全转换状态，如果转换非法则记录警告
     * @return 新的状态（如果转换非法则返回旧状态）
     */
    public static Status safeTransition(Status currentStatus, Status newStatus) {
        if (isValidTransition(currentStatus, newStatus)) {
            return newStatus;
        }
        Logger.w("DownloadStateMachine: 非法状态转换: " + currentStatus + " → " + newStatus + "，已忽略");
        return currentStatus;
    }

    /**
     * 判断是否为活跃状态（正在运行或等待运行）
     */
    public static boolean isActive(String status) {
        if (status == null) return false;
        try {
            Status s = Status.valueOf(status);
            return ACTIVE_STATUSES.contains(s);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 判断是否为终态
     */
    public static boolean isFinal(String status) {
        if (status == null) return false;
        try {
            Status s = Status.valueOf(status);
            return FINAL_STATUSES.contains(s);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 判断是否为可恢复状态（可从中断点恢复）
     */
    public static boolean isResumable(String status) {
        if (status == null) return false;
        try {
            Status s = Status.valueOf(status);
            return s == Status.PAUSED || s == Status.FAILED;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 根据 HTTP 状态码判断错误类型
     */
    public static ErrorType classifyHttpError(int httpCode) {
        if (httpCode == 429 || httpCode == 503) {
            return ErrorType.RETRYABLE; // 限流/服务不可用，可重试
        }
        if (httpCode == 408 || httpCode == 504) {
            return ErrorType.RETRYABLE; // 超时，可重试
        }
        if (httpCode >= 400 && httpCode < 500) {
            return ErrorType.NON_RETRYABLE; // 4xx 客户端错误，不可重试
        }
        if (httpCode >= 500) {
            return ErrorType.RETRYABLE; // 5xx 服务端错误，可重试
        }
        return ErrorType.NON_RETRYABLE;
    }

    /**
     * 根据异常信息判断错误类型
     */
    public static ErrorType classifyError(Throwable e) {
        if (e == null) return ErrorType.NON_RETRYABLE;
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        // 超时类错误可重试
        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("unreachable")
                || msg.contains("refused") || msg.contains("reset") || msg.contains("eof")) {
            return ErrorType.RETRYABLE;
        }
        // 文件类错误不可重试
        if (msg.contains("404") || msg.contains("403") || msg.contains("forbidden")
                || msg.contains("not found") || msg.contains("no such file")) {
            return ErrorType.NON_RETRYABLE;
        }
        // 默认认为可重试
        return ErrorType.RETRYABLE;
    }

    /**
     * 计算重试等待时间（指数退避）
     * @param attempt 当前重试次数（从1开始）
     * @param baseDelayMs 基础延迟（毫秒）
     * @return 等待时间（毫秒）
     */
    public static long computeRetryDelay(int attempt, long baseDelayMs) {
        if (attempt <= 1) return baseDelayMs;
        // 指数退避: 1s, 2s, 4s, 8s...
        long delay = baseDelayMs * (1L << (attempt - 1));
        // 上限 30 秒
        return Math.min(delay, 30_000L);
    }

    /**
     * 获取状态的中文描述
     */
    public static String getStatusDisplay(String status) {
        if (status == null) return "未知";
        try {
            Status s = Status.valueOf(status);
            return Status.getDisplayName(s);
        } catch (IllegalArgumentException e) {
            return status;
        }
    }
}