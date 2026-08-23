package core;

/**
 * Retangulo alinhado aos eixos, em milimetros.
 *
 * <p>Existe por causa do gol: e o unico obstaculo do simulador que nao e um
 * circulo nem uma parede infinita. Alinhado aos eixos porque o gol tambem e --
 * resolver contato com um retangulo girado exigiria trocar de referencial, e
 * nada aqui precisa disso.
 *
 * <p>O construtor nao ordena os cantos; use {@link #de} quando os extremos
 * puderem vir trocados, o que acontece sempre que a caixa e espelhada para o
 * lado {@code -x} do campo.
 */
public record Caixa(double xMin, double yMin, double xMax, double yMax) {

    /** Caixa a partir de dois cantos quaisquer, ordenando os extremos. */
    public static Caixa de(double x0, double y0, double x1, double y1) {
        return new Caixa(Math.min(x0, x1), Math.min(y0, y1),
                         Math.max(x0, x1), Math.max(y0, y1));
    }

    /** Extensao no eixo X. Nao se chama largura para nao colidir com a do campo. */
    public double extensaoX() { return xMax - xMin; }

    /** Extensao no eixo Y. */
    public double extensaoY() { return yMax - yMin; }

    public Vec2 centro() { return new Vec2((xMin + xMax) / 2.0, (yMin + yMax) / 2.0); }

    public boolean contem(Vec2 p) {
        return p.x() >= xMin && p.x() <= xMax && p.y() >= yMin && p.y() <= yMax;
    }

    /** Ponto da caixa mais proximo de {@code p}; o proprio {@code p} se ele esta dentro. */
    public Vec2 pontoMaisProximo(Vec2 p) {
        return new Vec2(Math.min(Math.max(p.x(), xMin), xMax),
                        Math.min(Math.max(p.y(), yMin), yMax));
    }

    /**
     * Caixa crescida de {@code m} para todos os lados.
     *
     * <p>E a soma de Minkowski com um quadrado de lado {@code 2m}: um circulo de
     * raio {@code m} contra a caixa vira um PONTO contra a caixa dilatada, com o
     * erro conhecido de arredondar os cantos para quina viva.
     */
    public Caixa dilatada(double m) {
        return new Caixa(xMin - m, yMin - m, xMax + m, yMax + m);
    }
}
