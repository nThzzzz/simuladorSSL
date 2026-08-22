package model;

import core.Vec2;

/**
 * Geometria do campo em milimetros, conforme as regras oficiais da SSL.
 * A origem (0,0) fica no centro do campo, +x aponta para o gol amarelo
 * e +y para a lateral superior -- mesma convencao da SSL-Vision.
 */
public record Geometria(
        double comprimento,             // eixo X, linha de fundo a linha de fundo
        double largura,                 // eixo Y, lateral a lateral
        double faixaExterna,            // area de escape ate a parede fisica
        double golLargura,
        double golProfundidade,
        double areaDefesaProfundidade,
        double areaDefesaLargura,
        double raioCirculoCentral,
        double espessuraLinha
) {
    public static Geometria divisaoB() {
        return new Geometria(9000, 6000, 300, 1000, 180, 1000, 2000, 500, 10);
    }

    public static Geometria divisaoA() {
        return new Geometria(12000, 9000, 300, 1800, 180, 1800, 3600, 500, 10);
    }

    public double meioComprimento() { return comprimento / 2.0; }
    public double meiaLargura()     { return largura / 2.0; }

    /** Limite em X da parede fisica, onde a bola quica. */
    public double limiteParedeX() { return meioComprimento() + faixaExterna; }

    /** Limite em Y da parede fisica, onde a bola quica. */
    public double limiteParedeY() { return meiaLargura() + faixaExterna; }

    /** Sinal do lado do campo defendido pela equipe. Azul defende -x. */
    public static int ladoDefendido(Cor cor) { return cor == Cor.AZUL ? -1 : 1; }

    /** Centro do gol defendido pela equipe indicada. */
    public Vec2 centroGolDefendido(Cor cor) {
        return new Vec2(ladoDefendido(cor) * meioComprimento(), 0);
    }

    /** Centro do gol atacado pela equipe indicada. */
    public Vec2 centroGolAtacado(Cor cor) {
        return centroGolDefendido(cor.oposta());
    }

    /** True se o ponto esta dentro das linhas do campo (sem a faixa externa). */
    public boolean dentroDoCampo(Vec2 p) {
        return Math.abs(p.x()) <= meioComprimento() && Math.abs(p.y()) <= meiaLargura();
    }
}
