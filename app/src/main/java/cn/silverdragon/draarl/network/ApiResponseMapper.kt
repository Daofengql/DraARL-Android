package cn.silverdragon.draarl.network

import org.json.JSONException
import org.json.JSONObject

internal inline fun <T> ApiJsonRequester.executeMapped(
    method: String,
    path: String,
    body: JSONObject? = null,
    requiresAuth: Boolean = true,
    mapper: (JSONObject) -> T
): T = decodeApiResponse(method, path, { execute(method, path, body, requiresAuth) }, mapper)

internal inline fun <T> decodeApiResponse(
    method: String,
    path: String,
    request: () -> JSONObject,
    mapper: (JSONObject) -> T
): T {
    val response = validatedApiResponse(method, path, request)
    return mapApiResponse(method, path, response, mapper)
}

private inline fun validatedApiResponse(method: String, path: String, request: () -> JSONObject): JSONObject = try {
    request().requireSuccess()
} catch (error: ApiException) {
    throw error.withRequestContext(method, path, ApiFailureStage.RESPONSE_VALIDATION)
}

private inline fun <T> mapApiResponse(
    method: String,
    path: String,
    response: JSONObject,
    mapper: (JSONObject) -> T
): T = try {
    mapper(response)
} catch (error: ApiException) {
    throw error.withRequestContext(method, path, ApiFailureStage.RESPONSE_MAPPING)
} catch (error: JSONException) {
    throw ApiException(
        code = HTTP_RESPONSE_MAPPING_ERROR,
        message = "服务器响应格式不正确",
        cause = error,
        requestContext = ApiRequestFailureContext(method.uppercase(), path, ApiFailureStage.RESPONSE_MAPPING)
    )
}

internal fun ApiException.withRequestContext(
    method: String,
    path: String,
    defaultStage: ApiFailureStage
): ApiException = if (
    requestContext != null
) {
    this
} else {
    ApiException(
        code = code,
        message = message,
        errorCode = errorCode,
        retryAfterSeconds = retryAfterSeconds,
        cause = this,
        requestContext = ApiRequestFailureContext(method.uppercase(), path, defaultStage)
    )
}

private const val HTTP_RESPONSE_MAPPING_ERROR = 500
