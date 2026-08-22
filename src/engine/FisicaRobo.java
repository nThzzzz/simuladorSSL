package engine;

import core.Angulo;
import core.Vec2;
import model.Robot;
import model.RobotCommand;

/**
 * Integracao do movimento de um robo omnidirecional.
 *
 * <p>O comando chega no referencial local do robo (frente = +x), e convertido
 * para o global e entra como velocidade ALVO -- nao como velocidade instantanea.
 * A saturacao de aceleracao entre o alvo e a velocidade atual e o que impede o
 * robo de teleportar e o que torna a trajetoria logada fisicamente plausivel.
 */
public final class FisicaRobo {

    public void integrar(Robot r, double dt) {
        RobotCommand cmd = r.getComando();

        // --- Linear ---
        Vec2 velAlvo = new Vec2(cmd.velTangencial(), cmd.velNormal())
                .paraGlobal(r.getTheta())
                .limitado(r.getVelMax());

        Vec2 delta = velAlvo.menos(r.getVelocidade());
        double deltaMax = r.getAcelMax() * dt;
        if (delta.norma() > deltaMax) delta = delta.comNorma(deltaMax);

        Vec2 velNova = r.getVelocidade().mais(delta);
        r.setPosicao(r.getPosicao().mais(r.getVelocidade().mais(velNova).escala(dt / 2.0)));
        r.setVelocidade(velNova);

        // --- Angular ---
        double omegaAlvo = clamp(cmd.velAngular(), -r.getOmegaMax(), r.getOmegaMax());
        double deltaOmega = clamp(omegaAlvo - r.getOmega(),
                -r.getAcelAngularMax() * dt, r.getAcelAngularMax() * dt);

        double omegaNovo = r.getOmega() + deltaOmega;
        r.setTheta(Angulo.normalizar(r.getTheta() + (r.getOmega() + omegaNovo) * dt / 2.0));
        r.setOmega(omegaNovo);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
