# Sistema de design — DynabloxL

Direção: **esqueuomorfismo refinado de instrumento**, não "flat com sombra" nem neon cyberpunk.
A referência mental é hardware técnico premium — painel de titânio, knob de borracha usinada,
mostrador LCD, anel de LED — com legibilidade e desempenho de app moderno.

Tudo é desenhado em `Canvas`. Não há PNG/WebP de material, não há `elevation` como substituto de
profundidade e não há blur de vidro empilhado sobre blur de vidro.

---

## 1. Paleta e tokens

Dois temas (`Theme.DynabloxL` escuro, `Theme.DynabloxL.Light` claro) expostos como 22 atributos
`dbx*`, resolvidos uma única vez por `ThemeTokens.from(context)` — **nunca dentro de `onDraw`**.

**Escuro (padrão)** — grafite / carvão / titânio, petróleo e azul elétrico, branco gelo, prata
escovada, âmbar, esmeralda e vermelho profundo:

| Token | Cor | Uso |
|---|---|---|
| `surfaceBase` | `#0E1215` grafite | fundo de janela |
| `surfaceRaised` | `#151A1E` carvão | painéis e teclas |
| `surfaceInset` | `#0A0E10` | poços, trilhos, LCD |
| `surfaceGlass` | `#CC11171A` | vidro fumê |
| `textPrimary` | `#EAF2F6` gelo | títulos e dígitos |
| `textSecondary` | `#B4BFC6` prata | corpo |
| `textFaint` | `#7C8A93` | legendas, unidades |
| `accent` | `#34A9FF` elétrico | foco, LED ativo, seleção |
| `accentDeep` | `#1C6280` petróleo | gradientes e sombras coloridas |
| `success` / `warning` / `danger` | `#3CC794` / `#E9A63C` / `#D24A3C` | semáforo de estado |
| `metalLight/Mid/Dark` | `#4A555C` / `#232A2F` / `#151A1E` | corpo de metal escovado |
| `chromeHi` / `chromeLo` | `#F4F8FA` / `#5D6A72` | bisel, hairline, reflexo |

**Claro** — marfim `#F6F3EC`, cinza quente `#E7E2D8`, prata `#CFD5D9`, azul profundo `#16324A` e
acento `#1A6CA6`, com as mesmas variantes semânticas em versão "deep" (`#1F8F66`, `#B77B1E`,
`#A93A2E`) para manter contraste em fundo claro.

Regras de uso:
- Acento é **raro**: foco, LED aceso, valor selecionado. Nada de colorir ícones aleatórios.
- Estado usa só as três cores semânticas; nunca uma quarta.
- Texto sobre material é sempre `Tint.onColor(...)` ou um token de texto — nunca branco puro sobre
  metal claro.

## 2. Materiais

`enum Material { CERAMIC, METAL, GLASS, RUBBER, INSET }` + `MaterialOptions(shadow, bevel, texture,
specular, innerShade, tint, tintAmount, opacity)`.

`Materials.paint()` desenha, nesta ordem:

1. **corpo** — `LinearGradient` vertical entre `metalLight/metalMid/metalDark` (metal) ou
   `surfaceRaised → surfaceBase` (cerâmica), com sweep de cromo em knobs;
2. **microtextura** — bitmap procedural clipado no corpo: `brushed` (metal), `noise` (cerâmica),
   `microDot` (borracha), `knurl` (anel serrilhado de knob);
3. **sombreamento interno** — recessão de 2,5–5 dp conforme o material (poço de LCD é o mais fundo);
4. **reflexo especular** — 4 % a 22 % de `chromeHi` no terço superior, mais forte em vidro;
5. **bisel** — hairline clara em cima, escura embaixo (`Skeuo.drawBevel`);
6. **sombra de contato** — `Skeuo.drawContactShadow`, multiplicada por `SkeuoTheme.shadowIntensity`.

Cada view tem um `MaterialState` que **cacheia os shaders** e só os reconstrói quando material,
tamanho, raio ou tokens mudam (`isStale`). É isso que permite 60 fps com gradiente + textura + bisel
em várias views simultâneas. `Textures.trim()` libera os bitmaps sob pressão de memória (chamado
pelo otimizador e por `onTrimMemory`).

## 3. Componentes

