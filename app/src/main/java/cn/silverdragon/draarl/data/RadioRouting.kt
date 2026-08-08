package cn.silverdragon.draarl.data

data class RadioRouting(
    val txGroupId: Int,
    val rxGroupIds: List<Int>,
) {
    companion object {
        const val MAX_RECEIVE_GROUPS = 16

        fun normalize(txGroupId: Int, rxGroupIds: Collection<Int>): RadioRouting {
            require(txGroupId > 0) { "发送频道无效" }
            val normalized = rxGroupIds.filter { it > 0 }.toMutableSet().apply { add(txGroupId) }.sorted()
            require(normalized.size <= MAX_RECEIVE_GROUPS) { "收听频道不能超过 $MAX_RECEIVE_GROUPS 个" }
            return RadioRouting(txGroupId, normalized)
        }

        fun forTransmitGroupSwitch(
            currentTxGroupId: Int,
            currentRxGroupIds: Collection<Int>,
            nextTxGroupId: Int,
        ): RadioRouting {
            val current = normalize(currentTxGroupId, currentRxGroupIds)
            val nextRxGroupIds = if (current.rxGroupIds == listOf(current.txGroupId)) {
                listOf(nextTxGroupId)
            } else {
                current.rxGroupIds + nextTxGroupId
            }
            return normalize(nextTxGroupId, nextRxGroupIds)
        }
    }
}
