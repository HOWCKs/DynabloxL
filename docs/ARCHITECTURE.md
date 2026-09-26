# Arquitetura — DynabloxL

Módulo único (`:app`), pacote `com.dynablox.launcher`. Sem Compose, sem codegen de DI, sem
biblioteca de gráficos: **Views + `Canvas` + framework**. A regra do projeto é que cada recurso do
MVP exista de verdade — se algo não pode ser feito sem uma permissão, a UI diz isso em vez de simular.

---

## 1. Camadas

```
ui/            Activities (scaffold compartilhado em DbxActivity)
menu/ hud/ fx/ controls/ shader/   Superfícies (views compostas) — vivem em janelas de overlay
service/       4 Services foreground que hospedam as superfícies
core/          Domínio: settings, comandos, telemetria, roblox, shizuku, notificação, overlay
design/        Sistema de design: tokens, materiais, texturas, movimento, widgets
di/            AppContainer (grafo manual, um por processo)
optimize/      Ações reais de otimização
```

Regra de dependência: `design/` não conhece `core/`; `core/` não conhece `ui/`; as superfícies
(`menu`, `hud`, …) conhecem `core` + `design`; `service` e `ui` conhecem tudo abaixo delas.

## 2. Grafo de dependências

`di/AppContainer` é criado uma vez por processo e exposto por `DynabloxApp.containerOf(context)`.
Tudo é `val` eager barato ou `by lazy` caro (`optimizer`), e o container também é o dono do escopo
de aplicação (`Dispatchers.Main.immediate`) usado por comandos assíncronos.

```
settings ── notifications ── device ── shizuku ── perf ── roblox
   │                                        │
   └── windows ── overlays ── injector ─────┴── commands ── optimizer
```

`AppSettings.changes: StateFlow<Int>` é o barramento de reatividade: cada overlay coleta esse fluxo e
se re-pinta/re-posiciona **sem recriar janela** (não existe Activity para recriar ali).

`SkeuoTheme.sync(settings)` mantém os singletons de design (`Dimens.factor`, `Motion.reduceMotion`,
`translucent`, `shadowIntensity`) coerentes com as preferências — é isso que faz a "escala de
interface" valer também dentro das sobreposições.

## 3. Modelo de janelas de sobreposição

Toda janela usa `TYPE_APPLICATION_OVERLAY` com:

```
FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH | FLAG_LAYOUT_IN_SCREEN | FLAG_HARDWARE_ACCELERATED
(+ FLAG_NOT_FOCUSABLE quando não há campo de texto)
```

Consequências deliberadas:

1. **Toque fora atravessa** para o app de baixo e volta como `MotionEvent.ACTION_OUTSIDE`, que o
   MenuHub usa para colapsar. Nada de "dim behind" permanente nem de modal invisível.
2. **A janela tem exatamente o tamanho da superfície visível.** `MenuHubView.computeGeometry()`
   devolve `(largura, altura, orbX, orbY)` por estado (COLLAPSED / ARC / PANEL); o serviço posiciona
   a janela de modo que o orbe fique onde o usuário o deixou, clampando na tela. Por isso abrir o
   painel não cria uma janela full-screen que engole o jogo.
3. **`FLAG_NOT_FOCUSABLE` só é removido no estado PANEL**, porque só ali existe IME (campo de busca).
   Janela focável rouba o botão voltar — e o voltar é tratado explicitamente em `dispatchKeyEvent`.
4. **FX de tela é `FLAG_NOT_TOUCHABLE`**: clique atravessa sempre.
5. **Controles virtuais usam duas janelas** (cluster esquerdo e direito), nunca uma full-screen —
   uma janela cobrindo a tela inteira bloquearia o jogo.
6. Blur real de fundo (`setBlurBehindRadius`) é aplicado só quando `isCrossWindowBlurEnabled`
   (API 31+); caso contrário o material é desenhado sem depender disso.

Cada serviço é `foregroundServiceType="specialUse"` com `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` descritivo
e uma notificação que sempre traz a ação **Ocultar** — nenhuma sobreposição pode ficar presa.

## 4. Telemetria

`core/perf/Samplers.kt` → `PerfEngine` → `StateFlow<PerfSnapshot>` → `HudView` / `OrbView` /
notificação.

| Dado | Fonte real |
|---|---|
| FPS | `Choreographer.FrameCallback` **ou** `dumpsys SurfaceFlinger --latency <layer>` via Shizuku |
| Tempo de quadro / quedas | derivado dos timestamps acima |
| CPU | `/proc/stat` (delta entre amostras), por núcleo em `/sys/devices/system/cpu/*` |
| GPU | nós sysfs conhecidos (`kgpu`, `mali`, `adreno`) — `null` quando não existe |
| RAM | `ActivityManager.MemoryInfo` |
| Bateria / temperatura | `BatteryManager` + sticky broadcast |
| Térmico | `PowerManager.currentThermalStatus` (API 29+) e listener térmico |
| Rede | `TrafficStats` (delta) |
| Ping | `LatencyProbe` (TCP connect ao host configurado) |