| Widget | O que desenha |
|---|---|
| `OrbView` | núcleo do MenuHub: corpo de vidro/metal, anel de LED que respira, arco de progresso de telemetria, ícone gravado, badge, estados IDLE/ACTIVE/BUSY/WARNING/ERROR |
| `IconTile` | tecla de satélite: cerâmica com ícone tintado, estrela de favorito, rótulo elipsizado, compressão ao pressionar |
| `PhysicalButton` | tecla física (ROUND / ROUNDED_SQUARE / CAPSULE) com ícone, rótulo, sub-rótulo, LED e afundamento real |
| `ToggleSwitch` | trilho usinado + pomo de metal que desliza com detent e LED de estado |
| `RotaryKnob` | knob serrilhado (`knurl`), indicador cromado, detentes táteis, escala e leitura digital |
| `FaderSlider` | trilho em poço, cap de metal escovado, escala e valor |
| `LedIndicator` | LED de 6–8 dp com halo, aro metálico e legenda em caixa alta |
| `DigitalReadout` | mostrador LCD: poço, dígitos monoespaçados, unidade, etiqueta gravada |
| `Sparkline` | traço de gráfico com preenchimento, limiar e média |
| `GaugeView` | arco de instrumento com zonas coloridas, ponteiro e leitura digital |
| `SkeuoPanel` | container que pinta um dos cinco acabamentos atrás dos filhos |
| `RadialArc` | hinge usinado + raios que conectam os satélites ao orbe, com stagger por índice |
| `SearchField` | poço de vidro fumê com lupa gravada, anel de foco luminoso e tecla física de limpar |

## 4. Movimento

`Motion` centraliza curvas e durações:

- `standard` (0.2, 0, 0, 1) para transições de estado;
- `spring` (0.30, **1.42**, 0.48, 1) para a expansão radial — overshoot subamortecido curto;
- `decelerate(1.9f)` para painéis chegando;
- `precise` (0.4, 0, 0.2, 1) para pressão de tecla e para `reduceMotion`.

`duration(base)` é escalado: `reduceMotion` corta para ≤ 80 ms, `INSTANT` vai a 0 ms, `PRECISE`
multiplica por 0,72. Microinteração base: `Motion.press(view, pressed)` afunda o corpo ~4 % e
reduz a sombra — cada tecla responde antes do clique ser confirmado. Animações ambientes (respiração
do LED, varredura do LCD) só existem quando `Motion.ambientAllowed()`.

## 5. Escala e acessibilidade

- **Escala de interface** (`densityScale` 0,85–1,40) é aplicada por `Dimens.factor`, e **todo** dp do
  design system passa por `Dimens.dp(context, value)` — inclusive dentro das sobreposições, que não
  têm Activity para recriar.
- **Reduce motion** remove respiração de LED, varreduras e overshoot.
- **Foco visível**: `SkeuoWidget.drawFocusRing()` pinta anel de acento em qualquer componente
  focado; teclas do pad e tiles têm anel próprio.
- **TalkBack**: `contentDescription` em tudo que é acionável, `a11yLabel` nos switches, LEDs com
  legenda textual, e o HUD publica `hud_a11y_live` com FPS, CPU e estado térmico a cada amostra.
- **Contraste**: tokens de texto foram escolhidos para ≥ 4,5:1 sobre o material correspondente em
  ambos os temas; o `LedIndicator` nunca é o único canal de informação (há legenda).
- **Superfícies translúcidas** e **profundidade de sombra** são ajustes do usuário — quem precisa de
  opacidade total desliga o vidro fumê e ganha cerâmica sólida.

## 6. O que este design system evita

- Glassmorphism empilhado (no máximo **uma** superfície translúcida por nível de profundidade).
- Sombra dura sem origem de luz: toda sombra tem direção consistente (luz vinda de cima, ~8°).
- Reflexo estourado: especular nunca passa de 22 % de alfa.
- Texto ilegível sobre textura: rótulos são gravados com hairline de contraste, não apenas pintados.
- Neon e gradientes multicoloridos: a paleta tem **um** acento por tema.
- Ícones de biblioteca: os 64 drawables são vetores autorais, traço 1,6 dp, cantos de 2 dp,
  desenhados para o mesmo grid.
