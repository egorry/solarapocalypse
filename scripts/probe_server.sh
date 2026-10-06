#!/bin/bash
# Run the CubicProbe research probe (docs/RESEARCH.md section 9) on a fresh dev world. Usage: scripts/probe_server.sh [tag]
# Deletes run/world_cubic. Logs go to research/ (git-ignored); prints the SOLAR PROBE lines.
cd "$(dirname "$0")/.." && mkdir -p research
TAG=${1:-run}
rm -rf run/world_cubic run/logs/latest.log
./gradlew runServer --console=plain "-Pextra_jvm_args=-Dsolarapocalypse.probe" > research/probe_gradle_$TAG.log 2>&1 &
GPID=$!
for i in $(seq 1 240); do
  sleep 5
  kill -0 $GPID 2>/dev/null || break
  if grep -q -E "Crash report saved|Encountered an unexpected exception" run/logs/latest.log 2>/dev/null; then sleep 5; break; fi
done
if kill -0 $GPID 2>/dev/null; then
  powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'GradleStartServer' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" 2>/dev/null
  echo "server killed after timeout/crash"
fi
wait $GPID 2>/dev/null
cp run/logs/latest.log research/probe_latest_$TAG.log
grep -E "SOLAR PROBE|Exception|FATAL" research/probe_latest_$TAG.log | head -60
