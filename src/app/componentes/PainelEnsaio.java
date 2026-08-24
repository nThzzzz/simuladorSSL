package app.componentes;

import app.fisica.Amostra;
import app.fisica.Ensaio;
import app.fisica.Trajetoria;
import app.fisica.Vista;

import core.Vec2;
import model.Bola;
import model.Robot;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * Faixa animada que compara duas execucoes do mesmo ensaio.
 *
 * <p>Os dois traçados dividem a mesma faixa e o mesmo relogio, em vez de ficarem
 * lado a lado: assim da para ver uma bola ultrapassar a outra, que e exatamente
 * a comparacao que interessa. O "antes" e fantasma, o "depois" e solido.
 *
 * <p>O enquadramento se ajusta aos dois traçados. Um limite fixo faria o traçado
 * mais curto virar um ponto quando o parametro esta no extremo oposto da faixa.
 */
public final class PainelEnsaio extends JPanel {

    private static final Color FUNDO   = new Color(24, 24, 27);
    private static final Color CHAO    = new Color(70, 74, 80);
    private static final Color PAREDE  = new Color(150, 120, 90);
    private static final Color ANTES   = new Color(130, 132, 140);
    private static final Color DEPOIS  = new Color(255, 150, 40);
    private static final Color ROBO    = new Color(60, 64, 72);

    /** O ensaio inteiro cabe neste tempo de exibicao, por mais longo que seja. */
    private static final double SEGUNDOS_DE_EXIBICAO = 3.5;
    private static final int QUADROS_POR_SEGUNDO = 30;

    private final Ensaio ensaio;
    private Trajetoria antes;
    private Trajetoria depois;

    private int indice;
    private int passoPorTique = 1;

    public PainelEnsaio(Ensaio ensaio) {
        this.ensaio = ensaio;
        setMinimumSize(new Dimension(200, 40));
        setBackground(FUNDO);
    }

    public void mostrar(Trajetoria antes, Trajetoria depois) {
        this.antes = antes;
        this.depois = depois;
        this.indice = 0;
        int maior = Math.max(tamanho(antes), tamanho(depois));
        this.passoPorTique = Math.max(1,
                (int) Math.ceil(maior / (SEGUNDOS_DE_EXIBICAO * QUADROS_POR_SEGUNDO)));
        repaint();
    }

    /** Avanca a animacao um tique. Quem chama e o relogio unico do dialogo. */
    public void avancar() {
        int maior = Math.max(tamanho(antes), tamanho(depois));
        if (maior == 0) return;
        // Uma pausa no fim para o olho comparar as duas posicoes finais.
        int pausa = QUADROS_POR_SEGUNDO;
        indice = (indice + passoPorTique) % (maior + pausa * passoPorTique);
        repaint();
    }

    private static int tamanho(Trajetoria t) { return t == null ? 0 : t.amostras().size(); }

    // ----------------------------------------------------------------- desenho

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (antes == null && depois == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double[] j = enquadramento();
        desenharCenario(g2, j);
        if (ensaio.mostraBola()) {
            desenharRastro(g2, j, antes, ANTES, 1.4f);
            desenharRastro(g2, j, depois, DEPOIS, 2.2f);
            desenharRobos(g2, j);
            desenharBola(g2, j, antes, ANTES, 0.75);
            desenharBola(g2, j, depois, DEPOIS, 1.0);
        } else {
            desenharRobosMoveis(g2, j, antes, ANTES);
            desenharRobosMoveis(g2, j, depois, DEPOIS);
        }

        g2.dispose();
    }

    /** {@code {xMin, xMax, vMin, vMax}} cobrindo os limites do ensaio e os dois traçados. */
    private double[] enquadramento() {
        double[] j = ensaio.limites().clone();
        for (Trajetoria t : List.of(existentes())) {
            for (Amostra a : t.amostras()) {
                if (ensaio.mostraBola()) {
                    j[0] = Math.min(j[0], a.bola().x());
                    j[1] = Math.max(j[1], a.bola().x());
                    double v = ensaio.vista() == Vista.PERFIL ? a.z() : a.bola().y();
                    j[2] = Math.min(j[2], v);
                    j[3] = Math.max(j[3], v);
                }
                for (Vec2 r : a.robos()) {
                    j[0] = Math.min(j[0], r.x());
                    j[1] = Math.max(j[1], r.x());
                }
            }
        }
        double margemX = (j[1] - j[0]) * 0.04 + Bola.RAIO * 2;
        j[0] -= margemX;
        j[1] += margemX;
        return j;
    }

