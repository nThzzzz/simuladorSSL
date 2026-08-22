package visao;

import core.Vec2;
import model.ParametrosFisica;

/**
 * O que o cliente pode pedir ao simulador -- e so isso.
 *
 * <p>Duas portas de escrita no mundo passam por aqui: a interface do simulador e
 * o {@code SimulatorCommand} que chega pela rede. Ter as duas atravessando a
 * mesma abstracao evita manter dois caminhos de escrita em dia.
 *
 * <p>Note o que NAO esta aqui: atribuir skill a um robo. Skill e conceito de quem
 * joga, nao de quem simula -- um software de time faz isso mandando
 * {@code RobotControl}, nao pedindo skill ao simulador.
 */
public interface CanalDeControle {

    /** Teleporta a bola no gramado, opcionalmente com velocidade. */
    default void reposicionarBola(Vec2 posicao, Vec2 velocidade) {
        reposicionarBola(posicao, 0, velocidade, 0);
    }

    /**
     * Teleporta a bola, possivelmente no ar.
     *
     * <p>Assinatura espelha o {@code TeleportBall} do protocolo, que tambem
     * carrega z e vz.
     *
     * @param z  altura do ponto mais baixo da bola, em mm
     * @param vz velocidade vertical, em mm/s
     */
    void reposicionarBola(Vec2 posicao, double z, Vec2 velocidade, double vz);

    void reiniciarPartida(String nomeAzul, int qtdAzul, String nomeAmarelo, int qtdAmarelo);

    void ajustarFisica(ParametrosFisica parametros);
}
