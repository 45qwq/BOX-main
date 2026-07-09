package com.fongmi.android.tv.utils

import com.fongmi.android.tv.App
import com.fongmi.android.tv.bean.Backup
import com.fongmi.android.tv.bean.History
import com.fongmi.android.tv.db.AppDatabase
import com.fongmi.android.tv.event.RefreshEvent
import com.github.catvod.utils.Logger
import com.github.catvod.utils.Prefers
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * WebDAV同步管理器
 * 用于同步观看记录和设置到WebDAV服务器
 */
class WebDAVSyncManager private constructor() {

    // 同步模式：ACCOUNT（账号模式）或 CODE（同步码模式）
    enum class SyncMode {
        ACCOUNT,  // 使用WebDAV账号
        CODE      // 使用同步码（无需账号）
    }

    companion object {
        private var instance: WebDAVSyncManager? = null

        @JvmStatic
        fun get(): WebDAVSyncManager {
            if (instance == null) {
                instance = WebDAVSyncManager()
            }
            return instance!!
        }

        /**
         * 生成同步码
         * @return 8位随机同步码（字母+数字）
         */
        @JvmStatic
        fun generateSyncCode(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val random = java.util.Random()
            val code = StringBuilder()
            for (i in 0 until 8) {
                code.append(chars[random.nextInt(chars.length)])
            }
            return code.toString()
        }
    }

    private var sardine: Sardine? = null
    private var baseUrl: String? = null
    private var username: String? = null
    private var password: String? = null
    private var syncCode: String? = null  // 同步码
    private var syncMode = SyncMode.ACCOUNT  // 默认使用账号模式
    @Volatile
    private var isSyncing = false  // 同步锁，防止重复同步

    private val HISTORY_FILE = "xmbox_history.json"
    private val SETTINGS_FILE = "xmbox_settings.json"
    private val BACKUP_FILE = "xmbox_backup.json"

    init {
        loadConfig()
    }

    /**
     * 加载WebDAV配置
     */
    private fun loadConfig() {
        // 检查同步模式
        val modeStr = com.fongmi.android.tv.Setting.getWebDAVSyncMode()
        if ("CODE" == modeStr) {
            syncMode = SyncMode.CODE
            syncCode = com.fongmi.android.tv.Setting.getWebDAVSyncCode()
            // 同步码模式：使用公开的WebDAV服务器（如jsDelivr CDN的GitHub仓库）
            // 或者使用其他公开存储服务
            baseUrl = getPublicStorageUrl()
            username = null
            password = null
        } else {
            syncMode = SyncMode.ACCOUNT
            baseUrl = com.fongmi.android.tv.Setting.getWebDAVUrl()
            username = com.fongmi.android.tv.Setting.getWebDAVUsername()
            password = com.fongmi.android.tv.Setting.getWebDAVPassword()
        }

        if (syncMode == SyncMode.ACCOUNT) {
            // 账号模式：需要账号密码
            if (!baseUrl.isNullOrEmpty() && !username.isNullOrEmpty() && !password.isNullOrEmpty()) {
                try {
                    sardine = OkHttpSardine()
                    sardine!!.setCredentials(username, password)
                    Logger.d("WebDAV: 账号模式配置已加载")
                } catch (e: Exception) {
                    Logger.e("WebDAV: 初始化失败: " + e.message)
                    sardine = null
                }
            } else {
                sardine = null
            }
        } else {
            // 同步码模式：使用公开存储，无需认证
            if (!syncCode.isNullOrEmpty() && !baseUrl.isNullOrEmpty()) {
                try {
                    sardine = OkHttpSardine()
                    // 公开存储不需要认证
                    Logger.d("WebDAV: 同步码模式配置已加载，同步码: " + syncCode)
                } catch (e: Exception) {
                    Logger.e("WebDAV: 初始化失败: " + e.message)
                    sardine = null
                }
            } else {
                sardine = null
            }
        }
    }

