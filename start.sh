#!/bin/bash
# ================================================================
#  LEGA Server - Linux/macOS Start Script
#  Optimized JVM flags for high-performance Minecraft servers
# ================================================================

LEGA_JAR="lega-server/build/libs/LEGA-1.0.0-SNAPSHOT.jar"
MIN_HEAP="4G"
MAX_HEAP="8G"

echo "[LEGA] Starting LEGA Server..."

java \
  -Xms${MIN_HEAP} \
  -Xmx${MAX_HEAP} \
  -XX:+UseG1GC \
  -XX:G1HeapRegionSize=16M \
  -XX:MaxGCPauseMillis=100 \
  -XX:+ParallelRefProcEnabled \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  --enable-preview \
  -jar ${LEGA_JAR} \
  nogui
