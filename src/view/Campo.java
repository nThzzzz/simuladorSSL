package view;

import core.Vec2;
import model.Bola;
import model.Cor;
import model.Geometria;
import model.Robot;
import visao.EstadoBola;
import visao.EstadoMundo;
import visao.EstadoRobo;
import visao.FonteDeVisao;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Renderizacao do campo.
 *
 * <p>Nao le o mundo vivo: recebe uma {@link FonteDeVisao} e desenha o
 * {@link EstadoMundo} que ela entrega.
 *
 * <p>Cada {@code paintComponent} tira UM retrato e usa ele do inicio ao fim. Ler
 * a fonte varias vezes durante o desenho produziria um quadro costurado de
 * instantes diferentes.
 */
public final class Campo extends JPanel {

    private static final Color COR_FUNDO   = new Color(18, 18, 20);
    private static final Color COR_ESCAPE  = new Color(30, 60, 35);
    private static final Color COR_GRAMA   = new Color(25, 110, 45);
    private static final Color COR_LINHA   = new Color(235, 235, 235);
    private static final Color COR_AZUL    = new Color(50, 120, 255);
    private static final Color COR_AMARELO = new Color(255, 205, 0);
    private static final Color COR_BOLA    = new Color(255, 140, 0);
    private static final Color COR_BOLA_ALTA = new Color(255, 190, 90);
    private static final Color COR_CORPO   = new Color(38, 38, 42);

    /**
     * Quanto do arrasto do mouse vira velocidade de chute (1/s). Com ganho 1,0
     * e preciso arrastar o campo inteiro para saturar os 6,5 m/s, o que deixa
     * espaco para dosar um passe fraco -- com o ganho antigo de 2,0 um terco do
     * campo ja estourava o teto e todo chute saia no maximo.
     */
    public static final double GANHO_CHUTE_PADRAO = 1.0;

    /** Quanto cada mm de altura desloca a bola na tela, em mm de mundo. */
    private static final double DESLOCAMENTO_ALTURA = 0.35;


    private final FonteDeVisao fonte;

    private double zoom = 1.0;
    private double panX = 0, panY = 0;
    private double ganhoChute = GANHO_CHUTE_PADRAO;

    private int mouseTelaX = -1, mouseTelaY = -1;
    private boolean mostrarMira = false;
    private boolean mirandoChip = false;
    private Vec2 miraAlvo = Vec2.ZERO;

    // Selecao por identidade, nao por referencia: o quadro e recriado a cada
    // ciclo, entao guardar o objeto do robo nao sobreviveria a um quadro de rede.
    private Cor corSelecionada;
    private int idSelecionado = -1;

    public Campo(FonteDeVisao fonte) {
        this.fonte = fonte;
        setPreferredSize(new Dimension(1060, 760));
        setBackground(COR_FUNDO);
    }

    // ------------------------------------------------------------ interacao

    public EstadoMundo quadro() { return fonte.ultimoQuadro(); }

    public double getZoom() { return zoom; }

    public void aplicarZoom(double fator) {
        zoom = Math.max(0.25, Math.min(zoom * fator, 8.0));
    }

    public void deslocar(double dx, double dy) { panX += dx; panY += dy; }

    public void setMouseTela(int x, int y) { mouseTelaX = x; mouseTelaY = y; }

    public void selecionar(EstadoRobo robo) {
        this.corSelecionada = robo == null ? null : robo.cor();
        this.idSelecionado = robo == null ? -1 : robo.id();
    }

    public void limparSelecao() { selecionar(null); }

    public Cor getCorSelecionada() { return corSelecionada; }
    public int getIdSelecionado()  { return idSelecionado; }

    /** Robo selecionado no quadro atual, ou {@code null} se saiu de campo. */
    public EstadoRobo getSelecionado() {
        return corSelecionada == null ? null : quadro().robo(corSelecionada, idSelecionado);
    }

    public void setMira(boolean ativa, Vec2 alvo) {
        this.mostrarMira = ativa;
        if (alvo != null) this.miraAlvo = alvo;
    }

    public boolean isMostrarMira() { return mostrarMira; }
    public Vec2 getMiraAlvo()      { return miraAlvo; }

