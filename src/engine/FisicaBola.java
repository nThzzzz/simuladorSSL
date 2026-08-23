package engine;

import core.Vec2;
import model.Bola;
import model.ParametrosFisica;

/**
 * Integracao do movimento da bola, no chao e no ar.
 *
 * <p>No chao, atrito em duas fases. Substitui o antigo {@code v *= 0.97} por
 * quadro, que era dependente de FPS: a 120 Hz a bola percorria metade da
 * distancia que percorria a 60 Hz. Agora a desaceleracao e por segundo, entao a
 * trajetoria so depende do estado inicial.
 *
 * <p>No ar, trajetoria balistica sem atrito horizontal. Isso importa: um chip
 * que perdesse atrito de rolamento enquanto voa cairia bem antes do alcance
 * real, porque atrito de rolamento so existe em contato com o gramado.
 *
 * <p>O instante do toque no chao e resolvido dentro do passo, e nao arredondado
 * para a borda do quadro. A 60 Hz um chip percorre ate 77 mm de altura por
 * quadro; grudar o quique no fim do passo mudaria visivelmente onde a bola
 * aterrissa e faria o alcance depender da taxa de simulacao.
 */
public final class FisicaBola {

    /** Abaixo desta velocidade vertical o quique para, em vez de virar micro-quiques. */
    private static final double VZ_MINIMA = 120.0; // mm/s

    /** Teto de quiques resolvidos num unico passo, por seguranca. */
    private static final int MAX_QUIQUES_POR_PASSO = 4;

    private final ParametrosFisica p;

    public FisicaBola(ParametrosFisica p) { this.p = p; }

    public void integrar(Bola bola, double dt) {
        bola.marcarInicioDoPasso();
        if (bola.estaNoAr()) voar(bola, dt);
        else rolar(bola, dt);
    }

    // --------------------------------------------------------------------- ar

    private void voar(Bola bola, double dt) {
        double restante = dt;
        int quiques = 0;

        while (restante > 1e-9) {
            double t = tempoAteTocarOChao(bola, restante);

            // Avanca ate o toque (ou ate o fim do passo, se nao houver toque).
            double vzFinal = bola.getVz() - p.gravidade() * t;
            bola.setPosicao(bola.getPosicao().mais(bola.getVelocidade().escala(t)));
            bola.setZ(bola.getZ() + (bola.getVz() + vzFinal) / 2.0 * t);
            bola.setVz(vzFinal);
            restante -= t;

            if (restante <= 1e-9) return;          // acabou o quadro ainda no ar
            if (++quiques > MAX_QUIQUES_POR_PASSO) break;

            // Tocou o chao: inverte a componente vertical e freia a horizontal.
            bola.setZ(0);
            double vzQuique = -vzFinal * p.restituicaoQuique();
            Vec2 horizontal = bola.getVelocidade().escala(p.atritoQuique());

            if (vzQuique < VZ_MINIMA) {
                // Assentou: o resto do passo e rolamento normal.
                bola.setVz(0);
                bola.lancar(horizontal, 0);
                rolar(bola, restante);
                return;
            }
            bola.lancar(horizontal, vzQuique);
        }

        // So chega aqui se estourou o teto de quiques: encosta a bola no chao.
        bola.setZ(0);
        bola.setVz(0);
        bola.lancar(bola.getVelocidade(), 0);
    }

    /**
     * Quanto falta para a bola tocar o gramado, limitado a {@code maximo}.
     *
     * <p>Raiz positiva de {@code z + vz*t - g*t^2/2 = 0}. Se a bola nao chega ao
     * chao dentro do intervalo, devolve o proprio {@code maximo}.
     */
    private double tempoAteTocarOChao(Bola bola, double maximo) {
        double g = p.gravidade();
        double z = bola.getZ();
        double vz = bola.getVz();

        double disc = vz * vz + 2 * g * z;
        if (disc < 0) return maximo;               // nao ocorre com z >= 0, mas guarda

        double t = (vz + Math.sqrt(disc)) / g;
        return (t > 0 && t < maximo) ? t : maximo;
    }

    // ------------------------------------------------------------------- chao

    private void rolar(Bola bola, double dt) {
        Vec2 v = bola.getVelocidade();
        double rapidez = v.norma();

        if (rapidez < p.velocidadeMinimaBola()) {
            bola.setVelocidade(Vec2.ZERO);
            bola.marcarRolando();
            return;
        }

        Vec2 direcao = v.escala(1.0 / rapidez);
        double restante = dt;
        double atual = rapidez;

        // Fase 1: deslizamento (atrito cinetico alto) ate atingir v = 5/7 * v0.
        if (bola.estaDeslizando()) {
            double desac = p.desaceleracaoDeslizamento();
            double tempoAteRolar = (atual - bola.getVelAlvoRolamento()) / desac;
            if (tempoAteRolar <= restante) {
                atual = bola.getVelAlvoRolamento();
                restante -= tempoAteRolar;
                bola.marcarRolando();
            } else {
                atual -= desac * restante;
                restante = 0;
            }
        }

        // Fase 2: rolamento puro (atrito uma ordem de grandeza menor).
        if (restante > 0) {
            atual = Math.max(0, atual - p.desaceleracaoRolamento() * restante);
        }

        // Deslocamento pela velocidade media do passo (trapezio), nao pela inicial.
        double deslocamento = (rapidez + atual) / 2.0 * dt;
        bola.setPosicao(bola.getPosicao().mais(direcao.escala(deslocamento)));

        if (atual < p.velocidadeMinimaBola()) {
            bola.setVelocidade(Vec2.ZERO);
            bola.marcarRolando();
        } else {
            bola.setVelocidade(direcao.escala(atual));
        }
    }
}