    /**
     * 获取公开存储URL（同步码模式使用）
     * 方案：使用GitHub Gist作为公开存储
     * 用户需要：
     * 1. 创建一个GitHub Gist（公开）
     * 2. 获取Gist的raw URL
     * 3. 输入同步码
     *
     * 文件路径格式：{gist_raw_url}/{syncCode}/xmbox_history.json
     */
    private fun getPublicStorageUrl(): String? {
        // 获取用户配置的GitHub Gist raw URL
        // 例如：https://gist.githubusercontent.com/username/gist_id/raw/
        val gistBaseUrl = com.fongmi.android.tv.Setting.getWebDAVPublicUrl()

        if (gistBaseUrl.isNullOrEmpty()) {
            // 如果没有配置，返回null（需要用户配置）
            return null
        }

        // 将同步码添加到路径中，作为子目录
        // 例如：https://gist.githubusercontent.com/username/gist_id/raw/ABC123XYZ/
        if (!syncCode.isNullOrEmpty()) {
            val url = if (gistBaseUrl.endsWith("/")) gistBaseUrl else "$gistBaseUrl/"
            return url + syncCode + "/"
        }

        return gistBaseUrl
    }

    /**
     * 检查WebDAV是否已配置
     */
    fun isConfigured(): Boolean {
        return if (syncMode == SyncMode.CODE) {
            // 同步码模式：需要同步码和公开存储URL
            sardine != null && !baseUrl.isNullOrEmpty() && !syncCode.isNullOrEmpty()
        } else {
            // 账号模式：需要账号密码和URL
            sardine != null && !baseUrl.isNullOrEmpty() && !username.isNullOrEmpty() && !password.isNullOrEmpty()
        }
    }

    /**
     * 测试WebDAV连接
     * @return 测试结果，包含成功状态和错误信息
     */
    fun testConnectionWithMessage(): TestResult {
        if (!isConfigured()) {
            return TestResult(false, "WebDAV未配置，请检查URL、用户名和密码")
        }

        try {
            // 确保baseUrl以/结尾
            val testUrl = if (baseUrl!!.endsWith("/")) baseUrl else "$baseUrl/"
            Logger.d("WebDAV: 测试连接URL: " + testUrl)
            Logger.d("WebDAV: 用户名: " + (username ?: "null"))

            // 尝试列出目录
            sardine!!.list(testUrl)
            Logger.d("WebDAV: 连接测试成功，可以访问目录")
            return TestResult(true, "连接成功！")
        } catch (e: java.io.IOException) {
            val errorMsg = e.message
            Logger.e("WebDAV: 连接测试失败: " + errorMsg)
            Logger.e("WebDAV: 异常类型: " + e.javaClass.name)
            Logger.e("WebDAV", e)

            // 根据错误类型提供更详细的提示
            if (errorMsg != null) {
                when {
                    errorMsg.contains("401") || errorMsg.contains("Unauthorized") -> {
                        return TestResult(false, "认证失败：用户名或密码错误，请检查账号密码。\n提示：坚果云需要使用应用密码，不是登录密码")
                    }
                    errorMsg.contains("403") || errorMsg.contains("Forbidden") -> {
                        return TestResult(false, "访问被拒绝：账号可能没有WebDAV权限")
                    }
                    errorMsg.contains("404") || errorMsg.contains("Not Found") -> {
                        return TestResult(false, "URL不存在：请检查WebDAV服务器地址是否正确")
                    }
                    errorMsg.contains("SSL") || errorMsg.contains("Certificate") -> {
                        return TestResult(false, "SSL证书错误：请检查服务器证书是否有效")
                    }
                    errorMsg.contains("timeout") || errorMsg.contains("Timeout") -> {
                        return TestResult(false, "连接超时：请检查网络连接或服务器地址")
                    }
                    errorMsg.contains("UnknownHost") || errorMsg.contains("unreachable") -> {
                        return TestResult(false, "无法连接到服务器：请检查网络连接和服务器地址")
                    }
                }
            }
            return TestResult(false, "连接失败：" + (errorMsg ?: "未知错误"))
        } catch (e: Exception) {
            val errorMsg = e.message
            Logger.e("WebDAV: 连接测试失败: " + errorMsg)
            Logger.e("WebDAV: 异常类型: " + e.javaClass.name)
            Logger.e("WebDAV", e)
            return TestResult(false, "连接失败：" + (errorMsg ?: e.javaClass.simpleName))
        }
    }

