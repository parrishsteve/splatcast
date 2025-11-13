#!/usr/bin/env bash
set -euo pipefail

: "${ZK_HOME:=/opt/zookeeper}"
: "${ZK_DATA:=/data/zookeeper}"

mkdir -p "${ZK_DATA}"

# Ensure dataDir is set to mount
if ! grep -q '^dataDir=' "${ZK_HOME}/conf/zoo.cfg"; then
  echo "dataDir=${ZK_DATA}" >> "${ZK_HOME}/conf/zoo.cfg"
fi

exec "${ZK_HOME}/bin/zkServer.sh" start-foreground
