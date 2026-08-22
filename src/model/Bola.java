package model;

import core.Vec2;

/**
 * Bola de golfe laranja padrao SSL.
 *
 * <p>O estado {@code deslizando} existe porque uma bola chutada nao rola de
 * imediato: ela desliza sobre o carpete com atrito cinetico ate que a rotacao
 * alcance a translacao, o que para uma esfera homogenea acontece em
 * v = 5/7 * v0. Depois disso o atrito cai uma ordem de grandeza. Sem essas duas
 * fases o chute longo fica visivelmente errado.
 */
public final class Bola {

    public static final double RAIO = 21.5;      // mm
    public static final double MASSA = 0.046;    // kg
    public static final double VEL_MAX = 6500.0; // mm/s -- teto imposto pela regra da SSL

    private static final double FRACAO_ROLAMENTO = 5.0 / 7.0;

    private Vec2 posicao = Vec2.ZERO;
    private Vec2 velocidade = Vec2.ZERO;

    private boolean deslizando = false;
    private double velAlvoRolamento = 0;

    public Vec2 getPosicao()    { return posicao; }
    public Vec2 getVelocidade() { return velocidade; }
    public double getRapidez()  { return velocidade.norma(); }

    public boolean estaDeslizando()      { return deslizando; }
    public double getVelAlvoRolamento()  { return velAlvoRolamento; }

    public void setPosicao(Vec2 p)    { this.posicao = p; }
    public void setVelocidade(Vec2 v) { this.velocidade = v; }

    /** Marca a transicao de deslizamento para rolamento puro. */
    public void marcarRolando() {
        this.deslizando = false;
        this.velAlvoRolamento = 0;
    }

    /**
     * Impoe uma nova velocidade a bola (chute, passe ou reposicionamento) e
     * reinicia a fase de deslizamento. A magnitude e saturada em {@link #VEL_MAX}.
     */
    public void lancar(Vec2 velocidade) {
        Vec2 v = velocidade.limitado(VEL_MAX);
        this.velocidade = v;
        double rapidez = v.norma();
        if (rapidez > 1e-6) {
            this.deslizando = true;
            this.velAlvoRolamento = rapidez * FRACAO_ROLAMENTO;
        } else {
            marcarRolando();
        }
    }

    /** Teleporta a bola e zera toda a inercia. */
    public void reposicionar(Vec2 p) {
        this.posicao = p;
        this.velocidade = Vec2.ZERO;
        marcarRolando();
    }
}
