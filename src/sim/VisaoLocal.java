package sim;

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
        if (cache != null && frameDoCache == mundo.getFrame()) return cache;

        List<EstadoRobo> robos = new ArrayList<>();
        for (Robot r : mundo.getRobos()) {
            robos.add(new EstadoRobo(r.getId(), r.getCor(), r.getPosicao(), r.getTheta(),
                    r.getVelocidade(), r.getOmega(), r.temBolaNoDribbler()));
        }

        Bola bola = mundo.getBola();
        cache = new EstadoMundo(mundo.getFrame(), mundo.getTempo(), mundo.getGeometria(),
                mundo.getAzul().getNome(), mundo.getAmarelo().getNome(),
                new EstadoBola(bola.getPosicao(), bola.getVelocidade(), bola.estaDeslizando()),
                robos);
        frameDoCache = mundo.getFrame();
        return cache;
    }
}
