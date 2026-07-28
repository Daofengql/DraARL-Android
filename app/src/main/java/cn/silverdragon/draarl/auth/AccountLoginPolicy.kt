package cn.silverdragon.draarl.auth

import cn.silverdragon.draarl.data.User

internal fun accountLoginRejection(user: User): String? = when {
    user.status != 1 -> "账号已被封禁，无法登录"
    user.approvalStatus == 0 -> "账号尚未审核，暂时无法登录"
    user.approvalStatus == 2 -> user.reviewNote.trim().takeIf(String::isNotBlank)?.let {
        "账号审核未通过：$it"
    } ?: "账号审核未通过，无法登录"
    user.approvalStatus != 1 -> "账号审核状态异常，暂时无法登录"
    else -> null
}
