package engine;

import core.Vec2;
import model.Bola;
import model.ParametrosFisica;

/**
 * Integracao do movimento da bola com atrito em duas fases.
 *
 * <p>Substitui o antigo {@code v *= 0.97} por quadro, que era dependente de FPS:
 * a 120 Hz a bola percorria metade da distancia que percorria a 60 Hz. Agora a
 * desaceleracao e por segundo, entao a trajetoria so depende do estado inicial.
 */
public final class FisicaBola {

    private final ParametrosFisica p;

    public FisicaBola(ParametrosFisica p) { this.p = p; }

    public void integrar(Bola bola, double dt) {
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
