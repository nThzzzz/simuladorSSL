import app.Janela;
import app.Rede;
import demo.Cenarios;
import demo.ExecutorDeCenario;
import demo.Roteiro;
import core.SimClock;
import log.ConfigLog;
import model.Geometria;
import model.ParametrosFisica;
import rede.ConfigRede;
import sim.ControleLocal;
import sim.Simulacao;
import sim.VisaoLocal;
import view.Estilo;
import visao.FonteDeVisao;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simulador SSL 2D -- um programa so, no mesmo papel do grSim.
 *
 * <pre>
 *   java Main                  janela + publica visao na rede
 *   java Main --sem-rede       janela sem tocar na rede
 *   java Main --headless ...   sem janela e sem rede: gera dataset o mais rapido possivel
 * </pre>
 *
 * <p>O simulador nao tem logica de jogo nenhuma: sem um software de time
 * conectado nas portas de {@code RobotControl}, os robos ficam parados -- igual
 * ao grSim. O modo headless nao publica de proposito, porque roda centenas de
 * vezes mais rapido que o tempo real e inundaria o multicast.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Argumentos a;
        try {
            a = Argumentos.parse(args);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            // Erro de uso nao merece stack trace: quem digitou errado quer ler
            // o que esta errado, nao a pilha do parser.
            String motivo = e instanceof ArrayIndexOutOfBoundsException
                    ? "falta o valor do ultimo argumento" : e.getMessage();
            System.err.println("erro: " + motivo);
            System.err.println("use --ajuda para ver as opcoes");
            System.exit(2);
            return;
        }

        Simulacao sim = new Simulacao(Geometria.divisaoB(), ParametrosFisica.padrao(), a.dt());
        sim.inicializarPartida("RoboFEI", a.robos(), "Adversario", a.robos());
        Runtime.getRuntime().addShutdownHook(new Thread(sim::pararGravacao));

        ExecutorDeCenario cenarios =
                new ExecutorDeCenario(sim, sim.getControladorExterno());
        cenarios.selecionar(procurarCenario(a.cenario()));
        sim.setAntesTick(() -> cenarios.tick(a.dt()));

        if (a.headless()) rodarHeadless(sim, a);
        else abrirJanela(sim, a, cenarios);
    }

    /**
     * Resolve o nome vindo da linha de comando; {@code null} deixa sem cenario.
     *
     * <p>A validacao acontece no parse dos argumentos, entao aqui o nome ja
     * chega valido e nao ha caminho de erro.
     */
    static Roteiro procurarCenario(String nome) {
        if (nome == null) return null;
        for (Roteiro r : Cenarios.todos(ParametrosFisica.padrao().gravidade())) {
            if (identificador(r).equals(nome)) return r;
        }
        throw new IllegalStateException("cenario nao validado no parse: " + nome);
    }

    private static List<String> nomesDeCenario() {
        return Cenarios.todos(ParametrosFisica.padrao().gravidade())
                .stream().map(Main::identificador).toList();
    }

    /** Nome do cenario em forma de opcao de linha de comando. */
    public static String identificador(Roteiro r) {
        return java.text.Normalizer.normalize(r.nome(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-");
    }

    /** Geracao de dataset: sem janela, sem rede, o mais rapido que a maquina der. */
    private static void rodarHeadless(Simulacao sim, Argumentos a) {
        Path saida = a.saida() != null ? a.saida() : Path.of("logs", carimbo());
        if (a.log().gravaAlgo()) sim.iniciarGravacao(saida, a.log());

        long inicio = System.nanoTime();
        sim.rodarHeadless(a.duracao());
        long quadros = sim.getQuadrosGravados();
        long eventos = sim.getEventosGravados();
        sim.pararGravacao();
        double segundos = (System.nanoTime() - inicio) / 1e9;

        System.out.printf("simulados %.1f s (%d quadros) em %.2f s reais  [%.0fx tempo real]%n",
                a.duracao(), sim.getClock().getFrame(), segundos,
                a.duracao() / Math.max(segundos, 1e-9));

        if (!a.log().gravaAlgo()) System.out.println("log desligado (--sem-log)");
        else System.out.printf("gravados %d quadros e %d eventos em %s%n",
                quadros, eventos, saida.toAbsolutePath());
    }

    private static void abrirJanela(Simulacao sim, Argumentos a, ExecutorDeCenario cenarios)
            throws Exception {
        FonteDeVisao fonte = new VisaoLocal(sim);

        Rede rede = null;
        if (!a.semRede()) {
            rede = new Rede(sim, fonte, a.rede());
            rede.anunciar();
            Rede aFechar = rede;
            Runtime.getRuntime().addShutdownHook(new Thread(aFechar::close));
        } else {
            System.out.println("rede desligada (--sem-rede)");
        }

        Rede r = rede;
        SwingUtilities.invokeLater(() -> {
            Estilo.instalar();
            Janela.abrir(
                "SSL Simulator",
                fonte,
                new ControleLocal(sim),
                sim,
                () -> sim.tickTempoReal(5), // teto de 5 ticks: prefere perder tempo a travar
                r, cenarios);
        });
    }

    static String carimbo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    record Argumentos(boolean headless, boolean semRede, String cenario,
                      double duracao, Path saida, double dt, int robos,
                      ConfigLog log, ConfigRede rede) {

        static Argumentos parse(String[] args) {
            boolean headless = false, semRede = false;
            String cenario = null;
            double duracao = 60, dt = SimClock.DT_PADRAO;
            Path saida = null;
            int robos = 6;
            boolean tracking = true, eventos = true, semLog = false;
            int intervalo = 1;
            ConfigRede rede = ConfigRede.padrao();

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--headless"       -> headless = true;
                    case "--sem-rede"       -> semRede = true;
                    case "--cenario"        -> cenario = args[++i];
                    case "--duracao"        -> duracao = Double.parseDouble(args[++i]);
                    case "--saida"          -> saida = Path.of(args[++i]);
                    case "--dt"             -> dt = Double.parseDouble(args[++i]);
                    case "--robos"          -> robos = Integer.parseInt(args[++i]);
                    case "--sem-log"        -> semLog = true;
                    case "--sem-tracking"   -> tracking = false;
                    case "--sem-eventos"    -> eventos = false;
                    case "--log-intervalo"  -> intervalo = Integer.parseInt(args[++i]);
                    case "--grupo"          -> rede = rede.comGrupo(args[++i]);
                    case "--porta-visao"    -> rede = rede.comPortaVisao(Integer.parseInt(args[++i]));
                    case "--porta-controle" -> rede = rede.comPortaControle(Integer.parseInt(args[++i]));
                    case "--porta-azul"     -> rede = rede.comPortaAzul(Integer.parseInt(args[++i]));
                    case "--porta-amarelo"  -> rede = rede.comPortaAmarelo(Integer.parseInt(args[++i]));
                    case "--ajuda", "-h"    -> { ajuda(); System.exit(0); }
                    default -> throw new IllegalArgumentException(
                            "argumento desconhecido: " + args[i] + " (use --ajuda)");
                }
            }

            ConfigLog log = semLog ? ConfigLog.DESLIGADO
                    : new ConfigLog(tracking, eventos, intervalo);
            String problema = rede.problema();
            if (problema != null) throw new IllegalArgumentException("rede: " + problema);

            if (cenario != null && !nomesDeCenario().contains(cenario)) {
                throw new IllegalArgumentException("cenario desconhecido: " + cenario
                        + "  (opcoes: " + String.join(", ", nomesDeCenario()) + ")");
            }

            return new Argumentos(headless, semRede, cenario, duracao, saida, dt,
                    robos, log, rede);
        }

        static void ajuda() {
            System.out.println("""
                    Simulador SSL 2D

                      java Main               janela + publica visao na rede
                      java Main --sem-rede    janela, sem tocar na rede
                      java Main --headless    sem janela e sem rede: gera dataset

                    Rede (publicada no modo janela)
                      visao      -> 224.5.23.2:10006   SSL_WrapperPacket
                      controle   <- porta 10300        SimulatorCommand
                      robos      <- portas 10301/10302 RobotControl (azul/amarelo)

                      --grupo <ip>         grupo multicast da visao  (padrao 224.5.23.2)
                      --porta-visao <n>    porta da visao            (padrao 10006)
                      --porta-controle <n> porta de SimulatorCommand (padrao 10300)
                      --porta-azul <n>     RobotControl azul         (padrao 10301)
                      --porta-amarelo <n>  RobotControl amarelo      (padrao 10302)

                      as mesmas opcoes podem ser mudadas com a janela aberta,
                      no botao "Configurar..." do painel Rede

                    Cenarios de teste
                      --cenario <nome>     chute-no-gol | passe-com-chip | conducao-com-roller
                                           sem isto, nada se move ate alguem conectar

                    Simulacao
                      --duracao <s>        tempo simulado no headless  (padrao 60)
                      --dt <s>             passo de fisica             (padrao 1/60)
                      --robos <n>          robos por equipe            (padrao 6)

                    Log
                      --saida <dir>        diretorio de saida  (padrao logs/<timestamp>)
                      --sem-log            nao grava nada
                      --sem-tracking       omite ball.csv e robots.csv
                      --sem-eventos        omite events.jsonl
                      --log-intervalo <n>  grava 1 a cada n quadros no tracking
                    """);
        }
    }
}
