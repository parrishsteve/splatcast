package co.vendistax.splatcast.session
import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.services.TransformerNotFoundException
import io.ktor.server.websocket.DefaultWebSocketServerSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.Throws

class SubscriberSessionHub(
    private val subscriberSessionFactory: SubscriberSessionFactory,
    private val logger: Logger = LoggerFactory.getLogger<SubscriberSessionHub>(),
) {
    private val sessions = ConcurrentHashMap<String, SubscriberSessionInterface>()

    @Throws (TransformerNotFoundException::class)
    fun add(appId: String, topicId: String, transformerId: String, session: DefaultWebSocketServerSession): String {
        val id = UUID.randomUUID().toString()
        val newSession = subscriberSessionFactory.sessionFactory(
            appId = appId.toLong(), topicId = topicId.toLong(), transformerId = transformerId.toLong(),
            serverSession = session)
        newSession.start()
        sessions[id] = newSession
        return id
    }

    fun destroy(sessionId: String) {
        val session = sessions[sessionId]
        if (session == null) {
            logger.error { "No sessions found for id $sessionId" }
            return
        }
        sessions.remove(sessionId)
        session.stop()
    }
}