    /** Se a mira atual representa um chip; muda cor e rotulo do vetor. */
    public void setMirandoChip(boolean chip) { this.mirandoChip = chip; }

    public double getGanhoChute()           { return ganhoChute; }
    public void setGanhoChute(double ganho) { this.ganhoChute = ganho; }

    /** Velocidade que o chute do mouse produziria com a mira atual. */
    public Vec2 velocidadeDeMira(Vec2 posicaoBola) {
        return miraAlvo.menos(posicaoBola).escala(ganhoChute).limitado(Bola.VEL_MAX);
    }

    /** Converte um ponto da tela para coordenadas de mundo (mm). */
    public Vec2 telaParaMundo(int x, int y) {
        try {
            Point2D p = transformMundo(quadro().geometria())
                    .inverseTransform(new Point2D.Double(x, y), null);
            return new Vec2(p.getX(), p.getY());
        } catch (NoninvertibleTransformException e) {
            return Vec2.ZERO; // so ocorreria com escala zero, que nao acontece aqui
        }
    }

    private double escalaBase(Geometria g) {
        if (getWidth() <= 0 || getHeight() <= 0) return 0.1;
        return Math.min(getWidth() / (g.limiteParedeX() * 2),
                        getHeight() / (g.limiteParedeY() * 2)) * 0.98;
    }

    private AffineTransform transformMundo(Geometria g) {
        double escala = escalaBase(g) * zoom;
        AffineTransform at = new AffineTransform();
        at.translate(getWidth() / 2.0 + panX, getHeight() / 2.0 + panY);
        at.scale(escala, -escala); // inverte Y: mundo cartesiano -> tela
        return at;
    }

    // ------------------------------------------------------------- desenho

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        EstadoMundo q = quadro();
        AffineTransform at = transformMundo(q.geometria());

        Graphics2D gm = (Graphics2D) g2.create();
        gm.transform(at);
        desenharCampo(gm, q.geometria());
        for (EstadoRobo r : q.robos()) desenharRobo(gm, r);
        desenharBola(gm, q.bola());
        if (mostrarMira) desenharMira(gm, q.bola());
        gm.dispose();

