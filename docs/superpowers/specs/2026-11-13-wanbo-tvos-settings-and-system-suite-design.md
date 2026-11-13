# WanboLightOS: Native tvOS Settings Suite & System Modernization Design Specification

**Date:** 2026-11-13  
**Status:** Approved by User  
**Target Device:** Wanbo T2 Max Smart Projector (~843 MB RAM usable, MediaTek MT5862 / T9255, Android 9 Pie / API 28)

---

## 1. Executive Summary

This design specification outlines the complete modernization of the Wanbo T2 Max system settings and application ecosystem, replacing legacy MediaTek TV components with a cohesive, native Apple TV (tvOS) inspired interface. 

### Key Objectives
1. **Total Replacement of `TvSettingsPlus`**: Purge the legacy `TvSettingsPlus.apk` from `/system/priv-app/` and deploy a dedicated, high-performance system settings application: `WanboSettings` (`com.wanbo.settings`).
2. **tvOS Split-View Architecture**: Implement a two-column Apple TV Split-View layout (left category rail, right dynamic content pane) with frosted glass aesthetics, smooth D-pad focus animations, and full system configuration accessibility.
3. **Comprehensive Hardware & OS Controls**: Provide complete viewing and modification capabilities for Wi-Fi, Bluetooth, Keystone 4-point calibration, Projection Mode (Front/Rear/Ceiling), Audio output, App storage/lifecycle, Date/Time, and Developer/ADB options.
4. **ROM Debloat & App Injections**:
   - Purge `EShareServer`, `EAirPlay` (`iMirror`), `EDLNA`, and the legacy `MLocalMM2` file manager.
   - Hide `LiveTV` from the user-facing launcher while maintaining the underlying `com.mediatek.tvinput` service to ensure 1-click HDMI hardware switching remains fully functional.
   - Pre-install a modern open-source Android TV File Manager (Material Files TV) in `/system/app/MaterialFiles/`.
   - Pre-install the official **Aptoide TV** store in `/system/priv-app/AptoideTV/`.

---

## 2. Architecture & System Integration

### 2.1 Package & Privileged Execution
- **Package Name**: `com.wanbo.settings`
- **Location on Filesystem**: `/system/priv-app/WanboSettings/WanboSettings.apk`
- **Permissions**: System-level privileged application with `uid: 1000` (system) or `root`, configured with `ro.control_privapp_permissions=disable` to grant:
  - `android.permission.WRITE_SECURE_SETTINGS`
  - `android.permission.WRITE_SETTINGS`
  - `android.permission.CHANGE_WIFI_STATE`
  - `android.permission.ACCESS_WIFI_STATE`
  - `android.permission.BLUETOOTH_ADMIN`
  - `android.permission.BLUETOOTH`
  - `android.permission.SET_TIME`
  - `android.permission.SET_TIME_ZONE`
  - `android.permission.FORCE_STOP_PACKAGES`
  - `android.permission.CLEAR_APP_CACHE`
  - `android.permission.DELETE_CACHE_FILES`
  - `android.permission.REBOOT`

### 2.2 Intent Filter Interception
`WanboSettings` implements intent filters for all standard Android system settings entry points, ensuring that any external application, top bar shortcut, or global button press seamlessly routes to the tvOS settings UI:
- `android.settings.SETTINGS`
- `android.settings.WIFI_SETTINGS`
- `android.settings.NETWORK_OPERATOR_SETTINGS`
- `android.settings.BLUETOOTH_SETTINGS`
- `android.settings.DEVICE_INFO_SETTINGS`
- `android.settings.DATE_SETTINGS`
- `android.settings.APPLICATION_SETTINGS`
- `android.settings.MANAGE_APPLICATIONS_SETTINGS`
- `android.settings.DISPLAY_SETTINGS`
- `android.settings.SOUND_SETTINGS`

---

## 3. UI/UX Design: Apple TV (tvOS) Split-View

