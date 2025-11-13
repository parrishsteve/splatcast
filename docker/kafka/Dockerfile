# ---- fetch stage: download Apache Kafka tarball ----
FROM debian:stable-slim AS fetch
SHELL ["/bin/bash","-lc"]
RUN apt-get update && apt-get install -y curl ca-certificates tar && rm -rf /var/lib/apt/lists/*
ARG KAFKA_URL ARG KAFKA_URL_FALLBACK
WORKDIR /tmp/fetch
RUN set -euo pipefail; \
  for u in "$KAFKA_URL" "$KAFKA_URL_FALLBACK"; do \
    echo "Trying $u" && curl -fL "$u" -o kafka.tgz && break || true; \
  done; \
  mkdir -p /opt/kafka && tar -xzf kafka.tgz --strip-components=1 -C /opt/kafka

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-jammy
SHELL ["/bin/bash","-lc"]

# For healthcheck (nc)
RUN apt-get update && apt-get install -y netcat-openbsd && rm -rf /var/lib/apt/lists/*

ARG APP_UID=10001 APP_GID=10001
RUN groupadd -g ${APP_GID} app && useradd -m -u ${APP_UID} -g ${APP_GID} app

ENV KAFKA_HOME=/opt/kafka \
    PATH=/opt/kafka/bin:$PATH \
    KAFKA_HEAP_OPTS="-Xms512m -Xmx512m"

COPY --from=fetch /opt/kafka /opt/kafka

# Include ASF LICENSE/NOTICE (handle with/without .txt)
RUN set -e; \
  mkdir -p /licenses/apache/kafka /data/kafka /var/log/kafka; \
  for f in LICENSE LICENSE.txt; do \
    if [ -f "/opt/kafka/$f" ]; then cp "/opt/kafka/$f" /licenses/apache/kafka/LICENSE; break; fi; \
  done; \
  for f in NOTICE NOTICE.txt; do \
    if [ -f "/opt/kafka/$f" ]; then cp "/opt/kafka/$f" /licenses/apache/kafka/NOTICE; break; fi; \
  done

# Entrypoint renders server.properties from env and starts the broker
COPY <<'SH' /usr/local/bin/start-kafka.sh
#!/usr/bin/env bash
set -euo pipefail

: "${KAFKA_HOME:=/opt/kafka}"
: "${KAFKA_DATA:=/data/kafka}"

# Listener config (expand to strings here)
: "${KAFKA_LISTENERS:=INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:29092}"
: "${KAFKA_ADVERTISED_LISTENERS:=INTERNAL://kafka:9092,EXTERNAL://localhost:29092}"
: "${KAFKA_INTER_BROKER_LISTENER_NAME:=INTERNAL}"
: "${KAFKA_LISTENER_SECURITY_PROTOCOL_MAP:=INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT}"

# Zookeeper + broker id
: "${KAFKA_ZK_CONNECT:=zookeeper:2181}"
: "${BROKER_ID:=1}"

# Single-node numeric defaults (expand to numbers now)
: "${KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR:=1}"
: "${KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR:=1}"
: "${KAFKA_TRANSACTION_STATE_LOG_MIN_ISR:=1}"
: "${KAFKA_NUM_PARTITIONS:=1}"
: "${KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS:=0}"

mkdir -p "${KAFKA_DATA}"

cat > "${KAFKA_HOME}/config/server.properties" <<EOF
broker.id=${BROKER_ID}
log.dirs=${KAFKA_DATA}

listeners=${KAFKA_LISTENERS}
advertised.listeners=${KAFKA_ADVERTISED_LISTENERS}
listener.security.protocol.map=${KAFKA_LISTENER_SECURITY_PROTOCOL_MAP}
inter.broker.listener.name=${KAFKA_INTER_BROKER_LISTENER_NAME}

zookeeper.connect=${KAFKA_ZK_CONNECT}

num.partitions=${KAFKA_NUM_PARTITIONS}
offsets.topic.replication.factor=${KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR}
transaction.state.log.replication.factor=${KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR}
transaction.state.log.min.isr=${KAFKA_TRANSACTION_STATE_LOG_MIN_ISR}
group.initial.rebalance.delay.ms=${KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS}
EOF

exec "${KAFKA_HOME}/bin/kafka-server-start.sh" "${KAFKA_HOME}/config/server.properties"
SH

RUN chmod +x /usr/local/bin/start-kafka.sh \
 && chown -R app:app /opt/kafka /licenses /data /var/log

USER app
EXPOSE 9092 29092

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s \
  CMD bash -lc 'nc -z 127.0.0.1 9092 || exit 1'

ENTRYPOINT ["/usr/local/bin/start-kafka.sh"]


