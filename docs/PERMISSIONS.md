# Permissões — o que cada uma habilita e o que degrada sem ela

Nenhuma permissão é pedida na abertura do app. Todas são solicitadas no momento em que o recurso é
usado, com explicação na tela, e todas podem ser negadas sem impedir o app de abrir.

## Obrigatórias para o recurso principal

| Permissão | Concedida por | Habilita | Sem ela |
|---|---|---|---|
| `SYSTEM_ALERT_WINDOW` ("exibir sobre outros apps") | Tela do sistema | Orbe/MenuHub, HUD, controles virtuais e FX de tela | O app continua funcionando como central/launcher; os toggles de overlay ficam desabilitados e a central de permissões mostra o estado real |
| `POST_NOTIFICATIONS` (API 33+) | Diálogo runtime | Notificação de serviço com ação **Ocultar** para cada overlay | Os serviços continuam em primeiro plano (o sistema mostra a notificação mínima); as ações de parar pela notificação somem |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Declaradas no manifest | Manter as sobreposições vivas com a tela de outro app em primeiro plano | — (não são permissões de usuário) |

Cada serviço declara `android:foregroundServiceType="specialUse"` com
`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` descrevendo exatamente o que hospeda (painel de comandos
flutuante, HUD de telemetria, gamepad que injeta toques, FX translúcido) — nada de tipo genérico.

## Injeção de entrada (controles virtuais)

Exatamente **um** destes dois caminhos é necessário:

| Caminho | Concedido por | Como injeta | Observações |
|---|---|---|---|
| **Shizuku** (`rikka.shizuku.permission.API_V23`) | App Shizuku (ADB sem fio ou root) | `Shizuku.newProcess("sh -c input tap|swipe|keyevent …")` com identidade shell | Preferido: funciona com o jogo em primeiro plano, permite `dumpsys SurfaceFlinger --latency` (FPS real do jogo), `am kill-all`, escalas de animação e `am force-stop` |
| **AccessibilityService** | Tela de acessibilidade do sistema | `dispatchGesture()` para toques/swipes e `performGlobalAction()` para voltar/início/recentes/travar | Sem root e sem Shizuku; é o fallback declarado no manifest com `accessibility_service_config.xml` |

Sem nenhum dos dois, o pad mostra **"Nenhum backend de injeção"** e abre a central de permissões —
ele nunca finge que injetou.

O serviço de acessibilidade **não lê conteúdo de tela**: `canRetrieveWindowContent="false"`,
`canRequestFilterKeyEvents="false"`, nenhum `flagRetrieveInteractiveWindows` e o único tipo de evento
declarado é `typeWindowStateChanged`. Ele existe só para `dispatchGesture()` e
`performGlobalAction()` — nada é coletado, armazenado ou enviado.

## Otimizador (todas opcionais)

| Permissão | Habilita | Sem ela |
|---|---|---|
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ação `battery`: abre o diálogo do sistema para isentar o app | A ação reporta "precisa de isenção" e o restante roda normalmente |
| `ACCESS_NOTIFICATION_POLICY` | Ação `dnd`: liga/desliga o modo foco | Idem, com mensagem específica |
| `PACKAGE_USAGE_STATS` | Listar apps em primeiro plano para o relatório e candidatos a force-stop | O relatório omite essa parte |
| `QUERY_ALL_PACKAGES` | Grade de apps instalados no launcher e candidatos a force-stop | Só apps com `<queries>` declaradas aparecem |

O Shizuku também cobre as ações `kill_bg`, `anim_scales` e `force_stop`. As escalas de animação
anteriores são gravadas em `AppSettings.storedAnimationScales` e devolvidas por **Restaurar**.

## Rede

| Permissão | Uso |
|---|---|
| `INTERNET` | Sonda de latência TCP do HUD (host configurável, desligável) e leitura do Shizuku |
| `ACCESS_NETWORK_STATE` | Saber se há rede antes de medir ping; throughput por `TrafficStats` |

Nenhum dado de telemetria sai do aparelho: não há analytics, crash reporting remoto nem backend
próprio. As únicas saídas de rede são a sonda de ping e o deep link do Roblox.

## Armazenamento e captura

- `screencap` (comando "Capturar tela") é executado **pelo shell via Shizuku** e grava em
  `Pictures/DynabloxL`; sem Shizuku o comando reporta indisponível. O app **não** pede
  `READ/WRITE_EXTERNAL_STORAGE` e **não** usa `MediaProjection` — por isso o FX de tela não captura
  nada: ele apenas adiciona luz (vinheta, tom, granulação, scanlines) sobre os outros apps, e a
  interface diz isso explicitamente.

## Ícone adaptativo e `<queries>`

`<queries>` declara `com.roblox.client`, intents `MAIN/LAUNCHER` e os schemes `roblox://` e
`https://`, o mínimo para detectar o cliente, listar apps e abrir o deep link
`roblox://experiences/start?placeId=<id>`.

---

### Resumo honesto

- Sem overlay: nada flutua, mas o app é uma central completa (launcher de Roblox, biblioteca,
  otimizador, shader lab, configurações).
- Sem Shizuku: FPS do jogo cai para o Choreographer (mede só o próprio app — e o HUD mostra a
  fonte), injeção vai para acessibilidade, e três ações do otimizador ficam indisponíveis.
- Sem acessibilidade **e** sem Shizuku: o pad não injeta e avisa.
- Nada é simulado: cada leitura vem de uma fonte nomeada na tela.
