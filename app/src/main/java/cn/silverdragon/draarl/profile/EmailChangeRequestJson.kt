package cn.silverdragon.draarl.profile

import org.json.JSONObject

internal fun emailChangeRequestJson(
    oldSessionId: String,
    oldCode: String,
    newSessionId: String,
    newCode: String,
): JSONObject = JSONObject()
    .put("old_session_id", oldSessionId)
    .put("old_code", oldCode.trim())
    .put("new_session_id", newSessionId)
    .put("new_code", newCode.trim())
