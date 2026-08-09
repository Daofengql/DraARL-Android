package cn.silverdragon.draarl.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

internal data class HttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val body: HttpRequestBody? = null,
    val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
    val writeTimeoutMillis: Long = DEFAULT_WRITE_TIMEOUT_MILLIS
)

internal sealed interface HttpRequestBody {
    data class Bytes(val content: ByteArray, val mediaType: String) : HttpRequestBody

    data class Multipart(val parts: List<HttpPart>) : HttpRequestBody
}

internal data class HttpPart(
    val name: String,
    val content: ByteArray,
    val fileName: String? = null,
    val mediaType: String? = null
)

internal data class HttpResponse(val status: Int, val headers: Map<String, List<String>>, val body: ByteArray) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { (key) -> key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()

    fun bodyText(): String = body.toString(Charsets.UTF_8)
}

internal interface HttpCall {
    fun execute(): HttpResponse

    fun cancel()
}

internal interface HttpTransport {
    fun newCall(request: HttpRequest): HttpCall
}

internal class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient(),
    private val allowCleartext: Boolean = false
) : HttpTransport {
    override fun newCall(request: HttpRequest): HttpCall {
        val url = request.url.toHttpUrlOrNull()
            ?: throw HttpTransportException(request, "请求地址无效")
        if (!allowCleartext && !url.isHttps) {
            throw HttpTransportException(request, "请求地址必须使用 HTTPS")
        }
        val callClient = client.newBuilder()
            .connectTimeout(request.connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(request.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(request.writeTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        val requestBody = request.body?.toOkHttpRequestBody()
            ?: request.method.uppercase().takeIf(METHODS_REQUIRING_BODY::contains)?.let { EMPTY_REQUEST_BODY }
        val builder = Request.Builder().url(url)
        request.headers.forEach(builder::header)
        builder.method(request.method.uppercase(), requestBody)
        return OkHttpCall(request, callClient.newCall(builder.build()))
    }
}

internal class HttpTransportException(val request: HttpRequest, message: String, cause: Throwable? = null) :
    IOException("${request.method.uppercase()} ${request.url}: $message", cause)

internal fun HttpTransport.execute(request: HttpRequest): HttpResponse = newCall(request).execute()

internal fun HttpResponse.toApiJson(): JSONObject {
    val text = bodyText()
    val json = if (text.isBlank()) {
        JSONObject()
    } else {
        runCatching { JSONObject(text) }.getOrElse { error ->
            throw ApiException(status, "服务器返回了无法识别的数据", cause = error)
        }
    }
    header("Retry-After")?.trim()?.toIntOrNull()?.takeIf { it > 0 }?.let {
        json.put(HTTP_RETRY_AFTER_SECONDS, it)
    }
    if (!json.has("code")) json.put("code", status)
    if (status !in HTTP_SUCCESS_RANGE && json.optInt("code") < HTTP_ERROR_MIN) json.put("code", status)
    return json
}

private class OkHttpCall(private val request: HttpRequest, private val call: Call) : HttpCall {
    override fun execute(): HttpResponse = try {
        call.execute().use(::readResponse)
    } catch (error: IOException) {
        throw HttpTransportException(request, error.message ?: "网络请求失败", error)
    }

    override fun cancel() = call.cancel()
}

private fun readResponse(response: Response): HttpResponse = HttpResponse(
    status = response.code,
    headers = response.headers.toMultimap(),
    body = response.body?.bytes() ?: ByteArray(0)
)

private fun HttpRequestBody.toOkHttpRequestBody(): RequestBody = when (this) {
    is HttpRequestBody.Bytes -> content.toRequestBody(mediaType.toMediaTypeOrNull())

    is HttpRequestBody.Multipart -> MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .apply {
            parts.forEach { part ->
                if (part.fileName == null) {
                    addFormDataPart(part.name, part.content.toString(Charsets.UTF_8))
                } else {
                    addFormDataPart(
                        part.name,
                        part.fileName,
                        part.content.toRequestBody(part.mediaType?.toMediaTypeOrNull())
                    )
                }
            }
        }
        .build()
}

internal const val HTTP_RETRY_AFTER_SECONDS = "_http_retry_after_seconds"
private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000L
private const val DEFAULT_READ_TIMEOUT_MILLIS = 15_000L
private const val DEFAULT_WRITE_TIMEOUT_MILLIS = 15_000L
private const val HTTP_ERROR_MIN = 400
private const val HTTP_SUCCESS_MAX = 299
private const val HTTP_SUCCESS_MIN = 200
private val EMPTY_REQUEST_BODY = ByteArray(0).toRequestBody(null)
private val HTTP_SUCCESS_RANGE = HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
private val METHODS_REQUIRING_BODY = setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT")
