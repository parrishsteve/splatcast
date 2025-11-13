#!/usr/bin/env bash
set -euo pipefail

: "${KAFKA_HOME:=/opt/kafka}"
: "${KAFKA_DATA:=/data/kafka}"
: "${KAFKA_LISTENERS:=INTERNAL://0.0.0.0:9092}"
: "${KAFKA_ADVERTISED_LISTENERS:=INTERNAL://kafka:9092}"
: "${KAFKA_INTER_BROKER_LISTENER_NAME:=INTERNAL}"
: "${KAFKA_ZK_CONNECT:=zookeeper:2181}"
: "${BROKER_ID:=1}"

mkdir -p "${KAFKA_DATA}"

# Render server.properties from env overrides
cat > "${KAFKA_HOME}/config/server.properties" <<EOF
broker.id=${BROKER_ID}
log.dirs=${KAFKA_DATA}
listeners=${KAFKA_LISTENERS}
advertised.listeners=${KAFKA_ADVERTISED_LISTENERS}
inter.broker.listener.name=${KAFKA_INTER_BROKER_LISTENER_NAME}
zookeeper.connect=${KAFKA_ZK_CONNECT}

num.partitions=1
offsets.topic.replication.factor=\${KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR:-1}
transaction.state.log.replication.factor=\${KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR:-1}
transaction.state.log.min.isr=1
group.initial.rebalance.delay.ms=0
EOF

exec "${KAFKA_HOME}/bin/kafka-server-start.sh" "${KAFKA_HOME}/config/server.properties"