    /**
     * 测试WebDAV连接（兼容旧接口）
     */
    fun testConnection(): Boolean {
        return testConnectionWithMessage().success
    }

    /**
     * 测试结果类
     */
    class TestResult(val success: Boolean, val message: String)

    /**
     * 确保目录存在
     */
    @Throws(Exception::class)
    private fun ensureDirectory(path: String) {
        try {
            if (!sardine!!.exists(path)) {
                sardine!!.createDirectory(path)
                Logger.d("WebDAV: 创建目录: $path")
            }
        } catch (e: Exception) {
            Logger.e("WebDAV: 创建目录失败: " + e.message)
            throw e
        }
    }

    /**
     * 获取文件完整URL
     */
    private fun getFileUrl(filename: String): String {
        val url = if (baseUrl!!.endsWith("/")) baseUrl else "$baseUrl/"
        return url + filename
    }

    /**
     * 上传观看记录
     */
    fun uploadHistory(): Boolean {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法上传观看记录")
            return false
        }

        try {
            // 获取所有观看记录 - 使用findAllRecent(0)来获取所有记录（包括旧记录）
            Logger.d("WebDAV: 开始查询数据库中的观看记录...")
            val historyList = AppDatabase.get().historyDao.findAllRecent(0)
            Logger.d("WebDAV: 数据库查询完成，结果: " + (historyList?.size ?: "null") + " 条")

            val list = historyList?.toMutableList() ?: mutableListOf()

            // 修复数据中可能的编码问题（重点修复key中的站点名称部分）
            Logger.d("WebDAV: 开始修复上传数据的编码问题...")
            for (h in list) {
                val originalKey = h.key

                // key格式: 站点key$视频ID$cid，需要单独修复站点key部分
                val fixedKey = fixHistoryKey(originalKey)
                if (originalKey != fixedKey) {
                    Logger.d("WebDAV: 修复key编码: '$originalKey' -> '$fixedKey'")
                    h.key = fixedKey ?: originalKey
                }

                val originalName = h.vodName
                val fixedName = fixEncodingIfNeeded(originalName)
                if (originalName != fixedName) {
                    Logger.d("WebDAV: 修复vodName编码: '$originalName' -> '$fixedName'")
                    h.vodName = fixedName
                }
            }

            Logger.d("WebDAV: 准备上传观看记录，共 " + list.size + " 条")

            // 记录前3条数据的详细信息
            for (i in 0 until Math.min(3, list.size)) {
                val h = list[i]
                Logger.d("WebDAV: 上传记录[$i] key=" + h.key + ", vodName=" + h.vodName)
                // 检查key中的每个字符
                val key = h.key
                val hexDump = StringBuilder()
                for (j in 0 until Math.min(20, key.length)) {
                    hexDump.append(String.format("%04x ", key[j].code))
                }
                Logger.d("WebDAV: key前20字符的Unicode: " + hexDump.toString())
            }

            var json = App.gson().toJson(list)
            if (json.isNullOrEmpty()) {
                Logger.w("WebDAV: JSON数据为空")
                json = "[]" // 确保至少有一个有效的JSON数组
            }

            // 记录JSON的前500个字符
            Logger.d("WebDAV: JSON前500字符: " + json.substring(0, Math.min(500, json.length)))

            // 确保目录存在（如果baseUrl包含子目录）
            if (syncMode == SyncMode.ACCOUNT && !baseUrl.isNullOrEmpty()) {
                try {
                    val dirUrl = if (baseUrl!!.endsWith("/")) baseUrl!! else "$baseUrl/"
                    ensureDirectory(dirUrl)
                } catch (e: Exception) {
                    Logger.w("WebDAV: 创建目录失败，尝试继续上传: " + e.message)
                    // 继续尝试上传，某些WebDAV服务可能不需要预先创建目录
                }
            }

            // 上传文件
            val fileUrl = getFileUrl(HISTORY_FILE)
            Logger.d("WebDAV: 上传文件URL: $fileUrl")
            Logger.d("WebDAV: 上传数据大小: " + json.length + " 字节")

            val data = json.toByteArray(Charsets.UTF_8)

            // 对于坚果云等WebDAV服务，直接上传文件即可（会自动创建文件）
            // 如果文件已存在，会被覆盖
            sardine!!.put(fileUrl, data)

            // 验证上传是否成功：检查文件是否存在
            return if (sardine!!.exists(fileUrl)) {
                Logger.d("WebDAV: 观看记录上传成功，共 " + list.size + " 条，文件已确认存在")
                true
            } else {
                Logger.e("WebDAV: 上传后文件不存在，可能上传失败")
                false
            }
        } catch (e: Exception) {
            Logger.e("WebDAV: 观看记录上传失败: " + e.message)
            Logger.e("WebDAV: 异常类型: " + e.javaClass.name)
            Logger.e("WebDAV", e)
            return false
        }
    }

    /**
     * 下载观看记录
     */
    fun downloadHistory(): Boolean {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法下载观看记录")
            Logger.e("WebDAV: baseUrl=$baseUrl, username=$username")
            return false
        }

        try {
            val fileUrl = getFileUrl(HISTORY_FILE)
            Logger.d("WebDAV: 检查文件是否存在: $fileUrl")

            // 检查文件是否存在
            if (!sardine!!.exists(fileUrl)) {
                Logger.w("WebDAV: 观看记录文件不存在，跳过下载")
                return false
            }

            Logger.d("WebDAV: 文件存在，开始下载")

            // 下载文件（使用循环读取，避免available()不准确的问题）
            val isStream = sardine!!.get(fileUrl)
            val baos = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (isStream.read(buffer).also { bytesRead = it } != -1) {
                baos.write(buffer, 0, bytesRead)
            }
            isStream.close()
            val data = baos.toByteArray()
            baos.close()

            val json = String(data, Charsets.UTF_8)
            if (json.isNullOrEmpty()) {
                Logger.d("WebDAV: 观看记录文件为空")
                return true // 文件存在但为空，也算同步成功
            }

            val listType = object : TypeToken<List<History>>() {}.type
            val remoteHistoryList: List<History>? = App.gson().fromJson(json, listType)

            // 验证数据
            if (remoteHistoryList == null) {
                Logger.e("WebDAV: JSON解析失败，返回null")
                return false
            }

            // 智能合并：比较本地和远程记录，保留较新的
            val localHistoryList = AppDatabase.get().historyDao.findAllRecent(0)
            Logger.d("WebDAV: 本地记录数: " + localHistoryList.size)
            Logger.d("WebDAV: 远程记录数: " + remoteHistoryList.size)

            // 修复远程记录的编码问题和时间戳
            Logger.d("WebDAV: 开始修复远程记录编码和时间戳...")
            val currentTime = System.currentTimeMillis()
            val historyTimeLimit = currentTime - com.fongmi.android.tv.Constant.HISTORY_TIME // 60天前

            for (remote in remoteHistoryList) {
                val originalKey = remote.key
                // 修复key中的站点名称部分
                val fixedKey = fixHistoryKey(originalKey)
                if (originalKey != fixedKey) {
                    Logger.d("WebDAV: 修复远程key: '$originalKey' -> '$fixedKey'")
                    remote.key = fixedKey ?: originalKey
                }

                val originalName = remote.vodName
                val fixedName = fixEncodingIfNeeded(originalName)
                if (originalName != fixedName) {
                    Logger.d("WebDAV: 修复远程vodName: '$originalName' -> '$fixedName'")
                    remote.vodName = fixedName
                }

                // 关键修复：确保createTime在60天内，否则会被过滤掉！
                val remoteCreateTime = remote.createTime
                if (remoteCreateTime < historyTimeLimit) {
                    Logger.d("WebDAV: 修复过期时间戳: " + remote.vodName +
                            " createTime=$remoteCreateTime -> $currentTime" +
                            " (已过期 ${(currentTime - remoteCreateTime) / 86400000} 天)")
                    remote.createTime = currentTime
                }

                // 记录前3条远程数据的详细信息
                if (remoteHistoryList.indexOf(remote) < 3) {
                    Logger.d("WebDAV: 远程记录[" + remoteHistoryList.indexOf(remote) + "]: " +
                            remote.vodName + " (key=" + remote.key +
                            ", cid=" + remote.cid +
                            ", createTime=" + remote.createTime + ")")
                }
            }

            // 修复本地记录的编码问题（重要！）
            Logger.d("WebDAV: 开始修复本地记录编码...")
            for (local in localHistoryList) {
                val originalKey = local.key
                // 修复key中的站点名称部分
                val fixedKey = fixHistoryKey(originalKey)
                if (originalKey != fixedKey) {
                    Logger.d("WebDAV: 修复本地key: '$originalKey' -> '$fixedKey'")
                    local.key = fixedKey ?: originalKey
                }

                // 记录前3条本地数据的详细信息
                if (localHistoryList.indexOf(local) < 3) {
                    Logger.d("WebDAV: 本地记录[" + localHistoryList.indexOf(local) + "]: " +
                            local.vodName + " (key=" + local.key +
                            ", cid=" + local.cid +
                            ", createTime=" + local.createTime + ")")
                }
            }

            // 创建本地记录的映射（key -> History）
            val localMap = HashMap<String, History>()
            for (local in localHistoryList) {
                if (local.key != null) {
                    localMap[local.key] = local
                }
            }
            Logger.d("WebDAV: 本地记录映射大小: " + localMap.size)

            // 合并远程记录
            val toInsert = mutableListOf<History>()
            val toUpdate = mutableListOf<History>()

            Logger.d("WebDAV: 开始合并 " + remoteHistoryList.size + " 条远程记录...")

            for (remote in remoteHistoryList) {
                // 验证远程记录
                if (remote.key.isNullOrEmpty()) {
                    Logger.w("WebDAV: 跳过无效的远程记录（key为空）")
                    continue
                }

                val local = localMap[remote.key]

                if (local == null) {
                    // 本地没有，直接添加
                    Logger.d("WebDAV: 发现新记录: " + remote.vodName + " (key=" + remote.key + ")")
                    toInsert.add(remote)
                } else {
                    Logger.d("WebDAV: 本地已有记录: " + remote.vodName + ", 比较时间 remote=" + remote.createTime + " local=" + local.createTime)

                    // 改进的合并策略：优先保留较新的记录，但也要比较播放进度
                    val remotePos = remote.position
                    val localPos = local.position
                    val remoteTime = remote.createTime
                    val localTime = local.createTime

                    var shouldUpdate = false
                    var reason = ""

                    // 策略1：如果远程时间更新，直接更新
                    if (remoteTime > localTime) {
                        shouldUpdate = true
                        reason = "远程时间更新 ($remoteTime > $localTime)"
                    }
                    // 策略2：如果时间相同或相近（误差1秒内），比较播放进度
                    else if (Math.abs(remoteTime - localTime) <= 1000) {
                        if (remotePos >= 0 && localPos >= 0) {
                            if (remotePos > localPos) {
                                shouldUpdate = true
                                reason = "播放进度更新 ($remotePos > $localPos)"
                            } else {
                                reason = "本地进度更新或相同"
                            }
                        } else if (remotePos >= 0 && localPos < 0) {
                            shouldUpdate = true
                            reason = "远程有有效进度，本地无效"
                        } else {
                            reason = "保留本地"
                        }
                    }
                    // 策略3：即使本地时间更新，如果远程有更大的播放进度，也更新
                    else if (remoteTime < localTime) {
                        if (remotePos >= 0 && localPos >= 0 && remotePos > localPos + 60000) {
                            // 远程进度领先本地超过1分钟，可能是用户在另一台设备继续观看
                            shouldUpdate = true
                            reason = "虽然本地时间更新，但远程进度显著领先 ($remotePos > $localPos)"
                        } else {
                            reason = "本地时间更新 ($localTime > $remoteTime)，保留本地"
                        }
                    }

                    if (shouldUpdate) {
                        Logger.d("WebDAV: → 将更新本地 - $reason")
                        toUpdate.add(remote)
                    } else {
                        Logger.d("WebDAV: → 保留本地 - $reason")
                    }
                }
            }

            Logger.d("WebDAV: 合并完成，待插入 " + toInsert.size + " 条，待更新 " + toUpdate.size + " 条")

            // 执行插入和更新
            if (toInsert.isNotEmpty()) {
                Logger.d("WebDAV: 开始插入 " + toInsert.size + " 条新记录...")
                AppDatabase.get().historyDao.insert(toInsert)
                Logger.d("WebDAV: 新增 " + toInsert.size + " 条观看记录")
                for (h in toInsert) {
                    Logger.d("WebDAV: ✓ 新增 - " + h.vodName + " (cid=" + h.cid + ", key=" + h.key + ")")
                }
            } else {
                Logger.d("WebDAV: 没有需要插入的新记录")
            }

            if (toUpdate.isNotEmpty()) {
                Logger.d("WebDAV: 开始更新 " + toUpdate.size + " 条记录...")
                AppDatabase.get().historyDao.update(toUpdate)
                Logger.d("WebDAV: 更新 " + toUpdate.size + " 条观看记录")
                for (h in toUpdate) {
                    Logger.d("WebDAV: ✓ 更新 - " + h.vodName + " (cid=" + h.cid + ")")
                }
            } else {
                Logger.d("WebDAV: 没有需要更新的记录")
            }

            Logger.d("WebDAV: 观看记录合并完成，远程 " + remoteHistoryList.size + " 条，本地 " + localHistoryList.size + " 条")

            // 验证数据库中的记录总数
            val allInDb = AppDatabase.get().historyDao.findAllRecent(0)
            Logger.d("WebDAV: 数据库中总共有 " + allInDb.size + " 条观看记录")

            // 输出数据库中前5条记录的详细信息
            Logger.d("WebDAV: === 数据库中的记录（前5条）===")
            for (i in 0 until Math.min(5, allInDb.size)) {
                val h = allInDb[i]
                Logger.d("WebDAV: [$i] " + h.vodName +
                        " (key=" + h.key +
                        ", cid=" + h.cid +
                        ", createTime=" + h.createTime + ")")
            }
            Logger.d("WebDAV: =========================")

            // 强制触发UI刷新（即使没有新增或更新，也刷新一次以确保显示）
            Logger.d("WebDAV: 触发UI刷新事件")
            App.post {
                RefreshEvent.history()
                Logger.d("WebDAV: UI刷新事件已发送到主线程")
            }

            return true // 即使远程为空，也算同步成功
        } catch (e: Exception) {
            Logger.e("WebDAV: 观看记录下载失败: " + e.message)
            Logger.e("WebDAV", e)
            return false
        }
    }

    /**
     * 上传设置
     */
    fun uploadSettings(): Boolean {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法上传设置")
            return false
        }

        try {
            // 获取所有设置
            val allPrefs = Prefers.getPrefers().all
            val json = App.gson().toJson(allPrefs)

            // 确保目录存在（如果baseUrl包含子目录）
            if (syncMode == SyncMode.ACCOUNT && !baseUrl.isNullOrEmpty()) {
                try {
                    val dirUrl = if (baseUrl!!.endsWith("/")) baseUrl!! else "$baseUrl/"
                    ensureDirectory(dirUrl)
                } catch (e: Exception) {
                    Logger.w("WebDAV: 创建目录失败，尝试继续上传: " + e.message)
                }
            }

            // 上传文件
            val fileUrl = getFileUrl(SETTINGS_FILE)
            val data = json.toByteArray(Charsets.UTF_8)
            sardine!!.put(fileUrl, data)

            Logger.d("WebDAV: 设置上传成功")
            return true
        } catch (e: Exception) {
            Logger.e("WebDAV: 设置上传失败: " + e.message)
            Logger.e("WebDAV", e)
            return false
        }
    }

    /**
     * 下载设置
     */
    @Suppress("UNCHECKED_CAST")
    fun downloadSettings(): Boolean {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法下载设置")
            return false
        }

        try {
            val fileUrl = getFileUrl(SETTINGS_FILE)

            // 检查文件是否存在
            if (!sardine!!.exists(fileUrl)) {
                Logger.d("WebDAV: 设置文件不存在，跳过下载")
                return false
            }

            // 下载文件
            val isStream = sardine!!.get(fileUrl)
            val buffer = ByteArray(isStream.available())
            isStream.read(buffer)
            isStream.close()

            val json = String(buffer, Charsets.UTF_8)
            val gson = App.gson()
            val settings = gson.fromJson(json, Map::class.java) as? Map<String, Any>

            // 应用设置（合并，不覆盖已存在的）
            if (settings != null && settings.isNotEmpty()) {
                for ((key, value) in settings) {
                    // 只同步非敏感设置，跳过某些本地设置
                    if (!shouldSkipSetting(key)) {
                        Prefers.put(key, value)
                    }
                }
                Logger.d("WebDAV: 设置下载成功")
                return true
            }

            return false
        } catch (e: Exception) {
            Logger.e("WebDAV: 设置下载失败: " + e.message)
            Logger.e("WebDAV", e)
            return false
        }
    }

    /**
     * 判断是否应该跳过某个设置项
     */
    private fun shouldSkipSetting(key: String): Boolean {
        // 跳过WebDAV相关设置，避免循环同步
        if (key.startsWith("webdav_")) {
            return true
        }
        // 跳过设备特定设置
        if (key == "device_uuid" || key == "device_name") {
            return true
        }
        return false
    }

    /**
     * 上传完整备份（包含所有数据）
     */
    fun uploadBackup(): Boolean {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法上传备份")
            return false
        }

        try {
            val backup = Backup.create()
            val json = backup.toString()

            // 确保目录存在（如果baseUrl包含子目录）
            if (syncMode == SyncMode.ACCOUNT && !baseUrl.isNullOrEmpty()) {
                try {
                    val dirUrl = if (baseUrl!!.endsWith("/")) baseUrl!! else "$baseUrl/"
                    ensureDirectory(dirUrl)
                } catch (e: Exception) {
                    Logger.w("WebDAV: 创建目录失败，尝试继续上传: " + e.message)
                }
            }

            // 上传文件
            val fileUrl = getFileUrl(BACKUP_FILE)
            val data = json.toByteArray(Charsets.UTF_8)
            sardine!!.put(fileUrl, data)

            Logger.d("WebDAV: 完整备份上传成功")
            return true
        } catch (e: Exception) {
            Logger.e("WebDAV: 完整备份上传失败: " + e.message)
            Logger.e("WebDAV", e)
            return false
        }
    }

    /**
     * 下载完整备份
     */
    fun downloadBackup(): Boolean {
        if (!isConfigured()) {
            Logger.e("WebDAV: 未配置，无法下载备份")
            return false
        }

        try {
            val fileUrl = getFileUrl(BACKUP_FILE)

            // 检查文件是否存在
            if (!sardine!!.exists(fileUrl)) {
                Logger.d("WebDAV: 备份文件不存在，跳过下载")
                return false
            }

            // 下载文件
            val isStream = sardine!!.get(fileUrl)
            val buffer = ByteArray(isStream.available())
            isStream.read(buffer)
            isStream.close()

            val json = String(buffer, Charsets.UTF_8)
            val backup = Backup.objectFrom(json)

            // 恢复备份
            if (backup.config?.isNotEmpty() == true) {
                backup.restore()
                Logger.d("WebDAV: 完整备份下载并恢复成功")
                return true
            }

            return false
        } catch (e: Exception) {
            Logger.e("WebDAV: 完整备份下载失败: " + e.message)
            Logger.e("WebDAV", e)
            return false
        }
    }

    /**
     * 同步观看记录（上传+下载合并）
     * @param async 是否异步执行，true=异步，false=同步（阻塞）
     */
    fun syncHistory(async: Boolean): Boolean {
        if (!isConfigured()) {
            return false
        }

        // 防止重复同步
        if (isSyncing) {
            Logger.w("WebDAV: 同步正在进行中，跳过本次请求")
            return false
        }

        val syncTask = Runnable {
            try {
                isSyncing = true
                // 先上传本地记录
                uploadHistory()
                // 再下载远程记录并合并
                downloadHistory()
            } finally {
                isSyncing = false
            }
        }

        if (async) {
            App.execute(syncTask)
        } else {
            syncTask.run()
        }

        return true
    }

    /**
     * 同步观看记录（异步执行，默认）
     */
    fun syncHistory(): Boolean {
        return syncHistory(true)
    }

    /**
     * 同步设置（上传+下载合并）
     * @param async 是否异步执行
     */
    fun syncSettings(async: Boolean): Boolean {
        if (!isConfigured()) {
            return false
        }

        val syncTask = Runnable {
            // 先上传本地设置
            uploadSettings()
            // 再下载远程设置并合并
            downloadSettings()
        }

        if (async) {
            App.execute(syncTask)
        } else {
            syncTask.run()
        }

        return true
    }

    /**
     * 同步设置（异步执行，默认）
     */
    fun syncSettings(): Boolean {
        return syncSettings(true)
    }

    /**
     * 完整同步（观看记录+设置）
     * @param async 是否异步执行
     */
    fun syncAll(async: Boolean): Boolean {
        if (!isConfigured()) {
            return false
        }

        // 防止重复同步
        if (isSyncing) {
            Logger.w("WebDAV: 同步正在进行中，跳过本次请求")
            return false
        }

        val syncTask = Runnable {
            try {
                isSyncing = true
                // 先上传本地记录
                uploadHistory()
                // 再下载远程记录并合并
                downloadHistory()
                // 同步设置
                syncSettings(false) // 设置同步使用同步方式，避免嵌套异步
            } finally {
                isSyncing = false
            }
        }

        if (async) {
            App.execute(syncTask)
        } else {
            syncTask.run()
        }

        return true
    }

    /**
     * 完整同步（异步执行，默认）
     */
    fun syncAll(): Boolean {
        return syncAll(true)
    }

    /**
     * 重新加载配置（配置更改后调用）
     */
    fun reloadConfig() {
        loadConfig()
    }

    /**
     * 修复History的key中的站点名称编码
     * key格式: 站点key$视频ID$cid
     */
    private fun fixHistoryKey(key: String?): String? {
        if (key.isNullOrEmpty()) {
            return key
        }

        try {
            // 使用AppDatabase.SYMBOL分隔
            val symbol = com.fongmi.android.tv.db.AppDatabase.SYMBOL
            val parts = key.split(java.util.regex.Pattern.quote(symbol).toRegex())

            if (parts.size >= 3) {
                // parts[0] = 站点key, parts[1] = 视频ID, parts[2] = cid
                val siteKey = parts[0]
                val fixedSiteKey = fixEncodingIfNeeded(siteKey)

                if (fixedSiteKey != null && siteKey != fixedSiteKey) {
                    // 重新组装key
                    val newKey = StringBuilder(fixedSiteKey)
                    for (i in 1 until parts.size) {
                        newKey.append(symbol).append(parts[i])
                    }
                    return newKey.toString()
                }
            }
        } catch (e: Exception) {
            Logger.e("WebDAV: 修复History key失败: " + e.message)
        }

        return key
    }

    /**
     * 修复字符串编码问题
     * 尝试将错误编码的UTF-8字符串修复为正确的UTF-8
     */
    private fun fixEncodingIfNeeded(str: String?): String? {
        if (str.isNullOrEmpty()) {
            return str
        }

        try {
            // 检查字符串中是否包含明显的乱码特征
            // 1. 包含替换字符 U+FFFD
            // 2. 包含异常的低位控制字符
            var needsFix = false
            for (i in str.indices) {
                val c = str[i]
                if (c == '\uFFFD' || (c in '\u0080'..'\u009F')) {
                    needsFix = true
                    break
                }
            }

            if (needsFix) {
                // 尝试修复：假设原始数据是UTF-8，但被错误地当作ISO-8859-1解码
                val bytes = str.toByteArray(java.nio.charset.StandardCharsets.ISO_8859_1)
                val fixed = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                Logger.d("WebDAV: 编码修复 '$str' -> '$fixed'")
                return fixed
            }
        } catch (e: Exception) {
            Logger.e("WebDAV: 编码修复失败: " + e.message)
        }

        return str
    }
}
