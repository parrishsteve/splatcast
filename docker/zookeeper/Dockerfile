FROM debian:stable-slim AS fetch
SHELL ["/bin/bash","-lc"]
RUN apt-get update && apt-get install -y curl ca-certificates tar && rm -rf /var/lib/apt/lists/*
RUN apt-get update && apt-get install -y netcat-openbsd && rm -rf /var/lib/apt/lists/*
ARG ZK_URL ARG ZK_URL_FALLBACK
WORKDIR /tmp/fetch
RUN set -euo pipefail; \
  for u in "$ZK_URL" "$ZK_URL_FALLBACK"; do \
    echo "Trying $u" && curl -fL "$u" -o zookeeper.tgz && break || true; \
  done; \
  mkdir -p /opt/zookeeper && tar -xzf zookeeper.tgz --strip-components=1 -C /opt/zookeeper

FROM eclipse-temurin:21-jre-jammy
SHELL ["/bin/bash","-lc"]
ARG APP_UID=10001 APP_GID=10001
RUN groupadd -g ${APP_GID} app && useradd -m -u ${APP_UID} -g ${APP_GID} app

ENV ZK_HOME=/opt/zookeeper PATH=/opt/zookeeper/bin:$PATH ZK_HEAP_OPTS="-Xms256m -Xmx256m"
COPY --from=fetch /opt/zookeeper /opt/zookeeper

# Include ASF LICENSE/NOTICE only (no COPY from repo)
# Include ASF LICENSE/NOTICE (ZooKeeper uses .txt filenames)
RUN set -e; \
  mkdir -p /licenses/apache/zookeeper /data/zookeeper /var/log/zookeeper; \
  for f in LICENSE LICENSE.txt; do \
    if [ -f "/opt/zookeeper/$f" ]; then cp "/opt/zookeeper/$f" /licenses/apache/zookeeper/LICENSE; break; fi; \
  done; \
  for f in NOTICE NOTICE.txt; do \
    if [ -f "/opt/zookeeper/$f" ]; then cp "/opt/zookeeper/$f" /licenses/apache/zookeeper/NOTICE; break; fi; \
  done


# Render a minimal zoo.cfg from env at runtime
COPY <<'SH' /usr/local/bin/start-zookeeper.sh
#!/usr/bin/env bash
set -euo pipefail
: "${ZK_HOME:=/opt/zookeeper}"
: "${ZK_DATA:=/data/zookeeper}"
: "${ZK_TICK_TIME:=2000}"
: "${ZK_CLIENT_PORT:=2181}"

mkdir -p "${ZK_DATA}"
cat > "${ZK_HOME}/conf/zoo.cfg" <<EOF
tickTime=${ZK_TICK_TIME}
dataDir=${ZK_DATA}
clientPort=${ZK_CLIENT_PORT}
initLimit=5
syncLimit=2
autopurge.snapRetainCount=3
autopurge.purgeInterval=1
EOF
exec "${ZK_HOME}/bin/zkServer.sh" start-foreground
SH
RUN chmod +x /usr/local/bin/start-zookeeper.sh && chown -R app:app /opt/zookeeper /licenses /data /var/log
USER app
EXPOSE 2181
ENTRYPOINT ["/usr/local/bin/start-zookeeper.sh"]



