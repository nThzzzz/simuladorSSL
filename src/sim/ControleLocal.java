package sim;

import core.Vec2;
import model.ParametrosFisica;
import visao.CanalDeControle;

/**
 * Canal de controle que aplica direto no simulador local.
 *
 * <p>Na fase de rede entra no lugar dele um emissor de
 * {@code ssl_simulation_control} por UDP; a interface nao muda.
 */
public final class ControleLocal implements CanalDeControle {

    private final Simulacao sim;

    public ControleLocal(Simulacao sim) {
        this.sim = sim;
    }

    @Override
    public void reposicionarBola(Vec2 posicao, double z, Vec2 velocidade, double vz) {
        sim.getMundo().reposicionarBola(posicao, z,
                velocidade == null ? Vec2.ZERO : velocidade, vz);
    }

    @Override
    public void reiniciarPartida(String nomeAzul, int qtdAzul,
                                 String nomeAmarelo, int qtdAmarelo) {
        sim.inicializarPartida(nomeAzul, qtdAzul, nomeAmarelo, qtdAmarelo);
    }

    @Override
    public void ajustarFisica(ParametrosFisica parametros) {
        sim.getMundo().setParametros(parametros);
    }
}
