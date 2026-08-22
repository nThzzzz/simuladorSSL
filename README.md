# ⚽ Simulador SSL 2D

Simulador bidimensional da categoria **Small Size League** (RoboCup), escrito em Java
(Swing/Graphics2D). Nasceu da curiosidade de entender a matemática por trás de simuladores
como o `grSim`, e hoje cumpre o mesmo papel que ele: simula a física, mostra numa janela e
publica na rede pelo protocolo oficial da liga.

## Como rodar

```bash
./tools/build.sh                    # javac puro, sem Gradle e sem rede

java -cp "out/production/SSL:lib/*" Main             # janela + publica visão na rede
java -cp "out/production/SSL:lib/*" Main --sem-rede  # janela, offline

# dataset sem janela, ≈650× tempo real (precisa de alguém pilotando os robôs)
java -cp "out/production/SSL:lib/*" Main --headless --duracao 300 --saida logs/corrida1

java -cp "out/production/SSL:lib/*" Main --ajuda            # todas as opções
java -cp "out/production/SSL:lib/*" teste.Autoteste         # invariantes da física
java -cp "out/production/SSL:lib/*" teste.AutotesteRede     # protocolo de rede
```

O modo headless não publica de propósito: ele roda centenas de vezes mais rápido que o tempo
real, e despejar isso num multicast só inundaria a rede.

A única dependência é `protobuf-java`, versionada em `lib/` junto com o Java gerado a partir
dos `.proto` (em `src/proto/`). Um clone limpo compila offline, sem Gradle, sem Maven e sem
baixar nada. Se o jar sumir, `./tools/build.sh` busca de volta sozinho. O
`./tools/gerar-proto.sh` só precisa rodar quando o protocolo da liga mudar.

**IntelliJ:** o `SSL.iml` já declara `lib/` como biblioteca do módulo, apontando para o
diretório e não para o jar nominal, então trocar a versão não exige mexer nele. Se o IDE
reclamar de `package com.google.protobuf does not exist` depois de um `git pull`, é cache:
*File → Reload All from Disk*.

## Convenções

Tudo em **milímetros**, **radianos** e **segundos**, que são as unidades da SSL-Vision, para
que o log saia diretamente comparável a dados reais. Origem `(0,0)` no centro do campo, `+x`
para o gol amarelo, `+y` para a lateral superior. Campo padrão: **Divisão B** (9000 × 6000 mm).

## Arquitetura

Dependências estritamente em uma direção, sem ciclos:

```
core     →  (nada)         Vec2, Angulo, SimClock
model    →  core           Bola, Robot, Equipe, Geometria, RobotCommand, ParametrosFisica
visao    →  core, model    EstadoMundo, FonteDeVisao, CanalDeControle
engine   →  core, model    Mundo, FisicaBola, FisicaRobo, Colisoes, Evento
log      →  + engine       Logger, LoggerArquivo, Json
sim      →  tudo           Simulacao, VisaoLocal, ControleLocal, ControladorExterno
rede     →  + proto        PublicadorVisao, ReceptorDeControle, ReceptorDeComandosRobo
view     →  core, model, visao    Campo
app      →  tudo           Janela, Rede, DialogoRede
```

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

## Motor de física

* **Passo fixo** (`SimClock`, 60 Hz por padrão). É o que torna o log reproduzível: o mesmo
  estado inicial e a mesma sequência de comandos geram exatamente os mesmos quadros,
  independente da carga da máquina ou do FPS de renderização.
* **Bola com atrito em duas fases.** Uma bola chutada não rola de imediato: desliza com
  atrito cinético (≈ 2,9 m/s²) até que a rotação alcance a translação, o que para uma esfera
  homogênea acontece em `v = 5/7·v₀`; a partir daí rola com atrito uma ordem de grandeza
  menor (≈ 0,49 m/s²). Sem as duas fases o chute longo fica visivelmente errado.
* **Robôs omnidirecionais** com saturação de velocidade e de aceleração, linear e angular.
* **Colisões** bola↔robô (capa circular truncada pela face plana do dribbler, resolvida no
  referencial local e por velocidade *relativa*), robô↔robô com impulso, e paredes.
* **Atuadores**: dribbler segura a bola contra a face; chutador plano e chip.

Os parâmetros ficam em `ParametrosFisica` e são gravados no cabeçalho do log, então uma
corrida sempre pode ser reproduzida com a física que a gerou. Dá para ajustá-los ao vivo pelo
painel lateral; a troca entra no log como evento `PARAMETROS_ALTERADOS`, então um log que
atravessa o ajuste continua interpretável.

Sobre os padrões de restituição: um robô de SSL é construído para **matar** a bola no
contato, não para devolvê-la, daí `restituicaoRobo = 0.35`. E a parede perde bem mais que
20%; com restituição alta a bola fica ricocheteando por dezenas de segundos, o que não
acontece numa partida real. Efeito medido de `0.80/0.60` para `0.50/0.35`:

| | antes | agora |
|---|---:|---:|
| chute máximo até assentar | 7,9 s (3 batidas) | 5,6 s (2 batidas) |
| rebote de frente num robô parado (chegando a 4 m/s) | 2,16 m/s | 1,26 m/s |

O chute máximo em si continua nos **6,5 m/s da regra** e cruza os 9 m do campo em 1,90 s.

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
que algo esteja pilotando.

## Log

`--saida <dir>` produz quatro arquivos:

| Arquivo | Conteúdo |
|---|---|
| `meta.json` | geometria, parâmetros de física, equipes e `dt`, o suficiente para reproduzir a corrida |
| `ball.csv` | uma linha por quadro |
| `robots.csv` | uma linha por robô por quadro (formato longo) |
| `events.jsonl` | um evento por linha |

O CSV de robôs é **longo**, e não largo com uma coluna por robô, porque a quantidade de robôs
muda em tempo de execução e um cabeçalho largo ficaria inválido no meio do arquivo. Para
filtrar: `df[(df.cor == "blue") & (df.id == 3)]`.

A amostra do quadro é gravada **antes** da integração, de propósito: a linha carimbada com
`t` contém o estado em `t` junto do comando que será aplicado no intervalo `[t, t+dt)`. É o
par (estado, ação) alinhado.

Eventos registrados: `PARTIDA_INICIADA`, `PARAMETROS_ALTERADOS`, `BOLA_REPOSICIONADA`,
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

Zoom no scroll, arrasto com o botão direito. Ferramentas: posicionar a bola, chutar
arrastando e inspecionar robô. O painel de log liga e desliga a gravação a quente, escolhe os
streams e mostra o volume gravado em tempo real. O painel de física tem sliders para quique e
atrito, aplicados na hora. O painel de rede mostra os contadores de pacote e abre a janela de
configuração de portas.

Ao mirar um chute, o vetor mostra a velocidade resultante em m/s e fica vermelho ao saturar
nos 6,5 m/s. Sem isso não dá para dosar a força, porque o comprimento do vetor sozinho não
diz nada depois que ele bate no teto. O ganho do arrasto é 1,0, então é preciso arrastar o
campo inteiro para chegar no máximo, o que deixa espaço para um passe fraco.

## Ainda não existe

Árbitro e estado de jogo (gol, bola fora, `HALT`/`STOP`/`RUNNING`, placar), altura de bola
para o chip, e replay a partir do log. O software que joga é projeto separado; este
repositório é só o simulador.
