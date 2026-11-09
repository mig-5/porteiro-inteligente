# Porteiro Inteligente — Interfonia Autônoma para Android + ESP32

Implementação do projeto descrito em `projeto.odt`: um sistema de interfonia local
em que um celular Android fixado na portaria atua como servidor (WebSocket/HTTP,
reconhecimento facial e geração de QR Code) e o celular do morador atua como
cliente (chamadas, abertura remota e cadastro biométrico). O portão é acionado por
um ESP32 via HTTP na rede Wi-Fi local.

## Estrutura do repositório

```
/
├── android-app/                     # Projeto Gradle com dois aplicativos
│   ├── server-portaria/             # App da portaria (servidor local)
│   │   └── src/main/java/com/porteiro/server/
│   │       ├── data/                # Room (5 tabelas), DAOs e repositório
│   │       ├── network/             # NanoHTTPD + WebSocket, QR, ESP32, acesso
│   │       ├── face/                # ML Kit + TFLite/embeddings, anti-bloqueio
│   │       ├── webrtc/              # Câmera única, chamadas e sinalização
│   │       ├── service/             # Serviço em primeiro plano
│   │       ├── settings/            # DataStore (porta, ESP32, segredo, limiar)
│   │       └── ui/                  # Compose: terminal, cadastros, logs, ajustes
│   └── client-morador/              # App do morador
│       └── src/main/java/com/porteiro/client/
│           ├── data/                # Configuração de pareamento (DataStore)
│           ├── net/                 # Tipos de mensagens WebSocket
│           ├── webrtc/              # Chamada recebida (WebRTC)
│           └── ui/                  # Pareamento por QR, home, chamada
└── esp32-firmware/
    ├── esp32-firmware.ino           # Wi-Fi, HTTP, autenticação e relé
    └── config.h                     # Wi-Fi, IP fixo, segredo, pinos
```

## Requisitos implementados

### Servidor (portaria)
- **RF-01** cadastro/edição via CRUD de apartamentos e moradores (aba Cadastros).
- **RF-02** QR Code de pareamento com `server_ip`, `port`, `auth_token`, `device_id`;
  expira em 5 minutos (RN-02).
- **RF-03** terminal com teclado numérico e lista rápida de apartamentos.
- **RF-04** chamada WebRTC de áudio/vídeo da câmera frontal para o morador.
- **RF-05** reconhecimento facial contínuo (ML Kit + embeddings) com cache local.
- **RF-06** POST autenticado ao ESP32 em `/api/unlock`.
- **RF-07** histórico de auditoria em SQLite (`access_logs`).
- **RN-01** o ESP32 só aciona com `X-App-Secret` correto.
- **RN-03** 3 falhas faciais em 30 s bloqueiam a busca por 30 s.
- **RN-04** máximo de 5 dispositivos por unidade.
- **RNF-04** apenas embeddings são persistidos; imagens não são salvas.

### Cliente (morador)
- **RF-08** leitura do QR Code (ZXing/CameraX) e conexão automática.
- **RF-09** tela/notificação de chamada recebida.
- **RF-10** botão grande “ABRIR PORTÃO” na chamada e na tela inicial.
- **RF-11** captura de foto e envio ao servidor para cadastro biométrico.
- **RNF-03** operação totalmente local, sem internet externa.

### Firmware ESP32
- **RF-12** conexão Wi-Fi com reconexão automática.
- **RF-13** servidor HTTP minimalista com validação de segredo e IP de origem.
- **RF-14** pulso no GPIO 5 por `duration_ms` (100–5000 ms).
- **RN-06** uso de `JsonDocument` pequeno e sem buffers dinâmicos grandes.

## Como compilar

Pré-requisitos: JDK 17, Android SDK (API 35) e Gradle Wrapper incluído.

```bash
cd android-app
./gradlew assembleDebug
```

APKs gerados:
- `android-app/server-portaria/build/outputs/apk/debug/server-portaria-debug.apk`
- `android-app/client-morador/build/outputs/apk/debug/client-morador-debug.apk`

Verificação executada neste repositório:
```bash
./gradlew :server-portaria:assembleDebug :client-morador:assembleDebug
./gradlew :server-portaria:lintDebug :client-morador:lintDebug
```

## Configuração do servidor (portaria)

1. Instale e abra o app **Porteiro Inteligente** no celular da portaria.
2. Conceda câmera, microfone e notificações.
3. Conecte o aparelho ao Wi-Fi da portaria (o endereço local aparece na tela).
4. Em **Cadastros**, crie apartamentos e moradores.
5. Toque em **QR** no morador para gerar o código de pareamento.
6. Em **Ajustes**, configure:
   - IP fixo do ESP32 (ex.: `192.168.1.100`) e porta (padrão `80`);
   - chave compartilhada (**igual** a `APP_SECRET` do firmware);
   - duração do pulso (padrão 1500 ms);
   - limiar de similaridade facial e liberação automática.
7. Use a aba **Portaria** para digitar/chamar o apartamento.

