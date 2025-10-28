package co.vendistax.splatcast.websocket
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import io.ktor.server.websocket.DefaultWebSocketServerSession
import java.util.concurrent.ConcurrentHashMap

class TopicWebSocketHub(
    private val subscriberSessionFactory: SubscriberSessionFactory,
    private val logger: Logger = LoggerFactory.getLogger<TopicWebSocketHub>(),
) {
    private val sessions = ConcurrentHashMap<String, MutableSet<SubscriberSession>>()

    private fun key(appId: String, topicId: String) = "$appId:$topicId"

    fun add(appId: String, topicId: String, transformerId:  String, session: DefaultWebSocketServerSession) {
        val newSession = subscriberSessionFactory.sessionFactory(
            appId = appId, topicId = topicId, transformerId = transformerId,
            serverSession = session)
        val set = sessions.computeIfAbsent(key(appId, topicId)) { ConcurrentHashMap.newKeySet() }
        set.add(newSession)
        try {
            newSession.start()
        } catch (ex: Throwable) {
            // handle subscription error if needed
            logger.error(ex) { "Session start error" }
        }
    }

    fun destroy(appId: String, topicId: String, session: DefaultWebSocketServerSession) {
        val sessionsForTopic = sessions[key(appId, topicId)]
        if (sessionsForTopic == null) {
            print("No sessions found for appId: $appId, topicId: $topicId")
            return
        }
        val destroySession = sessionsForTopic.find {
            it.serverSession == session
        }
        destroySession?.stop()
        sessionsForTopic.remove(destroySession)
    }
}
