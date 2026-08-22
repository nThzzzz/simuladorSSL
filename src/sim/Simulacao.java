package sim;

import core.SimClock;
import engine.Mundo;
import log.ConfigLog;
import log.Logger;
import log.LoggerArquivo;
import log.LoggerNulo;
import model.Geometria;
import model.ParametrosFisica;

import java.nio.file.Path;

/**
 * Amarra as tres camadas em um tick: decidir -> integrar -> gravar.
 *
 * <p>A ordem importa e e sempre a mesma, o que garante que o comando gravado num
 * quadro e exatamente o comando que produziu o movimento daquele quadro. Com
 * passo fixo, reexecutar a mesma sequencia de decisoes reproduz o log byte a byte.
 *
 * <p>Implementa {@link ConsoleLocal}: e o simulador que oferece as operacoes que
 * so fazem sentido no processo local. A interface grafica so enxerga esse
 * contrato, nunca o {@code Mundo} por dentro.
 */
public final class Simulacao implements ConsoleLocal {

    private final Mundo mundo;
    private final SimClock clock;
    private final ControladorExterno externo = new ControladorExterno();

    private Logger logger = LoggerNulo.INSTANCIA;
    private Runnable antesTick;
    private Runnable aposTick;
    private boolean gravando;

    public Simulacao(Geometria geometria, ParametrosFisica parametros, double dt) {
        this.mundo = new Mundo(geometria, parametros);
        this.clock = new SimClock(dt);
    }

    public static Simulacao padrao() {
        return new Simulacao(Geometria.divisaoB(), ParametrosFisica.padrao(), SimClock.DT_PADRAO);
    }

    public Mundo getMundo()                    { return mundo; }
    public SimClock getClock()                 { return clock; }
    /**
     * Unico ponto por onde um robo recebe comando.
     *
     * <p>Alimentado pelo {@code RobotControl} que chega pela rede e, quando ha um
     * cenario de teste rodando, tambem por ele. O simulador nao decide nada
     * sozinho: sem ninguem escrevendo aqui, os robos ficam parados, exatamente
     * como no grSim.
     */
    public ControladorExterno getControladorExterno() { return externo; }
    @Override
    public boolean estaGravando()              { return gravando; }

    /**
     * Gancho executado ao fim de cada tick, usado para publicar o quadro na rede.
     *
     * <p>Fica no tick e nao no repaint de proposito: a visao tem de sair uma vez
     * por quadro simulado, nao uma vez por quadro desenhado.
     */
    public void setAposTick(Runnable aposTick) { this.aposTick = aposTick; }

    /**
     * Gancho executado no inicio de cada tick, antes de qualquer decisao.
     *
     * <p>E onde o cenario de teste escreve os comandos, para que um comando
     * marcado para um instante ja governe o movimento daquele mesmo quadro.
     */
    public void setAntesTick(Runnable antesTick) { this.antesTick = antesTick; }

    public void inicializarPartida(String nomeAzul, int qtdAzul,
                                   String nomeAmarelo, int qtdAmarelo) {
        mundo.inicializarPartida(nomeAzul, qtdAzul, nomeAmarelo, qtdAmarelo);
    }

    // ----------------------------------------------------------- ConsoleLocal

    @Override
    public long getQuadrosGravados() { return logger.getQuadrosGravados(); }

    @Override
    public long getEventosGravados() { return logger.getEventosGravados(); }

    /**
     * Um passo de simulacao.
     *
     * <p>A amostra do quadro e gravada ANTES da integracao, de proposito: assim a
     * linha carimbada com t contem o estado em t junto do comando que sera
     * aplicado no intervalo [t, t+dt). E o par (estado, acao) alinhado -- se
     * gravassemos depois, o estado da linha seria o de t+dt e o comando o de t.
     */
    public void tick() {
        double dt = clock.getDt();
        mundo.iniciarQuadro(clock.getFrame(), clock.getTempo());
        if (antesTick != null) antesTick.run();
        externo.decidir(mundo, dt);
        logger.quadro(mundo);
        mundo.passo(dt);
        logger.eventos(mundo.drenarEventos()); // drena sempre, mesmo sem gravar
        clock.avancar();
        if (aposTick != null) aposTick.run();
    }

    /** Roda os ticks necessarios para acompanhar o relogio de parede. */
    public int tickTempoReal(int maxTicks) {
        int n = clock.ticksPendentes(maxTicks);
        for (int i = 0; i < n; i++) tick();
        return n;
    }

    /** Roda {@code duracaoSegundos} de tempo simulado o mais rapido possivel. */
    public void rodarHeadless(double duracaoSegundos) {
        long total = Math.round(duracaoSegundos / clock.getDt());
        for (long i = 0; i < total; i++) tick();
    }

    // ---------------------------------------------------------------- gravacao

    public void iniciarGravacao(Path diretorio) {
        iniciarGravacao(diretorio, ConfigLog.COMPLETO);
    }

    /**
     * Comeca a gravar em {@code diretorio}. Uma config que nao grava nada e
     * tratada como no-op: nem o diretorio e criado.
     */
    @Override
    public void iniciarGravacao(Path diretorio, ConfigLog config) {
        pararGravacao();
        if (!config.gravaAlgo()) return;
        LoggerArquivo l = new LoggerArquivo(diretorio, config);
        l.inicio(mundo, clock.getDt());
        this.logger = l;
        this.gravando = true;
    }

    @Override
    public void pararGravacao() {
        if (!gravando) return;
        logger.close();
        logger = LoggerNulo.INSTANCIA;
        gravando = false;
    }

    public Logger getLogger() { return logger; }
}
