# ⚽ Simulador SSL 2D

Simulador bidimensional da categoria **Small Size League** (RoboCup), escrito em Java
(Swing/Graphics2D). Nasceu da curiosidade de entender a matemática por trás de simuladores
como o `grSim`, e hoje cumpre o mesmo papel que ele: simula a física, mostra numa janela e
publica na rede pelo protocolo oficial da liga.

> **Primeira vez aqui? Leia o [GUIA.md](GUIA.md).** Ele ensina a rodar, a ajustar a física e a
> gerar dataset, com imagens. Este README é o outro documento: explica **por que** cada decisão
> de projeto é o que é, e é o que se lê antes de *mudar* alguma coisa.

## Como rodar

```bash
./tools/build.sh                    # javac puro, sem Gradle e sem rede

java -cp "out/production/SSL:lib/*" Main             # janela + publica visão na rede
java -cp "out/production/SSL:lib/*" Main --sem-rede  # janela, offline

# dataset sem janela, ≈650× tempo real
java -cp "out/production/SSL:lib/*" Main --headless --cenario chute-no-gol \
     --duracao 300 --saida logs/corrida1

java -cp "out/production/SSL:lib/*" Main --ajuda            # todas as opções
java -cp "out/production/SSL:lib/*" teste.Autoteste         # invariantes da física
java -cp "out/production/SSL:lib/*" teste.AutotesteRede     # protocolo de rede
```

O modo headless não publica de propósito: ele roda centenas de vezes mais rápido que o tempo
real, e despejar isso num multicast só inundaria a rede.

