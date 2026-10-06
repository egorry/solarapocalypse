#!/bin/bash
# Launch the dev server, wait for Done/crash, then stop it. Usage: scripts/smoke_server.sh <tag> (logs go to research/)
cd "$(dirname "$0")/.." && mkdir -p research
TAG=${1:-run}
rm -f run/logs/latest.log
./gradlew runServer --console=plain > research/runServer_$TAG.log 2>&1 &
GPID=$!
RESULT=TIMEOUT
for i in $(seq 1 180); do
  sleep 5
  if grep -q "Done (" run/logs/latest.log 2>/dev/null; then RESULT=DONE; break; fi
  if grep -q -E "Crash report saved|Encountered an unexpected exception|FMLSecurityManager\\$ExitTrappedException|Minecraft has crashed" run/logs/latest.log 2>/dev/null; then RESULT=CRASH; break; fi
  if ! kill -0 $GPID 2>/dev/null; then RESULT=EXITED; break; fi
done
sleep ${SMOKE_WAIT:-3}
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'GradleStartServer' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" 2>/dev/null
wait $GPID 2>/dev/null
cp run/logs/latest.log research/server_latest_$TAG.log 2>/dev/null
cp run/logs/mixinbooter.log research/server_mixinbooter_$TAG.log 2>/dev/null
echo "SMOKE RESULT: $RESULT"
