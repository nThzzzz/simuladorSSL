package visao;

import core.Vec2;

/** Estado da bola num quadro. Imutavel. */
public record EstadoBola(Vec2 posicao, Vec2 velocidade, boolean deslizando) {

    public static final EstadoBola PARADA = new EstadoBola(Vec2.ZERO, Vec2.ZERO, false);

    public double rapidez() { return velocidade.norma(); }
}
