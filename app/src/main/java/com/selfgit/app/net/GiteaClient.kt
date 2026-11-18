package com.selfgit.app.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/** 异常类别（对应设计文档 §9 异常处理矩阵）。 */
enum class ErrKind { CONNECTION, TLS, AUTH, FORBIDDEN, SERVER, NOT_FOUND, OTHER }

class ApiException(val kind: ErrKind, message: String) : Exception(message)

data class GiteaUser(val login: String, val displayName: String)

data class GiteaRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: String,
    val description: String,
    val isPrivate: Boolean,
    val updatedAt: String,
    val avatarUrl: String = ""   // 网页端自定义仓库头像（应用详情图标回退源）
)

data class GiteaAsset(val id: Long, val name: String, val size: Long)

/** git tag 存在性（上传 tag 查重用）。 */
data class GiteaTag(val name: String)

/** 仓库 README（默认分支根目录，内容已解码为文本）。 */
data class GiteaReadme(val name: String, val path: String, val content: String)

data class GiteaRelease(
    val id: Long,
    val tagName: String,
    val name: String,
    val body: String,
    val createdAt: String,
    val assets: List<GiteaAsset>
)

/**
 * Gitea API 客户端（阶段 0 子集）：
 * 免认证探测、令牌自校验、密码引导建令牌、仓库列表、Release 列表。
 *
 * 令牌注入统一走 header（Authorization: token …）；
 * 密码引导仅在 createToken 内部以 Basic Auth 一次性使用，不落盘。
 */
class GiteaClient {

    private val base: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ---- 错误分类 ----

    private fun classify(e: IOException): ErrKind = when (e) {
        is SSLHandshakeException, is SSLException -> ErrKind.TLS
        is UnknownHostException -> ErrKind.CONNECTION
        else -> ErrKind.CONNECTION
    }

    private fun httpError(code: Int, body: String): ApiException {
        val detail = runCatching { JSONObject(body).optString("message") }
            .getOrNull().takeUnless { it.isNullOrEmpty() } ?: body.take(180)
        return when (code) {
            401 -> ApiException(ErrKind.AUTH, "认证失败（401）：$detail")
            403 -> ApiException(ErrKind.FORBIDDEN, "权限不足（403）：$detail")
            404 -> ApiException(ErrKind.NOT_FOUND, "资源不存在（404）：$detail")
            in 500..599 -> ApiException(ErrKind.SERVER, "服务端错误（$code）：$detail")
            else -> ApiException(ErrKind.OTHER, "请求失败（$code）：$detail")
        }
    }

