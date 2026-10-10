#!/bin/bash
# Run a dev check on a fresh dev world. Usage: scripts/probe_server.sh [tag] [probe|selftest|bench] [variant]
# probe: the CubicProbe research probe (docs/RESEARCH.md section 9); selftest: debug/SelfTest (the apocalypse end to end);
# bench: debug/Bench (throughput of an infinite phase; variants in Bench.java, comma-separated: fire0,stone).
# Deletes run/world_cubic; selftest and bench copy scripts/<mode>.cfg to run/config/solarapocalypse-<mode>.cfg. Logs go to research/ (git-ignored); prints the SOLAR lines.
cd "$(dirname "$0")/.." && mkdir -p research
TAG=${1:-run}
MODE=${2:-probe}
VARIANT=${3:-}
# keep the previous run's logs (they may be the user's play session): research/ is git-ignored
for f in latest.log debug.log; do [ -f run/logs/$f ] && cp run/logs/$f research/before_${MODE}_${TAG}_$f; done
rm -rf run/world_cubic run/logs/latest.log
# the self-test's own config: the engine is checked against fixed phases, whatever the defaults or run/config say
[ "$MODE" != probe ] && cp scripts/$MODE.cfg run/config/solarapocalypse-$MODE.cfg
./gradlew runServer --console=plain "-Pextra_jvm_args=-Dsolarapocalypse.$MODE=$VARIANT" > research/${MODE}_gradle_$TAG.log 2>&1 &
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
cp run/logs/latest.log research/${MODE}_latest_$TAG.log
grep -E "SOLAR (PROBE|TEST|BENCH)|Exception|FATAL" research/${MODE}_latest_$TAG.log | head -80
