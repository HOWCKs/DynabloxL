# DynabloxL — MenuHub

**Launcher flutuante focado em Roblox, com HUD de desempenho, otimizador real, laboratório de shaders GLES2 e controles virtuais que injetam toque de verdade.**

Interface 100% desenhada à mão em `Canvas` (metal escovado, vidro fumê, cerâmica, borracha, biséis de cromo, anéis de LED, mostradores LCD) — sem bitmaps, sem `ImageView` de ícone "pronto", sem glassmorphism exagerado. Tudo é tema claro/escuro, escala de interface, `reduce-motion` e acessível por TalkBack.

> **Estado:** MVP funcional completo. O build de APK é feito pelo GitHub Actions (`.github/workflows/android-ci.yml`) e publicado como artefato instalável a cada push.

---

## O que já funciona de verdade

### 1. MenuHub flutuante (orbe + arco radial + painel modular)
- **Orbe** esquelomórfico arrastável, com anel de LED que respira conforme a telemetria e badge de estado.
- **Toque** → arco radial com até 8 satélites (`IconTile`) posicionados para o lado livre da tela, sobre uma dobradiça usinada desenhada em `Canvas`.
- **Satélite "grid"** → painel modular com **busca real** (com normalização de acentos e pontuação), **categorias**, **favoritos** (pin por toque longo), **recentes** e grid de comandos.
- Janela dimensionada exatamente à superfície visível: o toque fora atravessa para o jogo e volta como `ACTION_OUTSIDE` → o painel fecha.
- ~30 comandos registráveis (`CommandRegistry`) — o mesmo catálogo alimenta o arco, o painel, a tela inicial e as ações de notificação.

### 2. HUD de desempenho flutuante
Três layouts sobre o mesmo fluxo de telemetria: **mini** (faixa com FPS + LEDs), **full** (cluster com FPS, tempo de quadro, quadros perdidos, gauges de CPU/RAM, bateria, temperatura, rede, ping, sessão) e **graph** (sparkline de FPS ao vivo + médias).
- FPS por **Choreographer** ou por **SurfaceFlinger** (`dumpsys SurfaceFlinger --latency` via Shizuku) — a fonte real é exibida no rodapé, nunca implícita.
- CPU por `/proc/stat`, RAM por `ActivityManager`, bateria/temperatura por `BatteryManager`, térmico por `PowerManager`, rede por `TrafficStats`, ping por TCP connect.
- Arrastável (posição persistida), toque troca o modo, intervalo de atualização configurável (250 ms – 3 s).

### 3. Otimizador (ações reais e reversíveis)
`battery` (isenção de otimização), `cache`, `kill_bg` (`am kill-all`), `anim_scales` (desliga e **guarda** as escalas anteriores), `force_stop` (lista de apps escolhidos pelo usuário), `dnd` (modo foco), `perf_hint` (`PerformanceHintManager`, API 31+) e `thermal`.
- Cada entrada declara seu requisito (Shizuku / isenção de bateria / política de notificação / API 31).
- O relatório mostra **antes × depois medido** (RAM livre, CPU, duração) e a mensagem real de cada ação — nunca um número prometido.
- Botão **Restaurar** devolve o que foi alterado.

### 4. Shader Lab (GLES2) + FX de tela honesto
- 7 presets em **GLSL ES 1.00** (CRT, Bloom, Chroma, Noir, VHS, Thermal, Vignette) compilados **em tempo de execução** sobre uma cena procedural animada; o log do driver é exibido quando a compilação falha, e a taxa de renderização é medida.
- **FX de tela** (sobreposição): vinheta, tom, granulação e scanlines, com janela `FLAG_NOT_TOUCHABLE` (clique atravessa).
- Honestidade de escopo: sem captura de tela, uma sobreposição só pode **adicionar luz** — é exatamente isso que ela faz, e o texto deixa claro.

### 5. Controles virtuais com injeção real
- 5 presets (`fling`, `combat`, `roleplay`, `driving`, `minimal`) em **duas janelas** (cluster esquerdo/direito), para o resto da tela continuar jogável.
- 10 gestos: `tap`, `hold_short`, `hold_long`, 4 swipes e `KEY_BACK`/`HOME`/`RECENTS`.
- Backend **Shizuku** (`input tap/swipe/keyevent` com identidade shell) com fallback **AccessibilityService** (`dispatchGesture`) — a latência medida aparece na notificação e na mesa de controles.
- Cada tecla é testável individualmente, com a coordenada normalizada de destino visível.

---

## Build

```bash
./gradlew :app:assembleDebug          # APK em app/build/outputs/apk/debug/
./gradlew testDebugUnitTest           # testes unitários
```

| | |
|---|---|
| Pacote | `com.dynablox.launcher` |
| minSdk / targetSdk | 26 / 34 |
| Kotlin / AGP / Gradle | 1.9.24 / 8.5.2 / 8.7 (JDK 17) |
| UI | Views + `Canvas` (sem Compose, sem bitmaps de material) |
| DI | `AppContainer` manual (um grafo por processo) |

**CI/CD:** cada push roda a matriz `debug` + `release` (compilação, testes unitários, assemble), publica o artefato `DynabloxL-<versão>-<variante>.apk` com SHA-256 e, em tags `v*`, cria um GitHub Release. Falhas viram **anotações** no check run com o trecho relevante do log.

---

## Permissões (e por quê)

| Permissão | Uso |
|---|---|
| `SYSTEM_ALERT_WINDOW` | As quatro sobreposições (orbe, HUD, pad, FX). |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Serviços que hospedam as sobreposições, cada um com notificação e ação **Ocultar**. |
| `POST_NOTIFICATIONS` | Notificações de serviço com ação de parada. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Sonda de latência TCP do HUD. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ação `battery` do otimizador (com diálogo do sistema). |
| `ACCESS_NOTIFICATION_POLICY` | Ação `dnd` (modo foco). |
| `PACKAGE_USAGE_STATS` | Listar apps em primeiro plano no relatório. |
| `QUERY_ALL_PACKAGES` | Grade de apps instalados no launcher. |
| `rikka.shizuku.permission.API_V23` | Identidade shell para injeção, FPS via SurfaceFlinger e ajustes. |

Nenhuma permissão é obrigatória para abrir o app: sem overlay ele funciona como launcher/central; sem Shizuku a injeção cai para acessibilidade; sem nenhuma das duas, o pad avisa em vez de fingir.

Detalhes completos em [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md).

---

## Documentação

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — módulos, fluxo de telemetria, modelo de janelas, injeção e pipeline GL.
- [`docs/DESIGN.md`](docs/DESIGN.md) — sistema de design esquelomórfico: materiais, tokens, movimento, acessibilidade.
- [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md) — cada permissão, o que habilita e o que degrada sem ela.

---

## Roadmap

- [ ] Reordenar comandos do arco por arrasto (a ordem já é persistida em `hubOrder`).
- [ ] Editor visual de mapeamento de teclas (arrastar o alvo sobre um print do jogo).
- [ ] Widget de Home Screen com telemetria resumida.
- [ ] Perfis por jogo (preset de pad + FX + otimizações por pacote).
- [ ] Testes instrumentados das sobreposições (`androidTest`).

---

*DynabloxL não é afiliado, endossado ou relacionado à Roblox Corporation. "Roblox" é marca de Roblox Corporation; este projeto apenas usa o esquema de deep link público `roblox://experiences/start?placeId=`.*
