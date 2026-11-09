/*
 * Firmware do módulo ESP32 — acionador do relé do portão.
 *
 * Requisitos atendidos:
 *  RF-12 conexão Wi-Fi com reconexão automática
 *  RF-13 servidor HTTP minimalista com autenticação
 *  RF-14 pulso de relé programável (GPIO 5)
 *  RN-01 autorização por segredo pré-compartilhado (X-App-Secret)
 *  RN-06 baixo consumo de recursos
 *
 * Dependência: ArduinoJson 7.x (Gerenciador de Bibliotecas da Arduino IDE).
 *
 * Endpoints:
 *  POST /api/unlock   { "action": "open", "duration_ms": 1500,
 *                       "triggered_by": "facial_recognition" }
 *  GET  /api/status
 *  GET  /api/health
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>
#include "config.h"

WebServer server(HTTP_PORT);

bool relayActive = false;
unsigned long relayOffAt = 0;
unsigned long lastWifiAttempt = 0;
unsigned long bootMillis = 0;

// ---------------------------------------------------------------------------
//  Utilitários
// ---------------------------------------------------------------------------
static void applyRelay(bool active) {
  const uint8_t level = active ? RELAY_ACTIVE_LEVEL : !RELAY_ACTIVE_LEVEL;
  digitalWrite(RELAY_PIN, level);
  digitalWrite(LED_PIN, active ? HIGH : LOW);
}

static void sendJson(int status, const char* body) {
  server.send(status, "application/json", body);
}

static bool constantTimeEquals(const String& a, const char* b) {
  const size_t lenA = a.length();
  const size_t lenB = strlen(b);
  if (lenA != lenB) return false;
  uint8_t diff = 0;
  for (size_t i = 0; i < lenA; i++) {
    diff |= (uint8_t)(a[i] ^ b[i]);
  }
  return diff == 0;
}

static bool isAllowedServer() {
#ifdef ALLOWED_SERVER_IP
  if (strlen(ALLOWED_SERVER_IP) > 0) {
    IPAddress allowed;
    if (allowed.fromString(ALLOWED_SERVER_IP)) {
      return server.client().remoteIP() == allowed;
    }
  }
#endif
  return true;
}

static void handleUnlock() {
  if (!isAllowedServer()) {
    sendJson(403, "{\"ok\":false,\"error\":\"origem nao autorizada\"}");
    return;
  }

  const String secret = server.header("X-App-Secret");
  if (!constantTimeEquals(secret, APP_SECRET)) {
    sendJson(401, "{\"ok\":false,\"error\":\"segredo invalido\"}");
    return;
  }

  JsonDocument doc;
  const DeserializationError err = deserializeJson(doc, server.arg("plain"));
  if (err) {
    sendJson(400, "{\"ok\":false,\"error\":\"json invalido\"}");
    return;
  }

  const char* action = doc["action"] | "";
  if (strcmp(action, "open") != 0) {
    sendJson(400, "{\"ok\":false,\"error\":\"acao invalida\"}");
    return;
  }

  long duration = doc["duration_ms"] | DEFAULT_PULSE_MS;
  if (duration < MIN_PULSE_MS) duration = MIN_PULSE_MS;
  if (duration > MAX_PULSE_MS) duration = MAX_PULSE_MS;

  const char* triggeredBy = doc["triggered_by"] | "unknown";

  relayOffAt = millis() + (unsigned long)duration;
  relayActive = true;
  applyRelay(true);

  Serial.printf("[RELE] abertura por %s durante %ld ms\n", triggeredBy, duration);

  char response[96];
  snprintf(response, sizeof(response),
           "{\"ok\":true,\"duration_ms\":%ld}", duration);
  sendJson(200, response);
}

static void handleStatus() {
  char response[256];
  snprintf(response, sizeof(response),
           "{\"ok\":true,\"ip\":\"%s\",\"rssi\":%d,\"uptime_s\":%lu,"
           "\"relay_active\":%s,\"firmware\":\"1.0\"}",
           WiFi.localIP().toString().c_str(),
           WiFi.RSSI(),
           (millis() - bootMillis) / 1000UL,
           relayActive ? "true" : "false");
  sendJson(200, response);
}

static void handleHealth() {
  sendJson(200, "{\"ok\":true}");
}

static void handleNotFound() {
  sendJson(404, "{\"ok\":false,\"error\":\"rota nao encontrada\"}");
}

// ---------------------------------------------------------------------------
//  Wi-Fi
// ---------------------------------------------------------------------------
static void connectWifi() {
  lastWifiAttempt = millis();
  Serial.printf("[WiFi] conectando a %s...\n", WIFI_SSID);
  WiFi.disconnect();
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

#if USE_STATIC_IP
  IPAddress ip, gateway, subnet, dns;
  ip.fromString(STATIC_IP);
  gateway.fromString(GATEWAY_IP);
  subnet.fromString(SUBNET_MASK);
  dns.fromString(DNS_IP);
  WiFi.config(ip, gateway, subnet, dns);
#endif
}

static void maintainWifi() {
  if (WiFi.status() == WL_CONNECTED) {
    digitalWrite(LED_PIN, relayActive ? HIGH : LOW);
    return;
  }

  digitalWrite(LED_PIN, HIGH);
  if (millis() - lastWifiAttempt > 10000UL) {
    connectWifi();
  }
  if (((millis() / 400UL) % 2UL) == 0UL) {
    digitalWrite(LED_PIN, LOW);
  }
}

// ---------------------------------------------------------------------------
//  setup / loop
// ---------------------------------------------------------------------------
void setup() {
  Serial.begin(SERIAL_BAUD);
  delay(200);
  Serial.println("\n[porteiro] ESP32 iniciando");

  pinMode(RELAY_PIN, OUTPUT);
  pinMode(LED_PIN, OUTPUT);
  applyRelay(false);

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.persistent(false);
  connectWifi();

  server.on("/api/unlock", HTTP_POST, handleUnlock);
  server.on("/api/status", HTTP_GET, handleStatus);
  server.on("/api/health", HTTP_GET, handleHealth);
  server.onNotFound(handleNotFound);
  server.begin();

  bootMillis = millis();
  Serial.printf("[HTTP] servidor na porta %d\n", HTTP_PORT);
}

void loop() {
  server.handleClient();
  maintainWifi();

  if (relayActive && (long)(millis() - relayOffAt) >= 0) {
    relayActive = false;
    applyRelay(false);
    Serial.println("[RELE] pulso encerrado");
  }

  delay(2);
}
