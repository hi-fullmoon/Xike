package com.xike.app.ai

import androidx.compose.runtime.staticCompositionLocalOf
import com.xike.app.tr
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

internal val LocalAiReviewOperations = staticCompositionLocalOf<AiReviewOperations?> { null }

/** No logging, redirects, automatic retries, cookies, disk cache, or background work. */
internal class AiReviewOperations(private val store: AiSettingsStore) {
    private val writes = Mutex()
    private val client by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .addNetworkInterceptor { chain ->
                // Also prevent HTTP follow-ups (for example 503 Retry-After: 0) from
                // resending a billable POST even if the HTTP client considers them retryable.
                val attempted = requireNotNull(chain.request().tag(AtomicBoolean::class.java))
                if (!attempted.compareAndSet(false, true)) throw IOException("Request already sent")
                chain.proceed(chain.request())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    suspend fun load(): AiSettings = withContext(Dispatchers.IO) { store.read() }

    suspend fun save(settings: AiSettings) = writes.withLock {
        safe { withContext(Dispatchers.IO) { store.save(settings) } }
    }

    suspend fun test(settings: AiSettings): Result<Unit> = safe {
        require(settings.configured && settings.consent)
        completion(settings, aiRequest(settings, null))
        Unit
    }

    suspend fun analyze(
        settings: AiSettings,
        input: AiReviewInput,
        request: String,
    ): Result<AiReviewResult> = safe {
        if (!settings.canUse || settings != load()) {
            throw AiServiceException(tr("AI 配置已改变，请重新预览。", "AI settings changed. Preview again."))
        }
        withContext(Dispatchers.Default) { check(request == aiRequest(settings, input)) }
        val content = completion(settings, request)
        try {
            withContext(Dispatchers.Default) { parseAiReview(content, input) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw AiServiceException(
                tr("返回的分析格式或引用无效，请手动重试。", "Invalid review format or citations. Retry manually.")
            )
        }
    }

    private suspend fun <T> safe(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: AiServiceException) {
            Result.failure(error)
        } catch (_: Exception) {
            // Never expose provider bodies, URLs, credentials, or journal text through exceptions.
            Result.failure(
                AiServiceException(
                    tr(
                        "操作失败，请检查配置和网络后重试。",
                        "Operation failed. Check settings and network, then retry.",
                    )
                )
            )
        }

    private suspend fun completion(settings: AiSettings, body: String): String {
        val request =
            Request.Builder()
                .url(requireNotNull(settings.endpoint()))
                .header("Authorization", "Bearer ${settings.apiKey}")
                .tag(AtomicBoolean::class.java, AtomicBoolean(false))
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        val raw =
            suspendCancellableCoroutine<String> { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (continuation.isActive)
                                continuation.resumeWithException(
                                    AiServiceException(
                                        tr(
                                            "连接失败或请求超时，请检查网络后手动重试。",
                                            "Connection failed or timed out. Check your network and retry manually.",
                                        )
                                    )
                                )
                        }

                        override fun onResponse(call: Call, response: Response) {
                            try {
                                val text = response.use {
                                    if (!it.isSuccessful)
                                        throw AiServiceException(aiHttpError(it.code))
                                    val source = it.body?.source() ?: throw IOException()
                                    // request(n) bounds buffering even for chunked or compressed
                                    // responses.
                                    if (source.request(256L * 1024 + 1))
                                        throw AiServiceException(
                                            tr(
                                                "服务返回内容过大，请手动重试。",
                                                "The service response is too large. Retry manually.",
                                            )
                                        )
                                    source.readUtf8()
                                }
                                if (continuation.isActive) continuation.resume(text)
                            } catch (error: Exception) {
                                if (continuation.isActive)
                                    continuation.resumeWithException(
                                        if (error is AiServiceException) error
                                        else
                                            AiServiceException(
                                                tr(
                                                    "读取服务响应失败，请手动重试。",
                                                    "Unable to read the service response. Retry manually.",
                                                )
                                            )
                                    )
                            }
                        }
                    }
                )
            }
        return withContext(Dispatchers.Default) {
            try {
                val choice = JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                if (choice.optString("finish_reason") != "stop")
                    throw AiServiceException(
                        tr(
                            "服务未返回完整结果，请缩短周期或检查模型配置。",
                            "The result is incomplete. Shorten the period or check the model.",
                        )
                    )
                choice.getJSONObject("message").getString("content").also {
                    require(it.isNotBlank())
                }
            } catch (error: AiServiceException) {
                throw error
            } catch (_: Exception) {
                throw AiServiceException(
                    tr(
                        "接口响应无效，请使用兼容 Chat Completions 的接口。",
                        "Invalid response. Use a Chat Completions compatible endpoint.",
                    )
                )
            }
        }
    }
}

internal class AiServiceException(message: String) : IllegalStateException(message)

internal fun aiHttpError(code: Int): String =
    when (code) {
        401,
        403 -> tr("服务拒绝访问，请检查 Key 和模型权限。", "Access denied. Check the Key and model permissions.")
        402,
        429 ->
            tr("额度不足或请求过于频繁，请稍后手动重试。", "Insufficient quota or rate limited. Retry manually later.")
        in 300..399 ->
            tr("接口返回重定向，请填写最终 HTTPS 地址。", "The endpoint redirected. Enter the final HTTPS address.")
        400,
        404,
        422 ->
            tr(
                "接口地址、模型或请求范围不被支持，请检查配置或缩短周期。",
                "Unsupported endpoint, model, or request size. Check settings or shorten the period.",
            )
        else ->
            tr(
                "AI 服务暂时不可用（HTTP $code），请稍后手动重试。",
                "AI service unavailable (HTTP $code). Retry manually later.",
            )
    }
