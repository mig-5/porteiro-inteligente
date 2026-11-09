#pragma once

// ============================================================================
//  Configurações do firmware — ajuste antes de gravar no ESP32
// ============================================================================

// ---- Wi-Fi da portaria -----------------------------------------------------
#define WIFI_SSID       "NOME_DA_REDE_WIFI"
#define WIFI_PASSWORD   "SENHA_DA_REDE_WIFI"

// IP fixo opcional (recomendado para o servidor Android encontrar o ESP32).
// Deixe USE_STATIC_IP em 0 para usar DHCP.
#define USE_STATIC_IP   1
#define STATIC_IP       "192.168.1.100"
#define GATEWAY_IP      "192.168.1.1"
#define SUBNET_MASK     "255.255.255.0"
#define DNS_IP          "8.8.8.8"

// ---- Servidor HTTP ---------------------------------------------------------
#define HTTP_PORT       80

// ---- Segurança -------------------------------------------------------------
// Deve ser idêntica ao campo "Chave compartilhada" nos ajustes do app da portaria.
#define APP_SECRET      "porteiro-secret-change-me"

// Se preenchido, só aceita requisições vindas deste IP (celular da portaria).
// Deixe vazio ("") para aceitar qualquer origem na rede local.
#define ALLOWED_SERVER_IP "192.168.1.150"

// ---- Hardware --------------------------------------------------------------
#define RELAY_PIN       5     // GPIO conectada ao módulo relé
#define LED_PIN         2     // LED indicador da placa
#define RELAY_ACTIVE_LEVEL 1  // 1 = HIGH aciona; 0 = LOW aciona (módulos comuns)
#define DEFAULT_PULSE_MS 1500 // pulso padrão de abertura (1,5 s)
#define MAX_PULSE_MS     5000
#define MIN_PULSE_MS     100

// ---- Depuração -------------------------------------------------------------
#define SERIAL_BAUD     115200