`PerfSnapshot` guarda **a fonte do FPS** (`FpsSourceKind` + nome da layer) e o HUD a exibe. Quando
não há fonte, o mostrador mostra `--`, não `0`. O `HudView` mantém o histórico do sparkline
localmente (anel de 96 amostras), então o modo graph não depende de buffer global.

O `PerfEngine` só roda enquanto o HUD está vivo; `sessionStarted()` o liga automaticamente ao abrir
o Roblox quando `hudAutoStartWithSession` está ativo.

## 5. Comandos

`Command` é **dado, não tela**: `id`, títulos, ícone, categoria, palavras-chave, tint, requisito,
`radial`, `pinnable` e `action: suspend (CommandContext) -> Unit`.

`CommandRegistry` monta o catálogo estático (~30 comandos) + comandos dinâmicos de apps instalados,
e oferece `radialCommands()`, `visibleCommands()`, `byCategory()`, `favorites()` e `search()`.
A busca normaliza acentos (`Normalizer` NFD), pontua prefixo > início de palavra > substring >
palavra-chave > descrição, e dá bônus para favoritos — por isso "otim" encontra "Otimizar" e
"hud" encontra "Monitor de desempenho".

`CommandContext` carrega `context`, `container` e um `CommandFeedback`. Existem dois sumidouros de
feedback: o **pill inline** do painel (`HubPanel.show`) e o **toast** do container — o mesmo comando
funciona no arco (sem painel visível) e no painel (com status contextual).

## 6. Injeção de entrada

`controls/InputInjector` decide o backend por `settings.controlsBackend`:

- **SHIZUKU** — `Shizuku.newProcess(arrayOf("sh", "-c", "input tap|swipe|keyevent …"))`, com a
  identidade shell; `hold` vira `input swipe` no mesmo ponto com duração.
- **ACCESSIBILITY** — `DynabloxAccessibilityService.dispatchGesture()` (sem root), com
  `willContinue = false` e callback de conclusão.
- **NONE** — o resultado é `InjectionResult.unavailable(mensagem)`; a UI mostra o motivo e oferece a
  central de permissões. Nada é injetado "de mentira".

Cada chamada mede `latencyMs` e alimenta um `StateFlow` de latência média móvel, exibido na
notificação do serviço e na mesa de controles. Ações globais (voltar/início/recentes/travar) usam
`AccessibilityService.performGlobalAction` quando o serviço está ativo.

## 7. Otimizador

`Optimizer` guarda `Definition`s privadas (`apply` / `restore` opcionais). `run(selected)` executa em
`Dispatchers.IO`, mede `Metrics` antes e depois, espera 700 ms para o sistema assentar e devolve um
`Report` com `entries`, `appliedCount`, `failedCount`, `ramDeltaMb` e `durationMs`.

Ações reversíveis gravam o valor anterior em `AppSettings` (ex.: `storedAnimationScales`) e só são
restauradas por `restoreAll()` — o app nunca esconde o que mudou. `hintSession` é tipado como `Any?`
para que a classe `PerformanceHintManager` (API 31+) nunca seja resolvida em aparelhos mais antigos.

## 8. Pipeline GL (Shader Lab)

```
ShaderPresets (HEADER + effect por preset)  →  ShaderLabRenderer  →  GLSurfaceView (GLES2)
        ↑                                            ↓
   AppSettings.shaderParam(id, default)      onCompileError / onPresetReady / onRenderFps
```

O `HEADER` declara os uniforms (`uTime`, `uResolution`, `uAmount`, `uParamB`, `uParamC`) e uma cena
procedural animada (céu, horizonte, blocos, sol, grade) — todos os presets fazem pós-processamento
dessa cena. Compilação e link são verificados com `GL_COMPILE_STATUS`/`GL_LINK_STATUS`; em falha o
info log do driver vai para a UI e o renderer cai para o preset mais simples. `uTime` avança por
delta de relógio monotônico × `timeScale`, então a animação não depende da taxa de quadros.

## 9. Testes e CI

Testes unitários (JVM, sem Robolectric) cobrem o que é puro e visível ao usuário: formatação
(`Fmt`), o contrato de telemetria (`PerfSnapshot`), geometria dos presets de pad
(`ControlPresets`), integridade estrutural do GLSL (`ShaderPresets`) e taxonomia de comandos
(`CommandCategory`).

O workflow `android-ci.yml` roda a matriz `debug`/`release` com `compile…Kotlin testDebugUnitTest
assemble…`, publica o APK + SHA-256 como artefato e, em tag `v*`, cria Release. Em falha, um passo
Python converte o log em **anotações de check run** (`::error title=…`) — assim o motivo aparece na
página do Actions sem precisar baixar o log.
