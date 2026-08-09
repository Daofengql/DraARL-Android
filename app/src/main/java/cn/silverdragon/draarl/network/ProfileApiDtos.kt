package cn.silverdragon.draarl.network

import org.json.JSONObject

internal data class UploadedFileDto(val url: String)

internal object ProfileApiResponseMapper {
    fun user(response: JSONObject): UserDto = UserDto.fromJson(response.requireObject("data"))

    fun uploadedFile(response: JSONObject): UploadedFileDto {
        val data = response.optJSONObject("data") ?: response
        return UploadedFileDto(
            data.optStringClean("url")
                .ifBlank { data.optStringClean("file_url") }
                .ifBlank { throw ApiException(HTTP_RESPONSE_MAPPING_ERROR, "服务器响应缺少文件地址") }
        )
    }
}

private const val HTTP_RESPONSE_MAPPING_ERROR = 500
