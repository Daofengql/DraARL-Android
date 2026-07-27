package cn.silverdragon.draarl.ui.screens

internal object RegistrationValidation {
    fun basicInfo(username: String, callsign: String): String? = when {
        username.isBlank() -> "请输入用户名"
        username.length !in 3..20 -> "用户名长度需为 3-20 个字符"
        !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> "用户名只能包含字母、数字和下划线"
        callsign.isBlank() -> "请输入呼号"
        !callsign.matches(Regex("^[A-Za-z][A-Za-z0-9]{2,9}$")) -> "呼号需以字母开头，3-10 个字符"
        else -> null
    }

    fun contactInfo(email: String, phone: String): String? = when {
        email.isBlank() -> "请输入邮箱"
        !email.matches(EMAIL_PATTERN) -> "邮箱格式不正确"
        phone.isNotBlank() && !phone.matches(Regex("^1[3-9]\\d{9}$")) -> "手机号格式不正确"
        else -> null
    }

    fun password(password: String, confirmation: String): String? = when {
        password.length < 6 -> "密码长度至少 6 位"
        password != confirmation -> "两次密码不一致"
        else -> null
    }
}

internal object PasswordResetValidation {
    fun emailStep(email: String, captcha: String): String? = when {
        email.isBlank() -> "请输入邮箱"
        !email.matches(EMAIL_PATTERN) -> "邮箱格式不正确"
        captcha.isBlank() -> "请输入图片验证码"
        else -> null
    }
}

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
