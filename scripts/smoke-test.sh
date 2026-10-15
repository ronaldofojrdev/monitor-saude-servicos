#!/usr/bin/env bash
# Teste ponta a ponta com o scheduler de verdade (sem chamar /check na mao).
# Uso: docker compose up --build -d && ./scripts/smoke-test.sh
set -euo pipefail

API=${API_URL:-http://localhost:8085}

# primeira ocorrencia do campo no JSON
json_field() { grep -o "\"$1\":\"\{0,1\}[^\",}]*" | head -1 | sed "s/^\"$1\":\"\{0,1\}//"; }

post() { curl -sf -X POST "$API$1" -H 'Content-Type: application/json' -d "$2"; }

wait_status() { # id esperado segundos
  for _ in $(seq 1 "$3"); do
    [ "$(curl -sf "$API/targets/$1" | json_field status)" = "$2" ] && return 0
    sleep 1
  done
  echo "FALHOU: alvo $1 nao chegou em $2 (status atual: $(curl -sf "$API/targets/$1" | json_field status))"
  exit 1
}

SUFFIX=$RANDOM

echo "1) cadastra um alvo saudavel (o proprio monitor) e um alvo morto, checagem a cada 10s"
OK=$(post /targets "{\"name\":\"proprio-monitor-$SUFFIX\",\"url\":\"http://localhost:8085/actuator/health\",\"intervalSeconds\":10,\"expectedBodyContains\":\"UP\"}" | json_field id)
DEAD=$(post /targets "{\"name\":\"servico-morto-$SUFFIX\",\"url\":\"http://localhost:9/\",\"intervalSeconds\":10,\"timeoutMs\":1000,\"failureThreshold\":2}" | json_field id)

echo "2) o scheduler checa sozinho: o saudavel vira UP"
wait_status "$OK" UP 30
echo "   proprio-monitor: UP"

echo "3) o morto falha 2 vezes seguidas e vira DOWN (leva ~20s)"
wait_status "$DEAD" DOWN 60
echo "   servico-morto: DOWN"

echo "4) um alerta DOWN foi criado, uma vez so"
ALERTS=$(curl -sf "$API/alerts?targetId=$DEAD")
[ "$(echo "$ALERTS" | grep -o '"type":"DOWN"' | wc -l)" = "1" ] || { echo "FALHOU: esperado 1 alerta DOWN: $ALERTS"; exit 1; }
echo "   $(echo "$ALERTS" | json_field message)"

echo "5) historico e estatisticas"
echo "   checagens do saudavel: $(curl -sf "$API/targets/$OK/stats" | json_field totalChecks), uptime: $(curl -sf "$API/targets/$OK/stats" | json_field uptimePercent)%"
echo "   ultima falha do morto: $(curl -sf "$API/targets/$DEAD/history?failuresOnly=true&size=1" | json_field error)"

echo "6) metricas Prometheus"
METRICS=$(curl -sf "$API/actuator/prometheus")
echo "$METRICS" | grep -q "monitor_target_up{.*target=\"servico-morto-$SUFFIX\"} 0.0" || { echo "FALHOU: gauge do morto deveria ser 0.0"; exit 1; }
echo "$METRICS" | grep -q "monitor_target_up{.*target=\"proprio-monitor-$SUFFIX\"} 1.0" || { echo "FALHOU: gauge do saudavel deveria ser 1.0"; exit 1; }
echo "   monitor_target_up: saudavel=1.0, morto=0.0"

echo "7) health do proprio monitor"
curl -sf "$API/actuator/health" | grep -q '"monitor":{"status":"UP"' || { echo "FALHOU: health do monitor"; exit 1; }
echo "   monitor: UP"

echo "OK"
