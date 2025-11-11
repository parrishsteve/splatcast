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

    @Throws (TransformerNotFoundException::class, NoSuchElementException::class)
    fun add(
        appId: Long,
        topicId: Long,
        schemaId: Long? = null,
        schemaName: String? = null,
        fromTimestamp: Long? = null,
        session: DefaultWebSocketServerSession): String {
        val id = UUID.randomUUID().toString()
        // The caller could provide both ID and name, but if the ID is provided well that's just faster so prefer that.
        val newSession = when {
            schemaId != null -> subscriberSessionFactory.sessionFactory(
                appId = appId, topicId = topicId, toSchemaId = schemaId, fromTimestamp = fromTimestamp,
                serverSession = session)
            schemaName != null -> subscriberSessionFactory.sessionFactory(
                appId = appId, topicId = topicId, toSchemaName = schemaName, fromTimestamp = fromTimestamp,
                serverSession = session)
            else -> subscriberSessionFactory.sessionFactory(
                appId = appId, topicId = topicId, toSchemaId = null, fromTimestamp = fromTimestamp,
                serverSession = session)
        }
        newSession.start()
        sessions[id] = newSession
        return id
    }

    @Throws (TransformerNotFoundException::class, NoSuchElementException::class)
    fun add(
        appId: Long,
        topicName: String,
        schemaId: Long? = null,
        schemaName: String? = null,
        fromTimestamp: Long? = null,
        session: DefaultWebSocketServerSession): String {
        val id = UUID.randomUUID().toString()
        val newSession = when {
            schemaId != null -> subscriberSessionFactory.sessionFactory(
                appId = appId, topicName = topicName, toSchemaId = schemaId, fromTimestamp = fromTimestamp,
                serverSession = session)
            schemaName != null -> subscriberSessionFactory.sessionFactory(
                appId = appId, topicName = topicName, toSchemaName = schemaName, fromTimestamp = fromTimestamp,
                serverSession = session)
            else -> subscriberSessionFactory.sessionFactory(
                appId = appId, topicName = topicName, toSchemaId = null, fromTimestamp = fromTimestamp,
                serverSession = session)
        }
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
