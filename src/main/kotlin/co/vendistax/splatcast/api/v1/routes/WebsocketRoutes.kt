package co.vendistax.splatcast.api.v1.routes

import co.vendistax.splatcast.logging.Logger
import co.vendistax.splatcast.logging.LoggerFactory
import co.vendistax.splatcast.services.AppService
import co.vendistax.splatcast.services.TransformerNotFoundException
import co.vendistax.splatcast.session.SubscriberSessionHub
import co.vendistax.splatcast.validation.validateRequired
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException

private suspend fun DefaultWebSocketServerSession.handleExceptions(
    e: Exception,
    logger: Logger,
    app: String,
    topic: String,
) {
    when (e) {
        is ClosedReceiveChannelException -> {
            logger.debug { "WebSocket closed normally" }
        }
        is TransformerNotFoundException -> {
            this.close(CloseReason(4004, e.message ?: "Transformer not found"))
            logger.error(e, "Transformer not found: app=$app, topic=$topic")
        }
        is IllegalArgumentException -> {
            this.close(CloseReason(4000, e.message ?: "Invalid argument"))
            logger.error(e, "Invalid argument: app=$app, topic=$topic")
        }
        is NoSuchElementException -> {
            this.close(CloseReason(4005, e.message ?: "App or topic not found"))
            logger.error(e, "App or topic not found: app=$app, topic=$topic")
        }
        else -> {
            this.close(CloseReason(1011, "Internal server error"))
            logger.error(e, "WebSocket error: app=$app, topic=$topic")
        }
    }
}

private suspend fun DefaultWebSocketServerSession.handleWebSocketSession(
    appId: Long,
    topicId: Long?,
    topicName: String?,
    targetSchemaId: Long?,
    targetSchemaName: String?,
    subscriberSessionHub: SubscriberSessionHub,
    logger: Logger,
    logAppIdentifier: String,
    logTopicIdentifier: String,
) {
    var sessionId: String? = null
    try {
        sessionId = if (topicId != null) {
            subscriberSessionHub.add(
                appId = appId,
                topicId = topicId,
                schemaId = targetSchemaId,
                schemaName = targetSchemaName,
                session = this
            )
        } else {
            subscriberSessionHub.add(
                appId = appId,
                topicName = topicName!!,
                schemaId = targetSchemaId,
                schemaName = targetSchemaName,
                session = this
            )
        }

        logger.info {
            "WebSocket connected: session=$sessionId, app=$logAppIdentifier, topic=$logTopicIdentifier, " +
                    "schemaId=$targetSchemaId, schemaName=$targetSchemaName"
        }

        for (frame in incoming) {
            when (frame) {
                is Frame.Close -> break
                is Frame.Ping -> send(Frame.Pong(frame.data))
                is Frame.Text -> {
                    val text = frame.readText()
                    logger.warn { "Unexpected message from client: $text" }
                }
                else -> {}
            }
        }
    } catch (e: Exception) {
        this.handleExceptions(e, logger, logAppIdentifier, logTopicIdentifier)
    } finally {
        sessionId?.let(subscriberSessionHub::destroy)
    }
}

fun Route.webSocketRoutes(
    appService: AppService,
    subscriberSessionHub: SubscriberSessionHub,
    logger: Logger = LoggerFactory.getLogger("webSocketRoutes"),
) {
    webSocket("/apps/{appId}/topics/{topicId}/subscribe") {
        val appId = call.parameters["appId"].validateRequired("appId").toLongOrNull()
        val topicId = call.parameters["topicId"].validateRequired("topicId").toLongOrNull()

        if (appId == null || topicId == null) {
            this.close(CloseReason(4000, "Invalid appId or topicId"))
            return@webSocket
        }

        val targetSchemaId = call.request.queryParameters["schemaId"]?.toLongOrNull()
        val targetSchemaName = call.request.queryParameters["schemaName"]

        handleWebSocketSession(
            appId = appId,
            topicId = topicId,
            topicName = null,
            targetSchemaId = targetSchemaId,
            targetSchemaName = targetSchemaName,
            subscriberSessionHub = subscriberSessionHub,
            logger = logger,
            logAppIdentifier = appId.toString(),
            logTopicIdentifier = topicId.toString()
        )
    }

    webSocket("/apps/by-name/{appName}/topics/{topicName}/subscribe") {
        val appName = call.parameters["appName"].validateRequired("appName")
        val topicName = call.parameters["topicName"].validateRequired("topicName")

        val targetSchemaId = call.request.queryParameters["schemaId"]?.toLongOrNull()
        val targetSchemaName = call.request.queryParameters["schemaName"]
        val app = appService.findByName(appName)

        handleWebSocketSession(
            appId = app.appId,
            topicId = null,
            topicName = topicName,
            targetSchemaId = targetSchemaId,
            targetSchemaName = targetSchemaName,
            subscriberSessionHub = subscriberSessionHub,
            logger = logger,
            logAppIdentifier = appName,
            logTopicIdentifier = topicName
        )
    }
}
