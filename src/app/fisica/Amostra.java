package app.fisica;

import core.Vec2;

/**
 * Um instante do ensaio.
 *
 * @param bola  posicao no plano, em mm
 * @param z     altura da base da bola, em mm
 * @param robos poses dos robos envolvidos, ou vazio
 */
public record Amostra(Vec2 bola, double z, Vec2[] robos) {

    public static final Vec2[] SEM_ROBOS = new Vec2[0];
}