### 3.1 Visual Language & Design Tokens
- **Background**: Deep obsidian (`#0D0F14`) with subtle 40% blur / gradient overlay.
- **Glassmorphism**: Category rail and content cards use translucent background surfaces (`#1A1E29` at 70% opacity) with 1px border highlight (`#2E384D`).
- **Typography**: Clean sans-serif with high contrast white (`#FFFFFF`) for primary labels and cool slate (`#94A3B8`) for secondary summaries.
- **Focus Engine**: Focusable elements scale up by 1.05x with an illuminated boundary glow (`#38BDF8` cyan-blue highlight) and smooth 150ms interpolation.

### 3.2 Navigation Structure
```
+------------------------------------+-----------------------------------------------------+
|  WanboLightOS Ajustes              |  [Icon] Wi-Fi & Conexoes                            |
|                                    |                                                     |
|  [Wi-Fi]  Rede                     |  [Toggle Card] Wi-Fi .......................... [ON]|
|  [BT]     Bluetooth & Controles    |                                                     |
|  [Proj]   Imagem & Projecao        |  [Card] Rede Atual: MinhaRede_5G                    |
|  [Audio]  Som & Saida              |         Status: Conectado | IP: 192.168.1.15        |
|  [Apps]   Aplicativos              |                                                     |
|  [Geral]  Sistema & Preferencias   |  [Section Header] Redes Disponiveis                 |
|  [Info]   Sobre o Dispositivo      |  > [Item] Sala_Estar_5G .................... [Lck]  |
|                                    |  > [Item] Escritorio_2G .................... [Lck]  |
+------------------------------------+-----------------------------------------------------+
```

### 3.3 Detailed Category Panes

#### 1. Rede & Wi-Fi
- **Wi-Fi Power Toggle**: Enable/disable Wi-Fi interface via `WifiManager.setWifiEnabled()`.
- **Active Connection Details**: Current SSID, Signal Strength (dBm / bar count), IP Address, Gateway, Subnet Mask, MAC Address.
- **Available Networks List**: Real-time scan results with lock indicators for encrypted networks; clicking prompts a frosted glass modal password dialog.
- **Ethernet Status**: Detects wired link state and static/DHCP IP.

#### 2. Bluetooth & Controles Remotos
- **Bluetooth Toggle**: Enable/disable via `BluetoothAdapter`.
- **Paired Devices**: List of paired headphones, soundbars, gamepads, and Wanbo remotes with disconnect / unpair actions.
- **Discoverable Scanning**: Real-time discovery of nearby Bluetooth audio and HID devices with 1-click pairing.

#### 3. Imagem & Projecao (Wanbo Hardware)
- **Modo de Projeção**: 4-way selector invoking `MtkTvFApiDisplayWrapper.setMirrorMode()` and updating `FLIP_MODE`:
  - Projeção Frontal (Desktop)
  - Projeção Traseira (Rear Desktop)
  - Projeção Frontal Teto (Ceiling Front)
  - Projeção Traseira Teto (Ceiling Rear)
- **Calibração Keystone Trapezoidal**: Direct 1-click launch of the 4-point hardware trapezoid calibration hub (`com.chijimlee.tyylauncher.activity.ProjectModeActivity` / `TrapezoidActivity`).
- **Zoom Digital & Proporção**: 16:9, 4:3, e zoom de redução de tela (100% a 80%).
- **Perfil de Cor & Brilho**: Modos de imagem (Padrão, Cinema, Vívido, Usuário) com sliders de brilho, contraste e saturação.

#### 4. Som & Áudio
- **Volume do Sistema**: Slider de volume de 0 a 100 via `AudioManager`.
- **Saída de Áudio**: Alternância entre Alto-falantes Internos do Projetor, Fone/Caixa Bluetooth e Saída P2/HDMI ARC.
- **Perfil de Equalização**: Padrão, Música, Filme, Voz.

#### 5. Aplicativos & Armazenamento
- **Medidor de Armazenamento eMMC**: Gráfico visual em barra mostrando espaço Total (ex: 8 GB), Ocupado por Apps, Ocupado pelo Sistema e Espaço Livre.
- **Gerenciador de Aplicativos**: Lista de todos os aplicativos instalados com detalhes e ações:
  - Forçar Parada (`am force-stop`)
  - Limpar Dados (`pm clear`)
  - Limpar Cache
  - Desinstalar Aplicativo (`pm uninstall`)
  - Visualizar Permissões

