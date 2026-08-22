package sim;

import core.SimClock;
import engine.Mundo;
import model.Bola;
import model.Robot;
import visao.EstadoBola;
import visao.EstadoMundo;
import visao.EstadoRobo;
import visao.FonteDeVisao;

import java.util.ArrayList;
import java.util.List;

/**
 * Retrato do mundo simulado, consumido pela janela e pelo publicador de visao.
 *
 * <p>O quadro e reconstruido apenas quando o numero do quadro muda. Sem esse
 * cache a interface alocaria um retrato inteiro a cada movimento do mouse --
 * {@code telaParaMundo} precisa da geometria e e chamado o tempo todo.
 *
 * <p>O carimbo vem do {@link SimClock}, e nao de {@code mundo.getFrame()}. Os
 * dois nao sao a mesma coisa no instante em que este retrato e tirado:
 * {@code iniciarQuadro(n, t)} marca o mundo no COMECO do passo, {@code passo(dt)}
 * leva o estado para {@code t+dt}, e so entao o gancho de fim de tick publica.
 * Carimbar com o valor do mundo mandava para a rede as posicoes de {@code t+dt}
 * etiquetadas como {@code t} -- um quadro inteiro de defasagem entre o conteudo e
 * o carimbo, que a 60 Hz e 16,7 ms de erro sistematico para quem estiver
 * compensando latencia do outro lado.
 *
 * <p>O log continua usando o carimbo do mundo, e esta certo: ele grava ANTES da
 * integracao, entao para ele {@code t} e mesmo o instante do estado gravado.
 */
public final class VisaoLocal implements FonteDeVisao {

    private final Simulacao sim;
    private EstadoMundo cache;
    private long frameDoCache = -1;

    public VisaoLocal(Simulacao sim) {
        this.sim = sim;
    }

    @Override
    public EstadoMundo ultimoQuadro() {
        Mundo mundo = sim.getMundo();
        SimClock clock = sim.getClock();
        if (cache != null && frameDoCache == clock.getFrame()) return cache;

        List<EstadoRobo> robos = new ArrayList<>();
        for (Robot r : mundo.getRobos()) {
            robos.add(new EstadoRobo(r.getId(), r.getCor(), r.getPosicao(), r.getTheta(),
                    r.getVelocidade(), r.getOmega(), r.temBolaNoDribbler()));
        }

        Bola bola = mundo.getBola();
        cache = new EstadoMundo(clock.getFrame(), clock.getTempo(), mundo.getGeometria(),
                mundo.getParametros(),
                mundo.getAzul().getNome(), mundo.getAmarelo().getNome(),
                new EstadoBola(bola.getPosicao(), bola.getZ(), bola.getVelocidade(),
                        bola.getVz(), bola.estaDeslizando()),
                robos);
        frameDoCache = clock.getFrame();
        return cache;
    }
}
