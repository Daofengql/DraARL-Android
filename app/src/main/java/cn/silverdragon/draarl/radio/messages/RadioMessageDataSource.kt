package cn.silverdragon.draarl.radio.messages

import cn.silverdragon.draarl.data.ChannelMessageMapper
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageStore
import cn.silverdragon.draarl.data.ServerTimeParser
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.network.RadioApi

internal data class RadioMessagePage(val messages: List<RadioMessage>, val nextCursor: String, val hasMore: Boolean)

internal interface RadioMessageRemoteDataSource {
    fun loadPage(groupId: Int, cursor: String, accountUser: User): RadioMessagePage

    fun loadMessage(groupId: Int, messageId: Int, accountUser: User): RadioMessage

    fun loadPublicProfile(username: String): User
}

internal class ApiRadioMessageRemoteDataSource(private val api: RadioApi) : RadioMessageRemoteDataSource {
    override fun loadPage(groupId: Int, cursor: String, accountUser: User): RadioMessagePage {
        val page = api.getGroupMessages(groupId = groupId, cursor = cursor)
        return RadioMessagePage(
            messages = page.messages.mapNotNull { message ->
                val timestamp = ServerTimeParser.parseMillis(message.sentAt) ?: return@mapNotNull null
                ChannelMessageMapper.toRadioMessage(message, accountUser, timestamp)
            },
            nextCursor = page.nextCursor,
            hasMore = page.hasMore
        )
    }

    override fun loadMessage(groupId: Int, messageId: Int, accountUser: User): RadioMessage {
        val message = api.getGroupMessage(groupId, messageId)
        val timestamp = ServerTimeParser.parseMillis(message.sentAt)
            ?: error("服务器返回了无效的消息时间")
        return ChannelMessageMapper.toRadioMessage(message, accountUser, timestamp)
    }

    override fun loadPublicProfile(username: String): User = api.getPublicUserByName(username)
}

internal interface RadioMessageCache {
    fun generation(): Int

    fun load(accountKey: String, groupId: Int, limit: Int): List<RadioMessage>

    fun save(accountKey: String, groupId: Int, message: RadioMessage, expectedGeneration: Int)

    fun reconcile(
        accountKey: String,
        groupId: Int,
        remoteMessages: List<RadioMessage>,
        authoritativeWindow: LongRange? = null,
        expectedGeneration: Int
    )

    fun markPlayed(accountKey: String, groupId: Int, localId: String, serverRecordId: Int?, expectedGeneration: Int)

    fun markAllPlayed(accountKey: String, groupId: Int, expectedGeneration: Int)
}

internal class StoredRadioMessageCache(private val store: RadioMessageStore) : RadioMessageCache {
    override fun generation(): Int = store.generation()

    override fun load(accountKey: String, groupId: Int, limit: Int): List<RadioMessage> =
        store.load(accountKey, groupId, limit)

    override fun save(accountKey: String, groupId: Int, message: RadioMessage, expectedGeneration: Int) {
        store.save(accountKey, groupId, message, expectedGeneration)
    }

    override fun reconcile(
        accountKey: String,
        groupId: Int,
        remoteMessages: List<RadioMessage>,
        authoritativeWindow: LongRange?,
        expectedGeneration: Int
    ) {
        store.reconcile(accountKey, groupId, remoteMessages, authoritativeWindow, expectedGeneration)
    }

    override fun markPlayed(
        accountKey: String,
        groupId: Int,
        localId: String,
        serverRecordId: Int?,
        expectedGeneration: Int
    ) {
        store.markPlayed(accountKey, groupId, localId, serverRecordId, expectedGeneration)
    }

    override fun markAllPlayed(accountKey: String, groupId: Int, expectedGeneration: Int) {
        store.markAllPlayed(accountKey, groupId, expectedGeneration)
    }
}
