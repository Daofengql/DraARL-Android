package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.User
import org.json.JSONArray
import org.json.JSONObject

internal class UserJsonMapper(private val currentBaseUrl: () -> String) {
    fun fromJson(json: JSONObject, baseUrl: String = currentBaseUrl()): User = UserDto.fromJson(json).toDomain(baseUrl)

    fun fromDto(dto: UserDto, baseUrl: String = currentBaseUrl()): User = dto.toDomain(baseUrl)
}

internal data class UserDto(
    val id: Int,
    val username: String,
    val nickname: String,
    val callsign: String,
    val email: String,
    val emailVerified: Boolean,
    val role: String,
    val approvalStatus: Int,
    val reviewNote: String,
    val avatar: String,
    val address: String,
    val phone: String,
    val introduction: String,
    val dmrId: Int,
    val mdcId: String,
    val birthday: String,
    val sex: Int,
    val alarmMsg: Boolean,
    val lastGroupId: Int,
    val status: Int,
    val lastLoginTime: String,
    val lastLoginIp: String,
    val lastLoginIpLocation: String
) {
    fun toDomain(baseUrl: String) = User(
        id = id,
        username = username,
        nickname = nickname,
        callsign = callsign,
        email = email,
        emailVerified = emailVerified,
        role = role,
        approvalStatus = approvalStatus,
        reviewNote = reviewNote,
        avatarUrl = optionalHttpsUrl(avatar, baseUrl),
        address = address,
        phone = phone,
        introduction = introduction,
        dmrId = dmrId,
        mdcId = mdcId,
        birthday = birthday,
        sex = sex,
        alarmMsg = alarmMsg,
        lastGroupId = lastGroupId.takeIf { it > 0 } ?: DEFAULT_GROUP_ID,
        status = status,
        lastLoginTime = lastLoginTime,
        lastLoginIp = lastLoginIp,
        lastLoginIpLocation = lastLoginIpLocation
    )

    companion object {
        fun fromJson(json: JSONObject): UserDto = UserDto(
            id = json.requireInt("id"),
            username = json.requireString("username"),
            nickname = json.optStringClean("nickname"),
            callsign = json.optStringClean("callsign"),
            email = json.optStringClean("email"),
            emailVerified = json.optBoolean("email_verified"),
            role = json.userRole(),
            approvalStatus = json.optInt("approval_status"),
            reviewNote = json.optStringClean("review_note"),
            avatar = json.optStringClean("avatar_thumb").ifBlank { json.optStringClean("avatar") },
            address = json.optStringClean("address"),
            phone = json.optStringClean("phone"),
            introduction = json.optStringClean("introduction"),
            dmrId = json.optInt("dmrid"),
            mdcId = json.optStringClean("mdcid"),
            birthday = json.optStringClean("birthday"),
            sex = json.optInt("sex"),
            alarmMsg = json.optBoolean("alarm_msg"),
            lastGroupId = json.optInt("last_group_id", DEFAULT_GROUP_ID),
            status = json.optInt("status", 1),
            lastLoginTime = json.optStringClean("last_login_time"),
            lastLoginIp = json.optStringClean("last_login_ip"),
            lastLoginIpLocation = json.optStringClean("last_login_ip_location")
        )
    }
}

private fun JSONObject.userRole(): String = when (val roles = opt("roles")) {
    is JSONArray -> roles.optString(0, "user")
    is String -> roles.substringBefore(',').trim().ifBlank { "user" }
    else -> optStringClean("role").ifBlank { if (optBoolean("isAdmin")) "admin" else "user" }
}

private const val DEFAULT_GROUP_ID = 999
