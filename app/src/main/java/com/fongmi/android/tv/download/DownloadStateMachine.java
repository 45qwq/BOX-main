package com.fongmi.android.tv.download;

import com.github.catvod.utils.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 下载任务状态机
 * 规范状态转换：pending → queued → downloading → (merging) → completed
 *                                              ↓              ↓
 *                                           paused → pending  failed
 *                                            failed → pending (retry)
 *
 * 错误类型分类：
 *   RETRYABLE   - 可重试错误（网络超时、429、503 等）
 *   NON_RETRYABLE - 不可重试错误（404、403、500、文件不存在等）
 */
public class DownloadStateMachine {



    // ============ 状态常量 ============
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_DOWNLOADING = "downloading";
    public static final String STATUS_MERGING = "merging";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_PAUSED = "paused";
    public static final String STATUS_CANCELLED = "cancelled";

    // ============ 错误类型 ============
    public enum ErrorType {
        RETRYABLE,      // 可重试（网络超时、限流等）
        NON_RETRYABLE   // 不可重试（404、403、文件损坏等）
    }

    // 所有有效状态集合
    public static final Set<String> VALID_STATUSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            STATUS_PENDING, STATUS_QUEUED, STATUS_DOWNLOADING, STATUS_MERGING,
            STATUS_COMPLETED, STATUS_FAILED, STATUS_PAUSED, STATUS_CANCELLED
    )));

    // 活跃状态（正在运行或等待运行）
    public static final Set<String> ACTIVE_STATUSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            STATUS_PENDING, STATUS_QUEUED, STATUS_DOWNLOADING, STATUS_MERGING
    )));

    // 终态（不再变化）
    public static final Set<String> FINAL_STATUSES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            STATUS_COMPLETED, STATUS_CANCELLED
    )));

    /**
     * 验证状态转换是否合法
     * @return true 如果转换合法
     */
    public static boolean isValidTransition(String from, String to) {
        if (from == null || to == null) return false;
        if (from.equals(to)) return true;

        switch (from) {
            case STATUS_PENDING:
                return STATUS_QUEUED.equals(to) || STATUS_DOWNLOADING.equals(to)
                        || STATUS_FAILED.equals(to) || STATUS_CANCELLED.equals(to);
            case STATUS_QUEUED:
                return STATUS_DOWNLOADING.equals(to) || STATUS_CANCELLED.equals(to)
                        || STATUS_FAILED.equals(to);
            case STATUS_DOWNLOADING:
                return STATUS_MERGING.equals(to) || STATUS_COMPLETED.equals(to)
                        || STATUS_FAILED.equals(to) || STATUS_PAUSED.equals(to)
                        || STATUS_CANCELLED.equals(to);
            case STATUS_MERGING:
                return STATUS_COMPLETED.equals(to) || STATUS_FAILED.equals(to)
                        || STATUS_CANCELLED.equals(to);
            case STATUS_PAUSED:
                return STATUS_PENDING.equals(to) || STATUS_CANCELLED.equals(to);
            case STATUS_FAILED:
                return STATUS_PENDING.equals(to) || STATUS_CANCELLED.equals(to);
            case STATUS_COMPLETED:
            case STATUS_CANCELLED:
                return false; // 终态不可转换
            default:
                return false;
        }
    }

    /**
     * 安全转换状态，如果转换非法则记录警告
     * @return 新的状态（如果转换非法则返回旧状态）
     */
    public static String safeTransition(String currentStatus, String newStatus) {
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
        return ACTIVE_STATUSES.contains(status);
    }

    /**
     * 判断是否为终态
     */
    public static boolean isFinal(String status) {
        return FINAL_STATUSES.contains(status);
    }

    /**
     * 判断是否为可恢复状态（可从中断点恢复）
     */
    public static boolean isResumable(String status) {
        return STATUS_PAUSED.equals(status) || STATUS_FAILED.equals(status);
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
        switch (status) {
            case STATUS_PENDING: return "等待中";
            case STATUS_QUEUED: return "排队中";
            case STATUS_DOWNLOADING: return "下载中";
            case STATUS_MERGING: return "合并中";
            case STATUS_COMPLETED: return "已完成";
            case STATUS_FAILED: return "失败";
            case STATUS_PAUSED: return "已暂停";
            case STATUS_CANCELLED: return "已取消";
            default: return status;
        }
    }
}