#### 6. Sistema & Preferências
- **Nome do Dispositivo**: Permite renomear o projetor na rede local.
- **Data & Hora**: Alternador de fuso horário automático, seleção manual de fuso e formato 24h.
- **Idioma do Sistema**: Seletor de idioma (Português, Inglês, Espanhol).
- **Timer de Desligamento**: Sleep timer (Desativado, 15m, 30m, 1h, 2h).
- **Opções de Desenvolvedor**:
  - Depuração ADB sobre Wi-Fi (alterna `service.adb.tcp.port=5555`).
  - Depuração USB.

#### 7. Sobre o Projetor
- **Identificação do Modelo**: Wanbo T2 Max Smart Projector
- **Sistema Operacional**: WanboLightOS v1.0 (Android 9 Pie, API 28)
- **Hardware**: MediaTek MT5862 (4x ARM Cortex-A53)
- **Monitor de Memória RAM em Tempo Real**: Medidor visual da RAM física (~843 MB) + Swap ZRAM LZ4 (1 GB), exibindo memória livre, ocupada e buffers em tempo real.
- **Build & Kernel**: Versão do kernel e compilação limpa do firmware.

---

## 4. Ecossistema da ROM & Debloat

### 4.1 Debloat Complementar
Adicionar à rotina de limpeza do pipeline (`os-pipeline/modify_system_image.py`):
- `/system/priv-app/TvSettingsPlus` (substituído pelo `WanboSettings`)
- `/system/app/EShareServer` (serviço proprietário pesado)
- `/system/app/EAirPlay` (iMirror / AirPlay proprietário desatualizado)
- `/system/app/EDLNA` (serviço DLNA não utilizado)
- `/system/product/app/MLocalMM2` (gerenciador de arquivos legado antigo)

### 4.2 Preservação da Entrada HDMI com Ocultação do Live TV
- O pacote `/system/priv-app/LiveTV/LiveTV.apk` permanece instalado para fornecer a superfície de renderização de vídeo nativa da porta HDMI (`com.mediatek.tvinput`), mas é **filtrado e ocultado** de todos os seletores e gavetas de aplicativos do `WanboOS`.
- O botão **HDMI** na Top Bar do WanboOS aciona a entrada diretamente sem expor a interface de TV aberta do AOSP.

### 4.3 Injeção de Aplicativos Pré-instalados
1. **Material Files TV**: Aplicativo de arquivos open-source, moderno e fluído para Android TV instalado em `/system/app/MaterialFiles/MaterialFiles.apk`.
2. **Aptoide TV Oficial**: Loja de aplicativos para Android TV pré-instalada em `/system/priv-app/AptoideTV/AptoideTV.apk`.

---

## 5. Estratégia de Testes & Verificação

1. **Testes Unitários de Componentes**:
   - Testar o ViewModel do `WanboSettings` com D-Pad navigation, seleção de abas e parsing de intents.
   - Testar o formatador de memória RAM e parsing de `/proc/meminfo`.
2. **Testes de Integração de Sistema**:
   - Validar que a emissão de `android.settings.SETTINGS`, `WIFI_SETTINGS` e `BLUETOOTH_SETTINGS` abre a respectiva aba no `WanboSettings`.
   - Validar que o botão HDMI continua alternando para a entrada de vídeo sem o ícone do Live TV visível.
3. **Integridade da ROM & Limites de Sparse Chunks**:
   - Verificar que a remoção do `TvSettingsPlus` e adição do `WanboSettings`, `MaterialFiles` e `AptoideTV` mantém o total de blocos alocados dentro do orçamento do Ext4 (`384.000` blocos).
   - Validar que o Chunk 9 permaneça abaixo de 34.062.336 bytes.
   - Validar CRCs do cabeçalho e rodapé do binário `WanboLightOS_v1.0.bin`.