        desenharIdentificadores(g2, at, q);
        if (mostrarMira) desenharVelocidadeDeMira(g2, at, q);
        desenharCursor(g2, q);
        desenharHud(g2, q);
        g2.dispose();
    }

    private void desenharCampo(Graphics2D g, Geometria geo) {
        double meioX = geo.meioComprimento();
        double meioY = geo.meiaLargura();

        g.setColor(COR_ESCAPE);
        g.fill(new Rectangle2D.Double(-geo.limiteParedeX(), -geo.limiteParedeY(),
                geo.limiteParedeX() * 2, geo.limiteParedeY() * 2));

        g.setColor(COR_GRAMA);
        g.fill(new Rectangle2D.Double(-meioX, -meioY, geo.comprimento(), geo.largura()));

        g.setColor(COR_LINHA);
        g.setStroke(new BasicStroke((float) geo.espessuraLinha(),
                BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        g.draw(new Rectangle2D.Double(-meioX, -meioY, geo.comprimento(), geo.largura()));
        g.draw(new Line2D.Double(0, -meioY, 0, meioY));
        double rc = geo.raioCirculoCentral();
        g.draw(new Ellipse2D.Double(-rc, -rc, rc * 2, rc * 2));

        double ad = geo.areaDefesaProfundidade();
        double al = geo.areaDefesaLargura();
        g.draw(new Rectangle2D.Double(-meioX, -al / 2, ad, al));
        g.draw(new Rectangle2D.Double(meioX - ad, -al / 2, ad, al));

        double gl = geo.golLargura();
        double gp = geo.golProfundidade();
        g.setColor(COR_AZUL);
        g.fill(new Rectangle2D.Double(-meioX - gp, -gl / 2, gp, gl));
        g.setColor(COR_AMARELO);
        g.fill(new Rectangle2D.Double(meioX, -gl / 2, gp, gl));
    }

    private void desenharRobo(Graphics2D g, EstadoRobo r) {
        AffineTransform anterior = g.getTransform();
        g.translate(r.posicao().x(), r.posicao().y());
        g.rotate(r.theta());

        // Capa circular truncada pela face plana do dribbler.
        Area corpo = new Area(new Ellipse2D.Double(
                -Robot.RAIO, -Robot.RAIO, Robot.RAIO * 2, Robot.RAIO * 2));
        corpo.intersect(new Area(new Rectangle2D.Double(
                -Robot.RAIO, -Robot.RAIO,
                Robot.RAIO + Robot.DIST_FACE_FRONTAL, Robot.RAIO * 2)));

        g.setColor(COR_CORPO);
        g.fill(corpo);

        if (r.cor() == corSelecionada && r.id() == idSelecionado) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(10f));
            g.draw(new Ellipse2D.Double(-Robot.RAIO - 12, -Robot.RAIO - 12,
                    (Robot.RAIO + 12) * 2, (Robot.RAIO + 12) * 2));
        }

        g.setColor(r.cor() == Cor.AZUL ? COR_AZUL : COR_AMARELO);
        g.fill(new Ellipse2D.Double(-25, -25, 50, 50));

        if (r.posse()) {
            g.setColor(new Color(120, 255, 120));
            g.setStroke(new BasicStroke(8f));
            g.draw(new Line2D.Double(Robot.DIST_FACE_FRONTAL, -55,
                    Robot.DIST_FACE_FRONTAL, 55));
        }

        g.setTransform(anterior);
    }

    /**
     * Desenha a bola e, quando ela esta no ar, a sombra no gramado.
     *
     * <p>Num campo visto de cima nao ha profundidade para mostrar altura, entao
     * usa-se a convencao de jogo 2D: a sombra fica na posicao real, no chao, e a
     * bola sobe e cresce. Crescer junto com o deslocamento e o que distingue
     * "bola alta" de "bola deslocada" -- so o deslocamento seria ambiguo.
     *
     * <p>A haste ligando as duas existe porque, sem ela, uma bola alta parece
     * simplesmente estar em outro lugar. Ela ancora visualmente onde a bola vai
     * cair.
     */
    private void desenharBola(Graphics2D g, EstadoBola bola) {
        double x = bola.posicao().x();
        double y = bola.posicao().y();
        double z = bola.z();
        boolean noAr = z > 1;

        if (noAr) {
            // Sombra encolhe e desbota com a altura, como se a camera estivesse
            // acima: e o que faz o olho ler "subiu" em vez de "andou".
            double raioSombra = Bola.RAIO * Math.max(0.45, 1 - z / 3000.0);
            int alfa = (int) Math.max(35, 150 - z * 0.10);
            g.setColor(new Color(0, 0, 0, alfa));
            g.fill(new Ellipse2D.Double(x - raioSombra, y - raioSombra,
                    raioSombra * 2, raioSombra * 2));
        }

        // No espaco de mundo o eixo Y aponta para cima na tela, entao somar em y
        // levanta a bola visualmente.
        double alturaDesenho = y + z * DESLOCAMENTO_ALTURA;
        double raio = Bola.RAIO * (1 + z / 300.0);

        if (noAr) {
            g.setColor(new Color(255, 255, 255, 60));
            g.setStroke(new BasicStroke(3f));
            g.draw(new Line2D.Double(x, y, x, alturaDesenho - raio));
        }

        Shape forma = new Ellipse2D.Double(x - raio, alturaDesenho - raio, raio * 2, raio * 2);
        g.setColor(noAr ? COR_BOLA_ALTA : COR_BOLA);
        g.fill(forma);
        g.setColor(new Color(0, 0, 0, 200));
        g.setStroke(new BasicStroke((float) (4 * (1 + z / 600.0))));
        g.draw(forma);

        // Vetor de velocidade sai da posicao real, no chao: ele e horizontal.
        if (bola.rapidez() > 1) {
            Vec2 ponta = bola.posicao().mais(bola.velocidade().escala(0.25));
            g.setColor(new Color(255, 255, 255, 170));
            g.setStroke(new BasicStroke(6f));
            g.draw(new Line2D.Double(x, y, ponta.x(), ponta.y()));
        }
    }

    private void desenharMira(Graphics2D g, EstadoBola bola) {
        boolean saturado = saturado(bola);
        if (mirandoChip) g.setColor(new Color(120, 200, 255, 220));
        else g.setColor(saturado ? new Color(255, 60, 60, 230) : new Color(255, 190, 60, 220));
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(bola.posicao().x(), bola.posicao().y(),
                miraAlvo.x(), miraAlvo.y()));
    }

    private boolean saturado(EstadoBola bola) {
        return miraAlvo.menos(bola.posicao()).escala(ganhoChute).norma() >= Bola.VEL_MAX;
    }

    /**
     * Mostra a velocidade que o chute vai sair, em espaco de tela. Sem isso nao
     * da para dosar a forca -- o comprimento do vetor sozinho nao diz nada, ainda
     * mais depois que ele satura no teto de 6,5 m/s.
     */
    private void desenharVelocidadeDeMira(Graphics2D g, AffineTransform at, EstadoMundo q) {
        EstadoBola bola = q.bola();
        double velMs = velocidadeDeMira(bola.posicao()).norma() / 1000.0;
        boolean saturado = saturado(bola);

        Point2D meio = new Point2D.Double();
        at.transform(new Point2D.Double(
                (bola.posicao().x() + miraAlvo.x()) / 2,
                (bola.posicao().y() + miraAlvo.y()) / 2), meio);

        String texto = String.format("%.2f m/s%s%s", velMs,
                mirandoChip ? "  chip 45\u00b0" : "", saturado ? "  (teto)" : "");
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(texto) + 12;
        int h = fm.getHeight() + 4;
        int x = (int) meio.getX() - w / 2;
        int y = (int) meio.getY() - h - 8;

        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(saturado ? new Color(255, 110, 110)
                : mirandoChip ? new Color(150, 215, 255) : Color.WHITE);
        g.drawString(texto, x + 6, y + fm.getAscent() + 2);
    }

    /** Numeros dos robos em espaco de tela, para nao herdarem escala nem espelho. */
    private void desenharIdentificadores(Graphics2D g, AffineTransform at, EstadoMundo q) {
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);

        Point2D destino = new Point2D.Double();
        for (EstadoRobo r : q.robos()) {
            at.transform(new Point2D.Double(r.posicao().x(), r.posicao().y()), destino);
            String id = String.valueOf(r.id());
            g.drawString(id,
                    (float) (destino.getX() - fm.stringWidth(id) / 2.0),
                    (float) (destino.getY() + fm.getAscent() / 2.0 - 1));
        }
    }

    private void desenharCursor(Graphics2D g, EstadoMundo q) {
        if (mouseTelaX < 0) return;
        Vec2 p = telaParaMundo(mouseTelaX, mouseTelaY);
        String texto = String.format("x %.0f   y %.0f mm", p.x(), p.y());

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        int pad = 6;
        int w = fm.stringWidth(texto) + pad * 2;
        int h = fm.getHeight() + pad;
        int x = Math.min(mouseTelaX + 14, getWidth() - w - 4);
        int y = Math.min(mouseTelaY + 14, getHeight() - h - 4);

        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(texto, x + pad, y + fm.getAscent() + pad / 2);
    }

    private void desenharHud(Graphics2D g, EstadoMundo q) {
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.WHITE);

        String estado = q.bola().noAr()
                ? String.format("  no ar, %.0f mm", q.bola().z())
                : q.bola().deslizando() ? "  (deslizando)" : "";
        g.drawString(String.format("bola  %.2f m/s%s",
                q.bola().rapidez() / 1000.0, estado), 16, 24);
        g.drawString(String.format("t  %.2f s   quadro %d", q.tempo(), q.frame()), 16, 44);
        g.drawString(String.format("%s %d  x  %d %s",
                q.nomeAzul(), q.quantidade(Cor.AZUL),
                q.quantidade(Cor.AMARELO), q.nomeAmarelo()), 16, 64);

        EstadoRobo sel = getSelecionado();
        if (sel != null) g.drawString("selecionado: " + sel.chave(), 16, 84);
    }
}