    private Trajetoria[] existentes() {
        if (antes != null && depois != null) return new Trajetoria[]{antes, depois};
        if (antes != null) return new Trajetoria[]{antes};
        if (depois != null) return new Trajetoria[]{depois};
        return new Trajetoria[0];
    }

    private void desenharCenario(Graphics2D g, double[] j) {
        g.setColor(CHAO);
        g.setStroke(new BasicStroke(1f));
        double linha = ensaio.vista() == Vista.PERFIL ? 0 : 0; // chao ou eixo central
        int y = paraTelaY(linha, j);
        g.draw(new Line2D.Double(0, y, getWidth(), y));

        if (!Double.isNaN(ensaio.parede())) {
            int x = paraTelaX(ensaio.parede(), j);
            g.setColor(PAREDE);
            g.setStroke(new BasicStroke(2.5f));
            g.draw(new Line2D.Double(x, 4, x, getHeight() - 4));
        }
    }

    private void desenharRastro(Graphics2D g, double[] j, Trajetoria t, Color cor, float grossura) {
        if (t == null || t.amostras().isEmpty()) return;
        Path2D.Double caminho = new Path2D.Double();
        boolean primeiro = true;
        for (Amostra a : t.amostras()) {
            double v = ensaio.vista() == Vista.PERFIL ? a.z() : a.bola().y();
            double x = paraTelaX(a.bola().x(), j);
            double y = paraTelaY(v, j);
            if (primeiro) { caminho.moveTo(x, y); primeiro = false; }
            else caminho.lineTo(x, y);
        }
        g.setColor(new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), 70));
        g.setStroke(new BasicStroke(grossura));
        g.draw(caminho);
    }

    private void desenharRobos(Graphics2D g, double[] j) {
        Trajetoria t = depois != null ? depois : antes;
        if (t == null || t.amostras().isEmpty()) return;
        Amostra a = t.amostras().get(Math.min(indice, t.amostras().size() - 1));
        if (a.robos().length == 0) return;

        double escala = escala(j);
        double raio = Math.max(3, Robot.RAIO * escala);
        g.setColor(ROBO);
        for (Vec2 r : a.robos()) {
            double v = ensaio.vista() == Vista.PERFIL ? 0 : r.y();
            g.fill(new Ellipse2D.Double(paraTelaX(r.x(), j) - raio, paraTelaY(v, j) - raio,
                    raio * 2, raio * 2));
        }
    }

    /** Quando quem se move sao os robos, sao eles que ganham corpo e rastro. */
    private void desenharRobosMoveis(Graphics2D g, double[] j, Trajetoria t, Color cor) {
        if (t == null || t.amostras().isEmpty()) return;
        Amostra a = t.amostras().get(Math.min(indice, t.amostras().size() - 1));
        double raio = Math.max(4, Robot.RAIO * escala(j));
        for (Vec2 r : a.robos()) {
            g.setColor(cor);
            g.fill(new Ellipse2D.Double(paraTelaX(r.x(), j) - raio, paraTelaY(r.y(), j) - raio,
                    raio * 2, raio * 2));
        }
    }

    private void desenharBola(Graphics2D g, double[] j, Trajetoria t, Color cor, double peso) {
        if (t == null || t.amostras().isEmpty()) return;
        Amostra a = t.amostras().get(Math.min(indice, t.amostras().size() - 1));
        double v = ensaio.vista() == Vista.PERFIL ? a.z() : a.bola().y();
        double raio = Math.max(2.5, 4.5 * peso);
        g.setColor(cor);
        g.fill(new Ellipse2D.Double(paraTelaX(a.bola().x(), j) - raio,
                paraTelaY(v, j) - raio, raio * 2, raio * 2));
    }

    // Escala unica nos dois eixos preservaria a proporcao, mas achataria os
    // ensaios longos numa linha. Aqui cada eixo usa a sua.
    private int paraTelaX(double x, double[] j) {
        return (int) Math.round((x - j[0]) / (j[1] - j[0]) * (getWidth() - 8) + 4);
    }

    private int paraTelaY(double v, double[] j) {
        double faixa = Math.max(1, j[3] - j[2]);
        return (int) Math.round(getHeight() - 6 - (v - j[2]) / faixa * (getHeight() - 14));
    }

    private double escala(double[] j) {
        return (getWidth() - 8) / (j[1] - j[0]);
    }
}
