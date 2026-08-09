package cn.silverdragon.draarl.radio.session

import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.SecureSessionStore
import cn.silverdragon.draarl.network.RadioApi

internal interface RadioSessionRemoteDataSource {
    fun loadAccessPoints(): List<AccessPoint>

    fun freshAccessToken(): String

    fun renewAccessToken(): String

    fun updateRouting(sessionId: String, txGroupId: Int, rxGroupIds: Collection<Int>): RadioSessionRoutingResult
}

internal class ApiRadioSessionRemoteDataSource(private val api: RadioApi) : RadioSessionRemoteDataSource {
    override fun loadAccessPoints(): List<AccessPoint> = api.getAccessPoints()

    override fun freshAccessToken(): String = api.freshAccessToken()

    override fun renewAccessToken(): String = api.renewAccessToken()

    override fun updateRouting(
        sessionId: String,
        txGroupId: Int,
        rxGroupIds: Collection<Int>
    ): RadioSessionRoutingResult {
        val session = api.updateRadioSessionRouting(sessionId, txGroupId, rxGroupIds)
        return RadioSessionRoutingResult(
            txGroupId = session.txGroupId,
            rxGroupIds = session.rxGroupIds.toSet() + session.txGroupId
        )
    }
}

internal interface RadioSessionStorage {
    fun clientInstanceId(): String

    fun loadRouting(userId: Int, fallbackGroupId: Int): RadioSessionRoutingResult

    fun saveRouting(userId: Int, txGroupId: Int, rxGroupIds: Collection<Int>)

    fun saveSelectedAccessPoint(id: String)
}

internal class StoredRadioSessionStorage(private val store: SecureSessionStore) : RadioSessionStorage {
    override fun clientInstanceId(): String = store.clientInstanceId()

    override fun loadRouting(userId: Int, fallbackGroupId: Int): RadioSessionRoutingResult {
        val txGroupId = store.selectedGroupId(userId, fallbackGroupId)
        return RadioSessionRoutingResult(
            txGroupId = txGroupId,
            rxGroupIds = store.receiveGroupIds(userId, txGroupId)
        )
    }

    override fun saveRouting(userId: Int, txGroupId: Int, rxGroupIds: Collection<Int>) {
        store.setRadioRouting(userId, txGroupId, rxGroupIds)
    }

    override fun saveSelectedAccessPoint(id: String) {
        store.setSelectedAccessPointId(id)
    }
}
