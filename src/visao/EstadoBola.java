package visao;

import core.Vec2;
import model.Bola;

/**
 * Estado da bola num quadro. Imutavel.
 *
 * @param z  altura do ponto mais baixo da bola acima do gramado, em mm
 * @param vz velocidade vertical, em mm/s
 */
public record EstadoBola(Vec2 posicao, double z, Vec2 velocidade, double vz,
                         boolean deslizando) {

    public static final EstadoBola PARADA =
            new EstadoBola(Vec2.ZERO, 0, Vec2.ZERO, 0, false);

    public double rapidez() { return velocidade.norma(); }

    public boolean noAr() { return z > 0 || vz > 0; }

    /** Altura do centro da bola, que e o que a visao reporta como posicao. */
    public double zCentro() { return z + Bola.RAIO; }
}