O servidor HTTP/WebSocket escuta na porta `8080` (configurável) e continua ativo
por um serviço em primeiro plano.

## Pareamento do morador

1. Instale o app **Porteiro Morador**.
2. Toque em **Escanear QR Code** e leia o código exibido na portaria.
3. O app salva IP, porta e token e conecta automaticamente via WebSocket.
4. O token é permanente após o primeiro uso; o QR não utilizado expira em 5 min.
5. Para biometria, toque em **Cadastrar meu rosto** (envia a foto ao servidor,
   que extrai e guarda somente o vetor).

## Reconhecimento facial — modelo necessário

O pipeline usa ML Kit para detecção de rostos e um modelo TFLite para os
embeddings. Coloque o modelo em:

```
android-app/server-portaria/src/main/assets/mobilefacenet.tflite
```

Especificação esperada:
- entrada `[1, 112, 112, 3]`, float32 normalizado para `[-1, 1]`;
- saída `[1, 192]` (embedding);
- distância usada: similaridade de cosseno (limiar padrão `0.75`).

Sem o modelo, o app continua funcionando para chamadas, QR Code e abertura
remota, mas a liberação facial fica desativada (com aviso na tela). O fallback
geométrico existe apenas para desenvolvimento e está desligado por segurança em
`FaceEmbedder.ALLOW_GEOMETRIC_FALLBACK`.

## Firmware ESP32

Bibliotecas: **ArduinoJson 7.x** (testado com 7.4.3). Placa: ESP32 (Arduino IDE ou ESP-IDF).

Compilação verificada com ESP32 core 3.1.1 e partição **Huge APP (3 MB, sem OTA)**:
29% de flash e 14% de RAM — dentro do limite de 40% do RNF-06. Na Arduino IDE,
selecione *Tools → Partition Scheme → Huge APP (3MB No OTA/1MB SPIFFS)*. Via CLI:

```bash
arduino-cli lib install "ArduinoJson@7.4.3"
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=huge_app esp32-firmware
```

1. Ajuste `esp32-firmware/config.h`:
   - `WIFI_SSID` / `WIFI_PASSWORD`;
   - IP fixo do ESP32 (`STATIC_IP`) e `ALLOWED_SERVER_IP` (IP do celular da portaria);
   - `APP_SECRET` idêntico ao configurado no app;
   - pinos e nível ativo do relé.
2. Grave `esp32-firmware.ino`.
3. Ligação elétrica:
   - GPIO 5 → módulo relé (com optoacoplador/diodo de proteção);
   - contato NO/COM do relé em paralelo com o botão de pulso do portão;
   - LED da placa (GPIO 2) pisca ao conectar e acende durante o pulso.

Endpoint chamado pelo servidor:

```http
POST http://<IP_DO_ESP32>/api/unlock
Content-Type: application/json
X-App-Secret: <CHAVE_COMPARTILHADA>

{"action":"open","duration_ms":1500,"triggered_by":"facial_recognition"}
```

## Protocolo local resumido

HTTP (servidor da portaria):
- `GET /api/health`
- `POST /api/unlock` — header `X-Auth-Token`
- `POST /api/face` — header `X-Auth-Token`, corpo JPEG

WebSocket `/ws?token=<token>&device_name=<nome>`:
- servidor → cliente: `hello`, `incoming_call`, `webrtc_offer`, `webrtc_ice`,
  `call_ended`, `call_cancelled`, `unlock_result`;
- cliente → servidor: `webrtc_answer`, `webrtc_ice`, `unlock`, `hangup`, `reject`.

## Modelo de dados (Room/SQLite)

- `apartments` — unidades/blocos;
- `residents` — moradores vinculados ao apartamento (`is_admin`);
- `devices` — aparelhos pareados, token único e datas de pareamento/atividade;
- `facial_embeddings` — vetores serializados (sem imagens);
- `access_logs` — método, status e horário de cada evento.

## Segurança e limitações

- O tráfego usa HTTP/WebSocket em claro na rede local; mantenha a portaria em uma
  rede Wi-Fi confiável e altere `APP_SECRET` do firmware e do app.
- O elemento `ALLOWED_SERVER_IP` restringe o ESP32 ao IP do celular da portaria.
- Tokens de dispositivo são UUIDs; o QR não utilizado expira após 5 minutos.
- WebRTC usa apenas candidatos locais (sem STUN/TURN): funciona na mesma LAN,
  conforme RNF-03, mas não atravessa NAT/Internet.
- A captura de rosto para cadastro usa a câmera do sistema (miniatura). Para
  maior precisão, substitua por captura em resolução cheia/CameraX.
- O app da portaria mantém câmera e servidor ativos enquanto a tela está aberta;
  um serviço em primeiro plano mantém a rede ativa. O app do morador também usa
  um serviço em primeiro plano para continuar recebendo chamadas em segundo plano.


## Licença

Este projeto é software livre, distribuído sob a **GNU General Public License v3.0**.
Consulte o arquivo [LICENSE](LICENSE) para o texto completo.

Copyright (C) 2026 Miguel Schmidt