    private inline fun <T> call(request: Request, parse: (String) -> T): T {
        val response = try {
            base.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException(classify(e), when (e) {
                is SSLHandshakeException -> "TLS 握手失败：证书不受信任（仅支持系统信任证书，如 Let's Encrypt）"
                is SSLException -> "TLS 失败：${e.message ?: "未知"}"
                else -> "主机不可达：${e.message ?: "网络错误"}"
            })
        }
        response.use { resp ->
            // body 只能消费一次：先读成字符串，再把字符串交给解析
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code, bodyStr)
            return try {
                parse(bodyStr)
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                throw ApiException(ErrKind.OTHER, "响应解析失败：${e.message ?: "格式异常"}")
            }
        }
    }

    // ---- API ----

    /** 免认证轻量探测（§5.2），返回 Gitea 版本号。 */
    fun probe(url: String): String {
        val client = base.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url("$url/api/v1/version").build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException(classify(e), when (e) {
                is SSLHandshakeException -> "TLS 握手失败：证书不受信任（仅支持系统信任证书，如 Let's Encrypt）"
                is SSLException -> "TLS 失败：${e.message ?: "未知"}"
                else -> "主机不可达：${e.message ?: "网络错误"}"
            })
        }
        response.use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code, bodyStr)
            return JSONObject(bodyStr).optString("version", "unknown")
        }
    }

    /** 令牌自校验：GET /api/v1/user。 */
    fun verifyToken(url: String, token: String): GiteaUser {
        val request = Request.Builder()
            .url("$url/api/v1/user")
            .header("Authorization", "token $token")
            .build()
        return call(request) { bodyStr ->
            val obj = JSONObject(bodyStr)
            GiteaUser(
                login = obj.optString("login"),
                displayName = obj.optString("full_name").ifEmpty { obj.optString("login") }
            )
        }
    }

    /**
     * 密码引导：Basic Auth 一次性创建访问令牌（§3.2）。
     * 返回令牌明文（sha1）。401/403 视为 AUTH——可能是密码错误，也可能账号开了 2FA。
     */
    fun createToken(
        url: String,
        username: String,
        password: String,
        tokenName: String,
        scopes: List<String>
    ): String {
        val body = JSONObject().apply {
            put("name", tokenName)
            put("scopes", JSONArray(scopes))
        }.toString().toRequestBody(jsonType)

        val credentials = okhttp3.Credentials.basic(username, password)
        val request = Request.Builder()
            .url("$url/api/v1/users/$username/tokens")
            .header("Authorization", credentials)
            .post(body)
            .build()
        return call(request) { bodyStr ->
            val obj = JSONObject(bodyStr)
            obj.optString("sha1").ifEmpty {
                throw ApiException(ErrKind.OTHER, "令牌创建响应异常：缺少 sha1 字段")
            }
        }
    }

    /** 令牌重名时 Gitea 报 422/409——引导流程据此自动加后缀重试。 */
    fun isTokenNameConflict(e: ApiException): Boolean =
        e.message?.contains("already exists", ignoreCase = true) == true ||
            e.message?.contains("422") == true || e.message?.contains("409") == true

    /** 我的仓库全量列表（分页拉完）。 */
    fun listMyRepos(url: String, token: String): List<GiteaRepo> {
        val result = mutableListOf<GiteaRepo>()
        var page = 1
        while (true) {
            val request = Request.Builder()
                .url("$url/api/v1/user/repos?limit=50&page=$page")
                .header("Authorization", "token $token")
                .build()
            val batch = call(request) { bodyStr ->
                val arr = JSONArray(bodyStr)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    GiteaRepo(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        fullName = o.getString("full_name"),
                        owner = o.getJSONObject("owner").optString("login"),
                        description = o.optString("description"),
                        isPrivate = o.optBoolean("private", false),
                        updatedAt = o.optString("updated_at"),
                        avatarUrl = o.optString("avatar_url")
                    )
                }
            }
            result += batch
            if (batch.size < 50) break
            page++
        }
        return result
    }

    /** 仓库的 Release 列表（含附件与修改日志 body）。 */
    fun listReleases(url: String, token: String, owner: String, repo: String): List<GiteaRelease> {
        val request = Request.Builder()
            .url("$url/api/v1/repos/$owner/$repo/releases?limit=50")
            .header("Authorization", "token $token")
            .build()
        return call(request) { bodyStr ->
            val arr = JSONArray(bodyStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val assetsJson = o.optJSONArray("assets") ?: JSONArray()
                GiteaRelease(
                    id = o.getLong("id"),
                    tagName = o.optString("tag_name"),
                    name = o.optString("name").ifEmpty { o.optString("tag_name") },
                    body = o.optString("body"),
                    createdAt = o.optString("created_at"),
                    assets = (0 until assetsJson.length()).map { j ->
                        val a = assetsJson.getJSONObject(j)
                        GiteaAsset(
                            id = a.getLong("id"),
                            name = a.optString("name"),
                            size = a.optLong("size")
                        )
                    }
                )
            }
        }
    }

    /** 最新 Release（追踪角标用；无任何 Release 时返回 null）。 */
    fun latestRelease(url: String, token: String, owner: String, repo: String): GiteaRelease? {
        val request = Request.Builder()
            .url("$url/api/v1/repos/$owner/$repo/releases/latest")
            .header("Authorization", "token $token")
            .build()
        return try {
            call(request) { bodyStr ->
                val o = JSONObject(bodyStr)
                val assetsJson = o.optJSONArray("assets") ?: JSONArray()
                GiteaRelease(
                    id = o.getLong("id"),
                    tagName = o.optString("tag_name"),
                    name = o.optString("name").ifEmpty { o.optString("tag_name") },
                    body = "",
                    createdAt = o.optString("created_at"),
                    assets = (0 until assetsJson.length()).map { j ->
                        val a = assetsJson.getJSONObject(j)
                        GiteaAsset(a.getLong("id"), a.optString("name"), a.optLong("size"))
                    }
                )
            }
        } catch (e: ApiException) {
            // 404 = 该仓库尚无 Release，属正常业务态，不算错误
            if (e.kind == ErrKind.NOT_FOUND) null else throw e
        }
    }

    /**
     * 附件下载（流式落盘 + 进度回调）。
     * 注意：Gitea 附件 API 端点返回 JSON 元数据而非原始文件——
     * 需先取元数据中的 browser_download_url（/attachments/{uuid}），再下载该地址。
     * dest 必须在 App 专属目录（免存储权限）。失败时删除半成品文件。
     */
    fun downloadAsset(
        url: String,
        token: String,
        owner: String,
        repo: String,
        releaseId: Long,
        assetId: Long,
        dest: java.io.File,
        onProgress: (received: Long, total: Long) -> Unit
    ) {
        // 第一步：取附件元数据，拿到真实下载地址
        val metaRequest = Request.Builder()
            .url("$url/api/v1/repos/$owner/$repo/releases/$releaseId/assets/$assetId")
            .header("Authorization", "token $token")
            .build()
        val downloadUrl = call(metaRequest) { bodyStr ->
            JSONObject(bodyStr).optString("browser_download_url").ifEmpty {
                throw ApiException(ErrKind.OTHER, "附件元数据缺少下载地址")
            }
        }

        // 第二步：下载原始文件（私库附件同样需要令牌）
        val request = Request.Builder()
            .url(downloadUrl)
            .header("Authorization", "token $token")
            .build()
        try {
            base.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw httpError(resp.code, resp.body?.string().orEmpty())
                }
                val src = resp.body ?: throw ApiException(ErrKind.OTHER, "附件响应为空")
                val total = src.contentLength()
                dest.parentFile?.mkdirs()
                var received = 0L
                src.byteStream().use { input ->
                    java.io.FileOutputStream(dest).use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            received += n
                            if (total > 0) onProgress(received, total)
                        }
                        out.flush()
                    }
                }
                if (total > 0 && received < total) {
                    throw ApiException(ErrKind.CONNECTION, "下载不完整：$received / $total")
                }
                onProgress(received, if (total > 0) total else received)
            }
        } catch (e: Exception) {
            dest.delete()
            if (e is ApiException) throw e
            throw ApiException(
                classify(e as? IOException ?: IOException(e.message ?: "unknown")),
                "下载失败：${e.message ?: "未知错误"}"
            )
        }
    }

    // ---- 阶段 2：上传与版本库 ----

    /**
     * 仓库 README（默认分支根目录，§UI 应用详情板块）。
     * 注：Gitea 28.x 的 /readme 端点对部分仓库异常返回 404（实测 28.3.0），
     * 故改走 contents 端点：先列根目录匹配 readme 文件名，再取其 base64 内容解码。
     * 无 README 返回 null。
     */
    fun getReadme(url: String, token: String, owner: String, repo: String): GiteaReadme? {
        val auth = "token $token"

        // 第一步：根目录文件列表中匹配 readme（不区分大小写与扩展名）
        val listRequest = Request.Builder()
            .url("$url/api/v1/repos/$owner/$repo/contents")
            .header("Authorization", auth)
            .build()
        val readmeName = try {
            call(listRequest) { bodyStr ->
                val arr = JSONArray(bodyStr)
                (0 until arr.length())
                    .map { idx -> arr.getJSONObject(idx).optString("name") }
                    .firstOrNull { it.startsWith("readme", ignoreCase = true) }
            } ?: return null
        } catch (e: ApiException) {
            if (e.kind == ErrKind.NOT_FOUND) return null else throw e
        }

        // 第二步：取该文件内容（base64 → UTF-8）
        val encoded = java.net.URLEncoder.encode(readmeName, "UTF-8")
        val fileRequest = Request.Builder()
            .url("$url/api/v1/repos/$owner/$repo/contents/$encoded")
            .header("Authorization", auth)
            .build()
        return call(fileRequest) { bodyStr ->
            val o = JSONObject(bodyStr)
            val raw = o.optString("content").replace("\n", "").replace("\r", "")
            val text = if (raw.isNotEmpty()) {
                String(android.util.Base64.decode(raw, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } else ""
            GiteaReadme(name = readmeName, path = o.optString("path").ifEmpty { readmeName }, content = text)
        }
    }

    /** tag 查重：存在返回 GiteaTag，不存在（404）返回 null。 */
    fun getTag(url: String, token: String, owner: String, repo: String, tag: String): GiteaTag? {
        val encoded = java.net.URLEncoder.encode(tag, "UTF-8")
        val request = Request.Builder()
            .url("$url/api/v1/repos/$owner/$repo/tags/$encoded")
            .header("Authorization", "token $token")
            .build()
        return try {
            call(request) { bodyStr -> GiteaTag(JSONObject(bodyStr).optString("name").ifEmpty { tag }) }
        } catch (e: ApiException) {
            if (e.kind == ErrKind.NOT_FOUND) null else throw e
        }
    }

    /**
     * 创建 Release（tag 不存在时 Gitea 自动在默认分支打 tag）。
     * 空仓库（无任何提交）会失败——错误信息透传给上传页提示。
     */
    fun createRelease(
        url: String,
        token: String,
        owner: String,
        repo: String,
        tagName: String,
        name: String,
        body: String
    ): GiteaRelease {
        val payload = JSONObject().apply {
            put("tag_name", tagName)
            put("name", name)
            put("body", body)
        }.toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url("$url/api/v1/repos/$owner/$repo/releases")
            .header("Authorization", "token $token")
            .post(payload)
            .build()
        return call(request) { bodyStr ->
            val o = JSONObject(bodyStr)
            GiteaRelease(
                id = o.getLong("id"),
                tagName = o.optString("tag_name"),
                name = o.optString("name").ifEmpty { tagName },
                body = o.optString("body"),
                createdAt = o.optString("created_at"),
                assets = emptyList()
            )
        }
    }

    /**
     * 上传 APK 附件（multipart，进度回调）。
     * 走 10 分钟读超时的独立 client；失败时由调用方决定重试与清理。
     */
    fun uploadAsset(
        url: String,
        token: String,
        owner: String,
        repo: String,
        releaseId: Long,
        file: java.io.File,
        onProgress: (sent: Long, total: Long) -> Unit,
        onCall: (okhttp3.Call) -> Unit = {}
    ): GiteaAsset {
        val uploadClient = base.newBuilder()
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .build()

        val fileBody = file.asRequestBody("application/vnd.android.package-archive".toMediaType())
        val counted = object : RequestBody() {
            override fun contentType() = fileBody.contentType()
            override fun contentLength() = file.length()
            override fun writeTo(sink: okio.BufferedSink) {
                val total = file.length()
                var sent = 0L
                file.inputStream().use { input ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        sent += n
                        onProgress(sent, total)
                    }
                    sink.flush()
                }
                if (sent < total) throw IOException("附件上传不完整：$sent / $total")
            }
        }

        val fileName = java.net.URLEncoder.encode(file.name, "UTF-8")
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("attachment", file.name, counted)
            .build()

        val request = Request.Builder()
            .url("$url/api/v1/repos/$owner/$repo/releases/$releaseId/assets?name=$fileName")
            .header("Authorization", "token $token")
            .post(multipart)
            .build()

        return try {
            callOn(uploadClient, request, onCall = onCall) { bodyStr ->
                val o = JSONObject(bodyStr)
                GiteaAsset(
                    id = o.getLong("id"),
                    name = o.optString("name").ifEmpty { file.name },
                    size = o.optLong("size", file.length())
                )
            }
        } catch (e: Exception) {
            if (e is ApiException) throw e
            throw ApiException(
                classify(e as? IOException ?: IOException(e.message ?: "unknown")),
                "附件上传失败：${e.message ?: "未知错误"}"
            )
        }
    }

    /**
     * 创建仓库（上传页"新建仓库"模式）。
     * auto_init=true 由 Gitea 生成初始 README 提交——空仓库无法创建 Release，
     * 初始化后即可直接上传。默认私有。
     */
    fun createRepo(
        url: String,
        token: String,
        name: String,
        description: String,
        isPrivate: Boolean = true
    ): GiteaRepo {
        val payload = JSONObject().apply {
            put("name", name)
            if (description.isNotBlank()) put("description", description)
            put("private", isPrivate)
            put("auto_init", true)
            put("gitignores", "")
            put("license", "")
            put("readme", "Default")
        }.toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url("$url/api/v1/user/repos")
            .header("Authorization", "token $token")
            .post(payload)
            .build()
        return call(request) { bodyStr ->
            val o = JSONObject(bodyStr)
            GiteaRepo(
                id = o.getLong("id"),
                name = o.getString("name"),
                fullName = o.getString("full_name"),
                owner = o.getJSONObject("owner").optString("login"),
                description = o.optString("description"),
                isPrivate = o.optBoolean("private", true),
                updatedAt = o.optString("updated_at"),
                avatarUrl = o.optString("avatar_url")
            )
        }
    }

    /** 删除 Release（上传失败时清理空 Release，P4 第 6 步）。 */
    fun deleteRelease(url: String, token: String, owner: String, repo: String, releaseId: Long) {
        val request = Request.Builder()
            .url("$url/api/v1/repos/$owner/$repo/releases/$releaseId")
            .header("Authorization", "token $token")
            .delete()
            .build()
        call(request) { }
    }

    /**
     * 读取仓库根目录 app.json（已装检测约定文件）的 packageName。
     * 外部安装（非经 SelfGit 下载）的仓库由此关联本机包名；不存在返回 null。
     */
    fun getAppMeta(url: String, token: String, owner: String, repo: String): JSONObject? {
        val request = Request.Builder()
            .url("$url/api/v1/repos/$owner/$repo/contents/app.json")
            .header("Authorization", "token $token")
            .build()
        return try {
            call(request) { bodyStr ->
                val raw = JSONObject(bodyStr).optString("content")
                    .replace("\n", "").replace("\r", "")
                if (raw.isEmpty()) null
                else try {
                    JSONObject(String(android.util.Base64.decode(raw, android.util.Base64.DEFAULT)))
                } catch (_: Exception) { null }
            }
        } catch (e: ApiException) {
            null  // 无 app.json（NOT_FOUND）或读取失败：静默
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 补写仓库 app.json（上传成功后异步调用）：已存在则跳过，避免多余提交。
     * 失败静默——不影响上传结果。
     */
    fun ensureAppMeta(
        url: String,
        token: String,
        owner: String,
        repo: String,
        packageName: String,
        displayName: String
    ) {
        // 已存在则不覆盖（外部可能维护了更完整的信息）
        if (getAppMeta(url, token, owner, repo)?.optString("packageName") != null) return
        val content = android.util.Base64.encodeToString(
            JSONObject().apply {
                put("packageName", packageName)
                if (displayName.isNotBlank()) put("displayName", displayName)
            }.toString().toByteArray(),
            android.util.Base64.NO_WRAP
        )
        val payload = JSONObject().apply {
            put("content", content)
            put("message", "chore: add app.json (SelfGit install tracking)")
        }.toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url("$url/api/v1/repos/$owner/$repo/contents/app.json")
            .header("Authorization", "token $token")
            .put(payload)
            .build()
        try {
            call(request) { }
        } catch (_: Exception) { /* 静默 */ }
    }

    /**
     * 仓库图标远端回退（应用详情板块；本机未安装该应用时使用）：
     * 1) avatar_url——网页端自定义的仓库头像；
     * 2) 仓库根目录约定文件 icon.png / icon.jpg / icon.jpeg / icon.webp。
     * 成功后落盘缓存（owner_repo.img）返回文件；全部失败返回 null（UI 显示占位图标）。
     * 图标获取失败一律静默，不干扰主流程。
     */
    fun getRepoIconFile(
        url: String,
        token: String,
        owner: String,
        repo: String,
        avatarUrl: String?,
        cacheDir: java.io.File
    ): java.io.File? {
        cacheDir.mkdirs()
        val cache = java.io.File(cacheDir, "${owner}_${repo}.img")
        if (cache.exists() && cache.length() > 0) return cache

        // 1) 仓库自定义头像
        if (!avatarUrl.isNullOrBlank()) {
            try {
                val request = Request.Builder()
                    .url(avatarUrl)
                    .header("Authorization", "token $token")
                    .build()
                base.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { input ->
                            cache.outputStream().use { input.copyTo(it) }
                        }
                        if (cache.length() > 0) return cache
                    }
                }
            } catch (_: Exception) { /* 回退到约定文件 */ }
        }

        // 2) 根目录 icon.* 约定文件（contents 两步：列目录 → base64 内容）
        val auth = "token $token"
        return try {
            val listRequest = Request.Builder()
                .url("$url/api/v1/repos/$owner/$repo/contents")
                .header("Authorization", auth)
                .build()
                val iconName = call(listRequest) { bodyStr ->
                    val arr = JSONArray(bodyStr)
                    val iconExts = setOf("png", "jpg", "jpeg", "webp")
                    val names = (0 until arr.length())
                        .map { i -> arr.getJSONObject(i).optString("name") }
                    // 优先 icon.*，回退 logo.*（常见 README 配图即 logo）
                    names.firstOrNull { name ->
                        val ext = name.substringAfterLast('.', "").lowercase()
                        name.startsWith("icon.", ignoreCase = true) && ext in iconExts
                    } ?: names.firstOrNull { name ->
                        val ext = name.substringAfterLast('.', "").lowercase()
                        name.startsWith("logo.", ignoreCase = true) && ext in iconExts
                    }
                } ?: return null

            val encoded = java.net.URLEncoder.encode(iconName, "UTF-8")
            val fileRequest = Request.Builder()
                .url("$url/api/v1/repos/$owner/$repo/contents/$encoded")
                .header("Authorization", auth)
                .build()
            call(fileRequest) { bodyStr ->
                val o = JSONObject(bodyStr)
                val raw = o.optString("content").replace("\n", "").replace("\r", "")
                if (raw.isEmpty()) null
                else {
                    val bytes = android.util.Base64.decode(raw, android.util.Base64.DEFAULT)
                    java.io.FileOutputStream(cache).use { it.write(bytes) }
                    cache
                }
            }
        } catch (e: ApiException) {
            if (e.kind == ErrKind.NOT_FOUND) null else null  // 无 icon 文件或其他错误：静默
        } catch (_: Exception) {
            null
        }
    }

    /** 指定 client 执行请求（上传用长超时 client）；onCall 回调暴露 Call 以支持取消。 */
    private inline fun <T> callOn(
        client: OkHttpClient,
        request: Request,
        onCall: (okhttp3.Call) -> Unit = {},
        parse: (String) -> T
    ): T {
        val call = client.newCall(request)
        onCall(call)
        val response = try {
            call.execute()
        } catch (e: IOException) {
            throw ApiException(classify(e), "上传连接失败：${e.message ?: "网络错误"}")
        }
        response.use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code, bodyStr)
            return try {
                parse(bodyStr)
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                throw ApiException(ErrKind.OTHER, "响应解析失败：${e.message ?: "格式异常"}")
            }
        }
    }
}