As dependências são três, versionadas em `lib/` junto com o Java gerado a partir dos `.proto`
(em `src/proto/`): `protobuf-java`, e o `flatlaf` mais sua fonte JetBrains Mono, que são o que
faz a janela ter a mesma cara em qualquer sistema (veja [Aparência](#aparência)). As três são
jar único, sem dependência transitiva. Um clone limpo compila offline, sem Gradle, sem Maven e
sem baixar nada. Se algum jar sumir, `./tools/build.sh` busca de volta sozinho. O
`./tools/gerar-proto.sh` só precisa rodar quando o protocolo da liga mudar.

**IntelliJ:** o `SSL.iml` já declara `lib/` como biblioteca do módulo, apontando para o
diretório e não para o jar nominal, então trocar a versão não exige mexer nele. Se o IDE
reclamar de `package com.google.protobuf does not exist` depois de um `git pull`, é cache:
*File → Reload All from Disk*.

## Convenções

Tudo em **milímetros**, **radianos** e **segundos**, que são as unidades da SSL-Vision, para
que o log saia diretamente comparável a dados reais. Origem `(0,0)` no centro do campo, `+x`
para o gol amarelo, `+y` para a lateral superior. Campo padrão: **Divisão B** (9000 × 6000 mm).

Todas as dimensões vêm do regulamento: campo 9000 × 6000, gol 1000 × 180 com parede de 20 e
155 de altura, área de defesa
1000 × 2000, círculo central de raio 500, linha de 10, faixa externa de 300, robô de 180 mm de
diâmetro e 150 de altura, bola de 43 mm.

A bola é desenhada no tamanho real, sem piso de visibilidade. No zoom que enquadra o campo
inteiro isso dá 4,7 px de diâmetro, o que é pequeno mas fiel: ela é 0,5% do comprimento do
campo.

## Arquitetura

Cada pacote **declara** sua zona e de quem depende, no próprio `package-info.java`:

```java
/** <p>Zona: ESTAVEL. Depende de: core, model. */
package engine;
```

Isso não é enfeite: o `teste.Autoteste` lê essas linhas e o código, e falha quando os dois
divergem. Antes a arquitetura vivia só nesta seção, e prosa não impede ninguém de importar o
que não devia.

### As três zonas

|  | zona | o que é |
|---|---|---|
| **ESTAVEL** | `core` `model` `engine` `visao` `sim` `rede` `log` `view` | o simulador propriamente dito. Está pronto; quando algo quebra aqui, quase nunca foi você |
| **TRABALHO** | `app` `app.telas` `app.componentes` `app.fisica` | a janela |
| **EXTENSAO** | `demo` | onde se escreve roteiro de teste novo |

**ESTAVEL não pode depender de TRABALHO nem de EXTENSAO** — é o que garante que a física e a
rede não saibam que existe janela, e é por isso que o modo `--headless` roda sem abrir nenhuma.

### O grafo

```
core     →  (nada)                  Vec2, Angulo, SimClock
view     →  (nada)                  Estilo
model    →  core                    Bola, Robot, Equipe, Geometria, RobotCommand
visao    →  core, model             EstadoMundo, FonteDeVisao, CanalDeControle
engine   →  core, model             Mundo, FisicaBola, FisicaRobo, Colisoes, Evento
log      →  engine, model           Logger, LoggerArquivo, Json
sim      →  + core, log, visao      Simulacao, VisaoLocal, ControleLocal, ControladorExterno
rede     →  + proto, sim, visao     PublicadorVisao, ReceptorDeControle, ReceptorDeComandosRobo
demo     →  core, engine,           Cenarios, Roteiro, Passo, ExecutorDeCenario
            model, sim
app      →  model, rede,            Rede                       <- orquestrador
            sim, visao
  fisica →  core, engine, model     Ensaios, Ensaio, Trajetoria, Amostra, Vista
  componentes → app, app.fisica,    Campo, DialogoFisica, DialogoRede, PainelEnsaio
            core, model, rede, view, visao
  telas  →  + componentes, demo,    TelaJogo
            log
```

### Telas e componentes

`telas/` são janelas inteiras que montam um layout; `componentes/` são os pedaços que elas
montam, e a dependência é numa direção só. `app/fisica/` fica de fora dos dois de propósito: são
os ensaios que o diálogo plota, e isso é **conta, não desenho** — quem desenha é o
`PainelEnsaio`, que mora em `componentes/`.

Os nomes levam o tipo, como as implementações de estratégia do outro repositório:
`DialogoFisica`, `PainelEnsaio`, `TelaJogo`. Numa pilha de exceção dá para saber que a primeira
é modal e a segunda não.

### O snapshot

O `view` não lê o mundo vivo. Ele recebe uma `FonteDeVisao` e desenha o `EstadoMundo` que ela
entrega, um retrato imutável de um quadro. Cada `paintComponent` tira **um** retrato e usa do
início ao fim; ler a fonte várias vezes durante o desenho costuraria instantes diferentes.

O mesmo `EstadoMundo` é o que vai para a rede. Uma estrutura só serve ao desenho e à
publicação, então não há dois caminhos de leitura do mundo para manter em dia. Pelo mesmo
motivo, as duas portas que escrevem no mundo (a interface e o `SimulatorCommand` que chega
pela rede) passam pelo mesmo `CanalDeControle`.

O `EstadoRobo` não carrega o comando aplicado. O protocolo não tem campo para ele, então
guardar no snapshot só criaria um dado que nunca sai do processo. O comando de cada quadro
vai para o log, que é onde ele serve para alguma coisa.

## Rede

Portas e endereços são os oficiais, de propósito: o software de qualquer time da liga conecta
aqui como conectaria no grSim, sem alteração.

| | endereço | mensagem |
|---|---|---|
| visão (saída) | multicast `224.5.23.2:10006` | `SSL_WrapperPacket` |
| controle de simulação (entrada) | `:10300` | `SimulatorCommand` |
| comandos de robô (entrada) | `:10301` azul, `:10302` amarelo | `RobotControl` |

Uma saída e três entradas. O simulador não recebe visão de ninguém: ele é a fonte, e o que
aparece na janela vem direto do mundo simulado, não de um pacote que deu a volta pela rede. A
publicação está pendurada no tick, não no repaint, para que a visão saia uma vez por quadro
simulado independente da taxa de desenho.

Dá para mudar tudo isso por linha de comando (`--grupo`, `--porta-visao`, `--porta-controle`,
`--porta-azul`, `--porta-amarelo`) ou com o simulador rodando, no botão *Configurar...* do
painel Rede. Aplicar reabre os sockets, então quem estiver conectado cai e precisa reconectar.

A validação é em duas camadas de propósito. `ConfigRede.problema()` rejeita o que dá para
saber sem tocar no sistema: endereço fora da faixa multicast (que não daria erro, só faria o
pacote nunca chegar em ninguém), porta fora da faixa, duas escutas na mesma porta. Já "porta
ocupada por outro processo" só aparece na hora do bind, e nesse caso `Rede.reconfigurar()`
restaura a configuração anterior em vez de deixar o simulador mudo.

### O que o protocolo carrega, e o que não

`SSL_DetectionRobot` tem exatamente isto: `confidence, robot_id, x, y, orientation, pixel_x,
pixel_y, height`. Ele descreve o que uma **câmera** enxerga, não o que o simulador sabe.
Velocidade, comando aplicado e posse de bola não têm campo e não saem daqui. Quem consome
tipicamente reconstrói a velocidade com um filtro sobre quadros consecutivos, como faz todo
time da liga.

`pixel_x` e `pixel_y` são `required` no proto2 e um simulador não tem câmera; vão zerados,
como no grSim.

Três detalhes de tradução que não são 1:1:

* **unidades trocam**: visão em mm, `ssl_simulation_control` em metros. Errar dá mil vezes de
  diferença sem estourar exceção em lugar nenhum
* **chip**: no protocolo é `kick_speed` mais `kick_angle` em graus; aqui são dois campos
* **dribbler**: no protocolo é RPM; aqui é booleano

`MoveLocalVelocity{forward, left, angular}` bate exatamente com o `RobotCommand` daqui. Ter
escolhido o referencial local do robô desde o começo deixou essa parte como conversão de
unidade e mais nada. Dos três modos de movimento do `RobotControl`, só esse é tratado;
velocidade global e velocidade de roda ainda não.

### Quando o software de time está em outra máquina

O `MulticastSocket` publicava pela interface que o **sistema** escolhesse, e é justamente aí que
falha: numa máquina com VPN, Docker ou VirtualBox há uma dúzia de interfaces que aceitam
multicast e não levam a lugar nenhum. O lado que **recebe** já tratava isso — o cliente entra no
grupo em todas as interfaces — e o lado que envia tinha ficado para trás. `Interface de saída`,
em *Rede → Configurar…*, resolve; a lista mostra o IP ao lado do nome, e as com IPv4 vêm
primeiro, porque `utun3` e `awdl0` nunca são a resposta.

Mas escolher a interface não salva quando o problema é a **rede**, e frequentemente é: a ponte
do roteador entre Wi-Fi e cabo muitas vezes não repassa multicast, e rede de faculdade costuma
bloquear por política. Para isso existe `Tambem enviar para`: uma cópia de cada pacote vai por
**unicast** para os IPs listados, e unicast sai como qualquer UDP comum. O multicast continua
saindo — os destinos são adicionais, não substitutos, e o protocolo da liga segue intacto.

Do outro lado não é preciso mudar nada, e isso não é suposição: um socket multicast preso a uma
porta recebe unicast nela do mesmo jeito. O `teste.AutotesteRede` publica num grupo que ninguém
escuta e confere que o quadro chega assim mesmo.

## Motor de física

* **Passo fixo** (`SimClock`, 60 Hz por padrão). É o que torna o log reproduzível: o mesmo
  estado inicial e a mesma sequência de comandos geram exatamente os mesmos quadros,
  independente da carga da máquina ou do FPS de renderização.
* **Bola com atrito em duas fases.** Uma bola chutada não rola de imediato: desliza com
  atrito cinético (≈ 2,9 m/s²) até que a rotação alcance a translação, o que para uma esfera
  homogênea acontece em `v = 5/7·v₀`; a partir daí rola com atrito uma ordem de grandeza
  menor (≈ 0,49 m/s²). Sem as duas fases o chute longo fica visivelmente errado.
* **Chip com trajetória balística.** O chute carrega uma elevação (`anguloChute`), que
  reparte a velocidade entre plano e vertical. No ar não há atrito de rolamento, porque
  atrito de rolamento só existe em contato com o gramado; um chip que perdesse atrito
  enquanto voa cairia bem antes do alcance real. O instante do toque no chão é resolvido
  **dentro** do passo e não arredondado para a borda do quadro: a 60 Hz um chip percorre até
  77 mm de altura por quadro, e grudar o quique no fim do passo faria o alcance depender da
  taxa de simulação.
* **Robôs omnidirecionais** com saturação de velocidade e de aceleração, linear e angular.
* **Colisões** bola↔robô (capa circular truncada pela face plana do dribbler, resolvida no
  referencial local e por velocidade *relativa*), robô↔robô com impulso, paredes e **gol**.
  Bola acima dos 150 mm de teto do robô passa por cima, e o dribbler não alcança bola no ar.
* **Atuadores**: dribbler segura a bola contra a face; chutador plano e chip.

Os parâmetros ficam em `ParametrosFisica` e são gravados no cabeçalho do log, então uma
corrida sempre pode ser reproduzida com a física que a gerou. A troca entra no log como evento
`PARAMETROS_ALTERADOS`, então um log que atravessa o ajuste continua interpretável.

### Ajuste com prévia

Um slider marcado "restituição 0,50" não diz nada a ninguém. Por isso a janela de física
(botão *Configurar...* no painel lateral) traz, para cada parâmetro, um **ensaio que o isola**,
animado: fantasma cinza para o que está valendo no mundo, laranja para o que o slider propõe,
e o número que resume o efeito embaixo.

| parâmetro | ensaio | medida |
|---|---|---|
| atrito de rolamento | bola solta a 3 m/s | quanto rola |
| atrito de deslizamento | chute a 6 m/s | distância até virar rolamento |
| quique na parede | bola a 4 m/s na parede | velocidade de volta |
| quique no robô | bola a 4 m/s na casca | velocidade de volta |
| quique entre robôs | choque frontal a 1,5 m/s | velocidade de separação |
| restituição vertical | bola largada de 1 m | altura do primeiro quique |
| atrito do quique | chip a 5 m/s e 45° | alcance total |

Os dois traçados dividem a mesma faixa e o mesmo relógio, em vez de ficarem lado a lado:
assim dá para ver uma bola ultrapassar a outra, que é a comparação que interessa. O
enquadramento se ajusta aos dois, porque um limite fixo faria o traçado mais curto virar um
ponto quando o parâmetro está no extremo oposto.

Refazer um ensaio custa **0,08 ms**, então eles são recalculados a cada movimento do slider.
Cada um é uma simulação de um ou dois corpos por alguns segundos, rodando com passo de 1/240 s
para a medida ficar precisa.

Os ensaios são projetados para *discriminar*. Dois deles não discriminavam na primeira versão
e precisaram de conserto: o de deslizamento não terminava dentro do tempo de ensaio nos
valores baixos, e o de robô contra robô media o mesmo número para qualquer restituição porque
os dois **freavam antes de encostar**.

### O gol tem paredes

O gol era enfeite: `Geometria` guardava largura e profundidade, o desenho pintava um retângulo
atrás da linha de fundo e a física não sabia que ele existia — a bola atravessava o gol inteiro
e ia quicar na parede da faixa externa, 300 mm atrás. Agora são **três paredes por gol**, dois
postes e o fundo, com os 20 mm de espessura e os 155 mm de altura do regulamento. A bola que
entra pela boca fica lá dentro, chacoalhando; o robô entra atrás dela e para na face interna do
fundo.

`golProfundidade` é a profundidade **interna**, como no regulamento, então a pegada total do gol
é 180 + 20 = 200 mm — cabe nos 300 da faixa externa, com 100 mm de folga até a parede física.

O gol é o único obstáculo do campo fino o bastante para a bola **pular por cima dele em um
passo**: a parede tem 20 mm, a bola tem 43 de diâmetro e a 60 Hz ela percorre até 108 mm por
quadro. Resolver o contato pela posição de chegada, como se faz com o robô e com a parede
externa, deixaria um chute forte sair pelo fundo — num quadro a bola estaria dentro, no seguinte
já atrás da estrutura, sem nunca ter tocado nela. Por isso o contato com o gol é **varrido**: o
que se confronta com a parede é o segmento que a bola percorreu no passo, não o ponto onde ela
parou.

Pelo mesmo motivo a velocidade do quique é a do **instante do toque**, e não a do fim do passo.
O integrador cobra atrito do passo inteiro, inclusive do trecho depois do contato — trecho que a
bola nunca percorreu. Devolver esse pedaço custa uma linha e vale isto:

| | 60 Hz | 240 Hz | 2000 Hz |
|---|---:|---:|---:|
| sem a correção | 444 mm | 398 mm | 385 mm |
| com a correção | **384 mm** | **384 mm** | **384 mm** |

A parede externa ainda arredonda o quique para a borda do quadro, com o erro de dt que isso
carrega (≈ 17 mm no mesmo ensaio). São códigos separados de propósito: mexer no quique da parede
mudaria a física já gravada nos logs antigos.

A altura vai na visão, em `SSL_GeometryFieldSize.goal_height`, que o protocolo tem e antes saía
zerado. A **espessura da parede** não vai: não existe campo para ela, então quem consome a visão
sabe onde é a boca do gol e não sabe de que grossura é o poste. É mais um caso da assimetria de
sempre — o simulador sabe mais do que consegue contar.

Os 155 mm de altura são de verdade — acima deles não há gol nenhum, e é isso que faz um chip por
cima da trave continuar valendo. A parede externa é que segue infinitamente alta, então a bola
nunca se perde. Duas aproximações conhecidas: os cantos dos postes são quina viva, sem o
arredondamento da soma de Minkowski (a mesma simplificação já assumida na boca do dribbler), e
só o primeiro contato de cada passo é resolvido.

### Quique e giro

Um quique inverte a translação da bola mas **não o giro**: ela sai do contato girando ao
contrário do próprio movimento, e o atrito precisa parar e reverter esse giro antes de ela
voltar a rolar. Para uma esfera homogênea que chegava rolando, o deslize termina em
`v₀·(5e−2)/7`.

Com a restituição padrão de 0,5 isso deixa **7%** da velocidade de chegada, contra os 50% que
sobrariam ignorando o giro. A primeira versão ignorava, e era o que fazia a bola parecer
quicar demais. Chute de 6,5 m/s direto na parede:

| | volta da parede | assenta em |
|---|---:|---:|
| ignorando o giro | 4,17 m | 6,0 s |
| com o giro | **1,22 m** | **3,6 s** |

Esse termo tem uma sensibilidade que não aparece no número: perto de `e = 0,4` ele tende a
zero, então pequenos ajustes mudam muito o quanto a bola volta. De `0,50` para `0,59` a
velocidade de saída sobe 18%, mas a distância percorrida na volta sobe 58%. O padrão é
`restituicaoParede = 0.59`.

O mesmo vale para o contato com o robô. Duas simplificações: abaixo de `e = 0,4` a conta dá
negativo, ou seja o giro venceria e a bola voltaria em direção à parede, mas aqui ela apenas
para; e num quique de raspão, onde só uma componente inverte, a fórmula é aproximação.

Sobre os padrões de restituição: um robô de SSL é construído para **matar** a bola no
contato, não para devolvê-la, daí `restituicaoRobo = 0.35`.

O chute máximo em si continua nos **6,5 m/s da regra** e cruza os 9 m do campo em 1,90 s. No
chip, a saturação é sobre a velocidade **total** de saída, incluindo a componente vertical,
porque o limite da regra é sobre o quanto a bola sai do chutador e não sobre a projeção no
gramado.

`Bola.getZ()` é a altura do ponto **mais baixo** da bola, e não a do centro. Com essa
convenção "está no chão" é `z == 0` e "passa por cima de um robô" é `z >= Robot.ALTURA`, sem
somar ou subtrair raio em cada teste. Na rede vai a altura do centro (`z + RAIO`), para ser
coerente com `x` e `y`, que também são do centro.

Uma simplificação conhecida: a parede é tratada como infinitamente alta, então um chip nunca
sai do campo. Enquanto não houver árbitro para repor a bola, deixá-la escapar significaria
perdê-la para sempre. O gol, ao contrário, tem altura de verdade: por cima dele a bola passa.

## Quem move os robôs

Ninguém, por dentro. O simulador não tem lógica de jogo, igual ao grSim: sem um software de
time conectado nas portas de `RobotControl`, os robôs ficam parados na formação inicial. O
`ControladorExterno` é o único caminho pelo qual um robô se move, e aplica os comandos
recebidos antes do passo de física.

A formação inicial é uma **cruz com todos os robôs encarando o centro do campo**. Ela é
gerada em anéis a partir do centro da cruz (primeiro o robô do meio, depois os quatro braços,
depois o segundo anel), e não tabelada, o que a faz valer para qualquer quantidade sem lista
mágica. Os braços encolhem conforme o número de robôs para o anel mais externo continuar
dentro do próprio campo: sem isso, 11 robôs na Divisão A jogariam o último anel para fora da
linha de fundo e para dentro do campo adversário.

Consequência para o gerador de dataset: `--headless` grava um log de 12 robôs parados a menos
que algo esteja pilotando. Para isso existem os cenários de teste.

## Cenários de teste

Roteiros fixos que fazem o papel de um software de time conectado, para exercitar chutador,
chip e dribbler sem precisar de um. Escolhidos por `--cenario` ou pelo painel lateral.

| nome | o que faz |
|---|---|
| `chute-no-gol` | conduz a bola com o dribbler e chuta rasteiro no máximo da regra |
| `passe-com-chip` | chip por cima de um adversário, recebido por um companheiro |
| `conducao-com-roller` | frente, freada seca, ré, laterais e giro, tudo com a bola presa |

São **malha aberta**: nada olha para o estado do mundo para decidir o próximo passo, só uma
linha do tempo de ações. Isso mantém a corrida determinística (roda igual toda vez, o que
importa para gerar dataset) e evita devolver navegação ao simulador, que é do software que
joga. Os comandos saem pelo `ControladorExterno`, o mesmo ponto por onde entram os da rede,
então continua valendo que existe um único caminho pelo qual um robô se move.

A sequência da condução é escolhida para exercitar o roller, não para ficar bonita. Andar para
frente ou em círculo não prova nada: a casca do robô empurra a bola e ela acompanha por inércia
mesmo sem dribbler. O que separa "segurando" de "empurrando" é a freada seca e a marcha à ré.
Medido, com a mesma manobra:

| | após avançar | após a ré |
|---|---:|---:|
| roller ligado | 22 mm da boca | 22 mm |
| roller desligado | 22 mm da boca | 1660 mm |

Cada passo do roteiro vira um evento `CENARIO` no log. Sem isso, quem analisa o dataset teria
de adivinhar em que fase cada quadro caiu.

Todo cenário começa **recolhendo os robôs que não participam** para a própria linha de fundo.
A formação inicial é uma cruz com quatro robôs sobre o eixo X, exatamente por onde os cenários
mandam a bola: sem recolher, o chute bate num companheiro a 600 mm e volta. Dá para desligar
esse recolhimento na caixa ao lado do seletor, justamente para ver a interferência.

## Log

`--saida <dir>` produz quatro arquivos:

| Arquivo | Conteúdo |
|---|---|
| `meta.json` | geometria, parâmetros de física, equipes e `dt`, o suficiente para reproduzir a corrida |
| `ball.csv` | uma linha por quadro, com `z`, `vz` e `no_ar` |
| `robots.csv` | uma linha por robô por quadro (formato longo) |
| `events.jsonl` | um evento por linha |

O CSV de robôs é **longo**, e não largo com uma coluna por robô, porque a quantidade de robôs
muda em tempo de execução e um cabeçalho largo ficaria inválido no meio do arquivo. Para
filtrar: `df[(df.cor == "blue") & (df.id == 3)]`.

A amostra do quadro é gravada **antes** da integração, de propósito: a linha carimbada com
`t` contém o estado em `t` junto do comando que será aplicado no intervalo `[t, t+dt)`. É o
par (estado, ação) alinhado.

Eventos registrados: `PARTIDA_INICIADA`, `PARAMETROS_ALTERADOS`, `CENARIO`, `BOLA_REPOSICIONADA`,
`CHUTE`, `CHIP`, `POSSE_GANHA`, `POSSE_PERDIDA`, `COLISAO_BOLA_ROBO`, `COLISAO_ROBO_ROBO`,
`BOLA_PAREDE`.

A escrita acontece numa thread separada alimentada por fila limitada. Se o disco não
acompanhar, a física bloqueia em vez de descartar linhas: um log com buracos silenciosos é
pior do que uma simulação mais lenta.

### Ligando e desligando

Os dois streams têm custo muito diferente (o `robots.csv` é ~95% do volume), então dá para
gravar cada um separadamente e decimar o tracking. Eventos nunca são decimados: perder um
chute ou um gol para economizar disco não faz sentido.

```bash
--sem-log              # não grava nada
--sem-tracking         # só events.jsonl
--sem-eventos          # só os CSVs
--log-intervalo 6      # 1 quadro a cada 6, tracking a 10 Hz
```

Medido em 60 s de simulação com 6×6 robôs:

| Configuração | Volume | Velocidade |
|---|---:|---:|
| completo (60 Hz) | 4,9 MB | 295× |
| `--log-intervalo 6` (10 Hz) | 984 KB | 576× |
| `--sem-tracking` | 200 KB | 928× |
| `--sem-log` | zero | 980× |

Na interface os mesmos controles ficam no painel lateral e travam enquanto a gravação está
ativa, porque a configuração vale para a corrida inteira e o `meta.json` é escrito na
abertura. Ele registra quais streams foram gravados e a taxa efetiva (`hz_tracking`), então
quem lê o log sabe se o tracking foi decimado ou simplesmente não existe.

Trocar a composição das equipes durante a gravação é permitido: a mudança entra no log como
um evento `PARTIDA_INICIADA`, e o `meta.json` descreve a composição inicial da corrida.

## Interface

Zoom no scroll, arrasto move o campo. Ferramentas: posicionar a bola, chutar
arrastando (rasteiro ou chip a 45°) e inspecionar robô. O painel de cenário escolhe um dos
roteiros de teste e mostra o progresso do ciclo. Com a bola no ar, a janela desenha a
sombra no gramado e a bola sobe e cresce, ligadas por uma haste. Num campo visto de cima não
há profundidade para mostrar altura, e crescer junto com o deslocamento é o que distingue
"bola alta" de "bola deslocada"; a haste ancora visualmente onde ela vai cair. O painel de log liga e desliga a gravação a quente, escolhe os
streams e mostra o volume gravado em tempo real. O painel de física abre a janela de ajuste com prévia. O painel de rede mostra os contadores de pacote e abre a janela de
configuração de portas.

Arrastar com o **botão esquerdo** move o campo. Antes só o direito deslocava, e num trackpad de
MacBook não há botão direito para segurar — clique de dois dedos não se sustenta enquanto um
terceiro arrasta —, então o campo era, na prática, fixo para quem não usa mouse. O direito
continua funcionando.
Não há conflito com o chute porque a mira só começa quando o clique cai a menos de 250 mm da
bola; fora desse raio o arrasto não tinha uso nenhum.

O zoom ancora no **cursor**: o ponto do campo sob o ponteiro fica parado enquanto a escala muda.
Antes ancorava no centro do painel, e quem olhava um canto via o canto fugir da tela a cada
passo, tendo de arrastar de volta toda vez.

Cada evento de scroll vale no máximo **um entalhe de roda**. O trackpad do mac não manda um
evento por gesto como uma roda com entalhe: manda uma rajada — medidos 327 eventos em 4 s — em
que o peso varia de 0,006 a 4,7. Sem teto, aquele 4,7 sozinho mudava a escala em 58% num quadro,
no meio de centenas de eventos que não faziam nada visível. Era esse contraste, e não a
sensibilidade média, que fazia o zoom parecer instável: quase parado e de repente um pulo.
Cortar em um entalhe limita pela coisa certa e por isso não estraga a roda de mouse, onde o
valor já é 1 e o corte nunca age. O `teste.Autoteste` prende as três coisas — âncora, batente e
teto do pico.

Ao mirar um chute, o vetor mostra a velocidade resultante em m/s e fica vermelho ao saturar
nos 6,5 m/s. Sem isso não dá para dosar a força, porque o comprimento do vetor sozinho não
diz nada depois que ele bate no teto. O ganho do arrasto é 1,0, então é preciso arrastar o
campo inteiro para chegar no máximo, o que deixa espaço para um passe fraco.

### Aparência

Antes, o simulador não escolhia look-and-feel nenhum e caía no Metal, o visual de fábrica do
Swing. A estratégia, do outro lado, pedia o do **sistema**. As duas janelas costumam ficar lado
a lado na mesma tela e saíam com botão diferente uma da outra na mesma máquina, além de mudarem
de cara entre macOS e Windows.

Hoje `view.Estilo` instala o [FlatLaf](https://www.formdev.com/flatlaf/) nos dois, com as
mesmas cores. Ele desenha tudo em Java, sem delegar nada ao sistema, e por isso sai igual nos
três. O Metal também daria isso, mas não respeita cor por componente o bastante para acompanhar
o painel escuro daqui.

A fonte é um segundo eixo, independente do L&F. `new Font("SansSerif", ...)` não nomeia uma
fonte: é um pedido que o JDK resolve para uma fonte **física** diferente em cada sistema.
Larguras diferentes movem o texto que `Campo` desenha direto no `Graphics`, e nenhum
look-and-feel conserta isso. A JetBrains Mono vai embarcada no jar, e todo `new Font` do código
virou `Estilo.fonte(...)`.

Ela é monoespaçada de propósito, num programa que é quase todo número medido. O custo é que
coluna passou a ser caractere na régua, sem a folga que uma proporcional dava de graça: foi o
que alargou o campo de nome de equipe de 9 para 12 colunas ("Adversario" tem 10 e sumia pela
esquerda) e a coluna de perguntas do diálogo de física de 290 para 335 px. Ao mexer em largura
fixa perto de texto, meça com `getFontMetrics` em vez de estimar no olho.

## Ainda não existe

Árbitro e estado de jogo (gol, bola fora, `HALT`/`STOP`/`RUNNING`, placar) e replay a partir
do log. O software que joga é projeto separado; este
repositório é só o simulador.
