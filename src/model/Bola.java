package model;

import core.Vec2;

/**
 * Bola de golfe laranja padrao SSL, com altura.
 *
 * <p>O estado {@code deslizando} existe porque uma bola chutada nao rola de
 * imediato: ela desliza sobre o carpete com atrito cinetico ate que a rotacao
 * alcance a translacao, o que para uma esfera homogenea acontece em
 * v = 5/7 * v0. Depois disso o atrito cai uma ordem de grandeza. Sem essas duas
 * fases o chute longo fica visivelmente errado.
 *
 * <p>{@link #getZ()} e a altura do ponto MAIS BAIXO da bola acima do gramado, e
 * nao a do centro. Com essa convencao "esta no chao" e simplesmente
 * {@code z == 0}, e "passa por cima de um robo" e {@code z >= Robot.ALTURA},
 * sem somar ou subtrair raio em cada teste.
 */
public final class Bola {

    public static final double RAIO = 21.5;      // mm
    public static final double MASSA = 0.046;    // kg
    public static final double VEL_MAX = 6500.0; // mm/s -- teto imposto pela regra da SSL

    private static final double FRACAO_ROLAMENTO = 5.0 / 7.0;

    private Vec2 posicao = Vec2.ZERO;
    private Vec2 velocidade = Vec2.ZERO;
    private double z = 0;
    private double vz = 0;

    private boolean deslizando = false;
    private double velAlvoRolamento = 0;

    public Vec2 getPosicao()    { return posicao; }
    public Vec2 getVelocidade() { return velocidade; }
    public double getRapidez()  { return velocidade.norma(); }

    /** Altura do ponto mais baixo da bola acima do gramado, em mm. */
    public double getZ()  { return z; }
    public double getVz() { return vz; }

    /** Altura do centro da bola, que e o que a visao reporta como posicao. */
    public double getZCentro() { return z + RAIO; }

    public boolean estaNoAr() { return z > 0 || vz > 0; }

    public boolean estaDeslizando()      { return deslizando; }
    public double getVelAlvoRolamento()  { return velAlvoRolamento; }

    public void setPosicao(Vec2 p)    { this.posicao = p; }
    public void setVelocidade(Vec2 v) { this.velocidade = v; }
    public void setZ(double z)        { this.z = Math.max(0, z); }
    public void setVz(double vz)      { this.vz = vz; }

    /** Marca a transicao de deslizamento para rolamento puro. */
    public void marcarRolando() {
        this.deslizando = false;
        this.velAlvoRolamento = 0;
    }

    /** Chute rasteiro: so velocidade horizontal. */
    public void lancar(Vec2 velocidade) {
        lancar(velocidade, 0);
    }

    /**
     * Impoe uma nova velocidade a bola e reinicia a fase de deslizamento.
     *
     * <p>Serve tanto para o chute quanto para cada quique: ao tocar o chao a bola
     * volta a deslizar, porque a rotacao que ela trazia no ar nao corresponde a
     * velocidade horizontal com que aterrissou.
     *
     * <p>A saturacao em {@link #VEL_MAX} e aplicada a velocidade TOTAL, incluindo
     * a componente vertical: o limite da regra e sobre o quanto a bola sai do
     * chutador, nao sobre a projecao no gramado.
     */
    public void lancar(Vec2 velocidade, double vz) {
        // Chute parte de bola sem giro, entao o deslize acaba em 5/7 da saida.
        rebater(velocidade, vz, velocidade.norma() * FRACAO_ROLAMENTO);
    }

    /**
     * Impoe velocidade sabendo em que rapidez o deslize vai terminar.
     *
     * <p>Serve para quique, onde a fracao 5/7 nao vale: a bola inverte a
     * translacao mas MANTEM o giro que trazia, entao sai do contato girando ao
     * contrario do proprio movimento. O atrito precisa primeiro parar e reverter
     * esse giro, o que consome muito mais velocidade do que um chute. Quem sabe
     * calcular isso e quem resolve a colisao; aqui so recebemos o resultado.
     *
     * @param velocidadeDeRolamento rapidez em que o deslize termina; zero faz a
     *                              bola deslizar ate parar
     */
    public void rebater(Vec2 velocidade, double vz, double velocidadeDeRolamento) {
        double total = Math.hypot(velocidade.norma(), vz);
        if (total > VEL_MAX) {
            double escala = VEL_MAX / total;
            velocidade = velocidade.escala(escala);
            vz *= escala;
            velocidadeDeRolamento *= escala;
        }

        this.velocidade = velocidade;
        this.vz = vz;

        double horizontal = velocidade.norma();
        if (horizontal > 1e-6) {
            this.deslizando = true;
            this.velAlvoRolamento = Math.max(0, Math.min(velocidadeDeRolamento, horizontal));
        } else {
            marcarRolando();
        }
    }

    /** Teleporta a bola para o gramado e zera toda a inercia. */
    public void reposicionar(Vec2 p) {
        reposicionar(p, 0, Vec2.ZERO, 0);
    }

    /** Teleporta a bola, possivelmente no ar e em movimento. */
    public void reposicionar(Vec2 p, double z, Vec2 velocidade, double vz) {
        this.posicao = p;
        this.z = Math.max(0, z);
        marcarRolando();
        lancar(velocidade, vz);
    }
}
