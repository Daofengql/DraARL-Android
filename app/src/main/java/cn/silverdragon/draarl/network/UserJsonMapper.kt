package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.User
import org.json.JSONArray
import org.json.JSONObject

internal class UserJsonMapper(private val currentBaseUrl: () -> String) {
    fun fromJson(json: JSONObject, baseUrl: String = currentBaseUrl()): User {
        val roles = json.opt("roles")
        val role = when (roles) {
            is JSONArray -> roles.optString(0, "user")

            is String -> roles.substringBefore(',').trim().ifBlank { "user" }

            else -> json.optStringClean("role").ifBlank {
                if (json.optBoolean("isAdmin")) "admin" else "user"
            }
        }
        return User(
            id = json.optInt("id"),
            username = json.optStringClean("username"),
            nickname = json.optStringClean("nickname"),
            callsign = json.optStringClean("callsign"),
            email = json.optStringClean("email"),
            emailVerified = json.optBoolean("email_verified"),
            role = role,
            approvalStatus = json.optInt("approval_status"),
            reviewNote = json.optStringClean("review_note"),
            avatarUrl = optionalHttpsUrl(
                json.optStringClean("avatar_thumb").ifBlank { json.optStringClean("avatar") },
                baseUrl
            ),
            address = json.optStringClean("address"),
            phone = json.optStringClean("phone"),
            introduction = json.optStringClean("introduction"),
            dmrId = json.optInt("dmrid"),
            mdcId = json.optStringClean("mdcid"),
            birthday = json.optStringClean("birthday"),
            sex = json.optInt("sex"),
            alarmMsg = json.optBoolean("alarm_msg"),
            lastGroupId = json.optInt("last_group_id", DEFAULT_GROUP_ID).takeIf { it > 0 } ?: DEFAULT_GROUP_ID,
            status = json.optInt("status", 1),
            lastLoginTime = json.optStringClean("last_login_time"),
            lastLoginIp = json.optStringClean("last_login_ip"),
            lastLoginIpLocation = json.optStringClean("last_login_ip_location")
        )
    }
}

private const val DEFAULT_GROUP_ID = 999
