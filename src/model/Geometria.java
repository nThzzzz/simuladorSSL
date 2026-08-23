package model;

import core.Caixa;
import core.Vec2;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometria do campo em milimetros, conforme as regras oficiais da SSL.
 * A origem (0,0) fica no centro do campo, +x aponta para o gol amarelo
 * e +y para a lateral superior -- mesma convencao da SSL-Vision.
 *
 * <p>O gol nao e um retangulo pintado atras da linha de fundo: e uma estrutura
 * de tres paredes, dois postes e o fundo, com espessura e altura proprias. A
 * versao anterior guardava so largura e profundidade e a fisica nao sabia que o
 * gol existia -- a bola atravessava o gol inteiro e ia quicar na parede da faixa
 * externa, 300 mm atras. {@link #golProfundidade()} e a profundidade INTERNA,
 * como no regulamento, entao a pegada total do gol e ela mais a espessura da
 * parede do fundo.
 */
public record Geometria(
        double comprimento,             // eixo X, linha de fundo a linha de fundo
        double largura,                 // eixo Y, lateral a lateral
        double faixaExterna,            // area de escape ate a parede fisica
        double golLargura,
        double golProfundidade,         // interna: da linha de fundo a face do fundo
        double golEspessuraParede,
        double golAltura,               // acima disso a bola passa por cima do gol
        double areaDefesaProfundidade,
        double areaDefesaLargura,
        double raioCirculoCentral,
        double espessuraLinha
) {
    public static Geometria divisaoB() {
        return new Geometria(9000, 6000, 300, 1000, 180, 20, 155, 1000, 2000, 500, 10);
    }

    public static Geometria divisaoA() {
        return new Geometria(12000, 9000, 300, 1800, 180, 20, 155, 1800, 3600, 500, 10);
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

    // -------------------------------------------------------------------- gol

    /**
     * O vazio dentro do gol, entre os postes e a face do fundo.
     *
     * <p>Aberto pela boca: e uma caixa so para efeito de desenho e de consulta,
     * a fisica nunca a usa como parede.
     */
    public Caixa cavidadeDoGol(int lado) {
        return Caixa.de(lado * meioComprimento(), -golLargura / 2.0,
                        lado * (meioComprimento() + golProfundidade), golLargura / 2.0);
    }

    /**
     * As tres paredes do gol do lado indicado: {@code +1} para o gol em +x.
     *
     * <p>Os postes vao da linha de fundo ate o fim da estrutura, e o fundo os
     * atravessa por tras. Assim o canto interno fica fechado sem sobreposicao
     * duvidosa: a bola que entra rente ao poste nao acha fresta no canto.
     */
    public List<ParedeDoGol> paredesDoGol(int lado) {
        double xBoca   = meioComprimento();
        double xFundo  = xBoca + golProfundidade;
        double xCostas = xFundo + golEspessuraParede;
        double yInterno = golLargura / 2.0;
        double yExterno = yInterno + golEspessuraParede;
        String prefixo = lado > 0 ? "gol_x_max" : "gol_x_min";

        return List.of(
                new ParedeDoGol(prefixo + "_poste_y_max",
                        Caixa.de(lado * xBoca, yInterno, lado * xCostas, yExterno)),
                new ParedeDoGol(prefixo + "_poste_y_min",
                        Caixa.de(lado * xBoca, -yInterno, lado * xCostas, -yExterno)),
                new ParedeDoGol(prefixo + "_fundo",
                        Caixa.de(lado * xFundo, -yExterno, lado * xCostas, yExterno)));
    }

    /** As seis paredes dos dois gols, do lado -x para o +x. */
    public List<ParedeDoGol> paredesDosGols() {
        List<ParedeDoGol> todas = new ArrayList<>(paredesDoGol(-1));
        todas.addAll(paredesDoGol(1));
        return List.copyOf(todas);
    }
}
