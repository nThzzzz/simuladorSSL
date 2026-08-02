import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;

public class Campo extends JPanel {
    private static final int FIELD_WIDTH = 900;
    private static final int FIELD_HEIGHT = 600;
    private static final int MARGIN = 50;

    // Variáveis para rastrear o mouse
    private int mouseX = -1;
    private int mouseY = -1;
    private boolean isMouseOver = false;
    private double zoomFactor = 1.0;

    private List<Robot> robots = new ArrayList<>();

    private Time time1;
    private Time time2;

    public Campo(String nomeAzul, int qtdAzul, String nomeAmarelo, int qtdAmarelo) {
        setPreferredSize(new Dimension(FIELD_WIDTH + 2 * MARGIN, FIELD_HEIGHT + 2 * MARGIN));
        setBackground(new Color(20, 20, 20));

        // Substituindo os valores fixos pelas variáveis recebidas
        time1 = new Time(nomeAzul, qtdAzul, true);    // Time Azul
        time2 = new Time(nomeAmarelo, qtdAmarelo, false); // Time Amarelo

        robots.addAll(time1.istanciarRobos(this));
        robots.addAll(time2.istanciarRobos(this));

        // ==== Captura os movimentos do mouse ====
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                repaint(); // Pede para redesenhar a tela com a nova posição
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                isMouseOver = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isMouseOver = false;
                repaint();
            }
        };

        addMouseMotionListener(mouseHandler);
        addMouseListener(mouseHandler);

        // ==== NOVO: Captura a rolagem do mouse para o Zoom ====
        addMouseWheelListener(e -> {
            // Pega a rotação exata, lendo perfeitamente mouses normais e trackpads
            double rotacao = e.getPreciseWheelRotation();

            // Multiplica a força da rolagem por 0.05 (5%) para um zoom super suave
            if (rotacao < 0) {
                // Rolar para cima (Aproxima)
                zoomFactor *= (1.0 - (rotacao * 0.05));
            } else if (rotacao > 0) {
                // Rolar para baixo (Afasta)
                zoomFactor /= (1.0 + (rotacao * 0.05));
            }

            // Mantém o limite de zoom
            zoomFactor = Math.max(0.2, Math.min(zoomFactor, 5.0));

            repaint();
        });
    }

    public void atualizarPartida(String nomeAzul, int qtdAzul, String nomeAmarelo, int qtdAmarelo) {
        // Atualiza os times com os novos dados recebidos da interface
        this.time1 = new Time(nomeAzul, qtdAzul, true);
        this.time2 = new Time(nomeAmarelo, qtdAmarelo, false);

        // Limpa a lista antiga e gera os novos robôs
        this.robots.clear();
        this.robots.addAll(this.time1.istanciarRobos(this));
        this.robots.addAll(this.time2.istanciarRobos(this));

        // Força a tela a apagar tudo e desenhar novamente com os novos dados
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Salva o estado original da tela (para desenhar o texto do mouse depois)
        AffineTransform originalTransform = g2d.getTransform();

        // --- MUNDO CARTESIANO DO CAMPO ---
        g2d.translate(MARGIN + FIELD_WIDTH / 2.0, MARGIN + FIELD_HEIGHT / 2.0);

        // NOVO: Aplica o Zoom (multiplica a escala X e Y)
        g2d.scale(zoomFactor, zoomFactor);

        // Inverte o eixo Y (mantido do código original)
        g2d.scale(1, -1);

        drawField(g2d);
        drawBall(g2d);

        for (Robot r : robots) {
            drawRobot(g2d, r);
        }

        // --- HUD (INTERFACE DE TELA) ---
        // Restaura a câmera para o canto superior esquerdo original para desenhar a interface
        g2d.setTransform(originalTransform);

        // Desenha a caixa de posição do mouse por cima de tudo
        if (isMouseOver) {
            drawCursorInfo(g2d);
        }
    }

    private void drawField(Graphics2D g2d) {
        int halfW = FIELD_WIDTH / 2;
        int halfH = FIELD_HEIGHT / 2;

        g2d.setColor(new Color(25, 110, 45));
        g2d.fillRect(-halfW, -halfH, FIELD_WIDTH, FIELD_HEIGHT);

        // Linhas Brancas
        g2d.setColor(Color.WHITE);
        // Grossura diminuída para 1f
        g2d.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Limites do campo
        g2d.drawRect(-halfW, -halfH, FIELD_WIDTH, FIELD_HEIGHT);
        g2d.drawLine(0, -halfH, 0, halfH);

        int circleRadius = 75;
        g2d.drawOval(-circleRadius, -circleRadius, circleRadius * 2, circleRadius * 2);

        int defenseWidth = 100;
        int defenseHeight = 220;
        g2d.drawRect(-halfW, -defenseHeight / 2, defenseWidth, defenseHeight);
        g2d.drawRect(halfW - defenseWidth, -defenseHeight / 2, defenseWidth, defenseHeight);

        int goalWidth = 25;
        int goalHeight = 100;
        g2d.setColor(new Color(60, 130, 255));
        g2d.fillRect(-halfW - goalWidth, -goalHeight / 2, goalWidth, goalHeight);
        g2d.setColor(new Color(255, 210, 0));
        g2d.fillRect(halfW, -goalHeight / 2, goalWidth, goalHeight);
    }

    private void drawBall(Graphics2D g2d) {
        int ballRadius = 2;

        // 1. Preenchimento Laranja
        g2d.setColor(new Color(255, 140, 0));
        g2d.fillOval(-ballRadius, -ballRadius, ballRadius * 2, ballRadius * 2);

        // 2. Contorno Preto Discreto
        // Usando um preto com 200 de opacidade (um pouco transparente) para não pesar muito
        g2d.setColor(new Color(0, 0, 0, 200));
        // Linha super fina (0.5f) para não engolir o raio pequeno da bola
        g2d.setStroke(new BasicStroke(0.5f));
        g2d.drawOval(-ballRadius, -ballRadius, ballRadius * 2, ballRadius * 2);
    }

    private void drawRobot(Graphics2D g2d, Robot r) {
        // Raio 9 pixels = 18cm de diâmetro (Regulamento Oficial SSL)
        double radius = 9;

        AffineTransform oldTransform = g2d.getTransform();
        g2d.translate(r.x, r.y);
        g2d.rotate(r.theta);

        g2d.setColor(new Color(40, 40, 40));
        Arc2D.Double body = new Arc2D.Double(-radius, -radius, radius * 2, radius * 2, 40, 280, Arc2D.CHORD);
        g2d.fill(body);

        g2d.setColor(r.isBlue ? new Color(0, 100, 255) : new Color(255, 210, 0));
        // O círculo do centro do robô também precisa diminuir proporcionalmente
        g2d.fillOval(-3, -3, 6, 6);

        g2d.rotate(-r.theta);
        g2d.scale(1, -1);

        g2d.setColor(Color.WHITE);
        // A fonte diminuiu de 12 para 9, para que o ID caiba em cima desse robô menor
        g2d.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(String.valueOf(r.id));
        int textHeight = fm.getAscent();

        // Ajuste no eixo Y para centralizar perfeitamente a fonte menor
        g2d.drawString(String.valueOf(r.id), -textWidth / 2, textHeight / 2 - 1);

        g2d.setTransform(oldTransform);
    }

    // ==== NOVO: Desenha as coordenadas ao lado do cursor ====
    private void drawCursorInfo(Graphics2D g2d) {
        double centerX = MARGIN + FIELD_WIDTH / 2.0;
        double centerY = MARGIN + FIELD_HEIGHT / 2.0;

        // NOVO: Divide a distância pelo zoom para achar a coordenada real no campo
        double cartesianX = (mouseX - centerX) / zoomFactor;
        double cartesianY = (centerY - mouseY) / zoomFactor;

        // Como o tamanho do campo mudou visualmente, vamos tirar a limitação do "if"
        // que escondia as coordenadas fora da linha branca, assim você sempre vê os números.
        String zoom = String.format("X: %.0f | Y: %.0f", cartesianX, cartesianY);

        // Limita para só mostrar coordenadas se estiver dentro do gramado (opcional)
        // Se quiser ver até na margem preta, pode remover este "if"
        if (Math.abs(cartesianX) <= FIELD_WIDTH / 2.0 && Math.abs(cartesianY) <= FIELD_HEIGHT / 2.0) {
            String text = String.format("X: %.0f | Y: %.0f", cartesianX, cartesianY);

            g2d.setFont(new Font("Monospaced", Font.BOLD, 12));
            FontMetrics fm = g2d.getFontMetrics();
            int padding = 6;
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getAscent();

            // Desenha um fundo semitransparente escuro para o texto dar leitura
            g2d.setColor(new Color(0, 0, 0, 180));
            // Desloca um pouco (12px) para baixo e para a direita para não ficar exatamente embaixo da setinha do mouse
            int boxX = mouseX + 12;
            int boxY = mouseY + 12;
            g2d.fillRoundRect(boxX, boxY, textWidth + padding * 2, textHeight + padding, 5, 5);

            // Desenha o texto por cima
            g2d.setColor(Color.WHITE);
            g2d.drawString(text, boxX + padding, boxY + textHeight + padding / 2 - 1);
        }
    }
}