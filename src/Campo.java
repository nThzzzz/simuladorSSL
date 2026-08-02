import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;

public class Campo extends JPanel {
    public static final int FIELD_WIDTH = 900;
    public static final int FIELD_HEIGHT = 600;
    public static final int MARGIN = 50;

    public double zoomFactor = 1.0;
    private Mundo mundo; // Referência ao estado do jogo

    // Variáveis visuais da interface (mira e mouse)
    public int mouseX = -1, mouseY = -1;
    public boolean showAim = false;
    public double dragX = 0, dragY = 0;

    public Campo(Mundo mundo) {
        this.mundo = mundo;
        setPreferredSize(new Dimension(FIELD_WIDTH + 2 * MARGIN, FIELD_HEIGHT + 2 * MARGIN));
        setBackground(new Color(20, 20, 20));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform originalTransform = g2d.getTransform();
        g2d.translate(MARGIN + FIELD_WIDTH / 2.0, MARGIN + FIELD_HEIGHT / 2.0);
        g2d.scale(zoomFactor, zoomFactor);
        g2d.scale(1, -1);

        drawField(g2d);
        drawBall(g2d);
        for (Robot r : mundo.robots) {
            drawRobot(g2d, r);
        }

        if (showAim) drawAimVector(g2d);

        g2d.setTransform(originalTransform);

        drawCursorInfo(g2d);
        drawHUD(g2d);
    }

    private void drawHUD(Graphics2D g2d) {
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.setColor(Color.WHITE);

        // Converte de cm/s para m/s dividindo por 100
        double velMS = mundo.bola.getVelocidade() / 100.0;

        g2d.drawString(String.format("Velocidade da Bola: %.2f m/s", velMS), 20, 30);
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

    private void drawRobot(Graphics2D g2d, Robot r) {
        double radius = 9;

        AffineTransform oldTransform = g2d.getTransform();
        g2d.translate(r.x, r.y);
        g2d.rotate(r.theta);

        g2d.setColor(new Color(40, 40, 40));
        Arc2D.Double body = new Arc2D.Double(-radius, -radius, radius * 2, radius * 2, 40, 280, Arc2D.CHORD);
        g2d.fill(body);

        g2d.setColor(r.isBlue ? new Color(0, 100, 255) : new Color(255, 210, 0));
        g2d.fillOval(-3, -3, 6, 6);

        g2d.rotate(-r.theta);
        g2d.scale(1, -1);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(String.valueOf(r.id));
        int textHeight = fm.getAscent();

        g2d.drawString(String.valueOf(r.id), -textWidth / 2, textHeight / 2 - 1);

        g2d.setTransform(oldTransform);
    }

    private void drawCursorInfo(Graphics2D g2d) {
        double centerX = MARGIN + FIELD_WIDTH / 2.0;
        double centerY = MARGIN + FIELD_HEIGHT / 2.0;

        double cartesianX = (mouseX - centerX) / zoomFactor;
        double cartesianY = (centerY - mouseY) / zoomFactor;

        String zoom = String.format("X: %.0f | Y: %.0f", cartesianX, cartesianY);

        if (Math.abs(cartesianX) <= FIELD_WIDTH / 2.0 && Math.abs(cartesianY) <= FIELD_HEIGHT / 2.0) {
            String text = String.format("X: %.0f | Y: %.0f", cartesianX, cartesianY);

            g2d.setFont(new Font("Monospaced", Font.BOLD, 12));
            FontMetrics fm = g2d.getFontMetrics();
            int padding = 6;
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getAscent();

            g2d.setColor(new Color(0, 0, 0, 180));
            int boxX = mouseX + 12;
            int boxY = mouseY + 12;
            g2d.fillRoundRect(boxX, boxY, textWidth + padding * 2, textHeight + padding, 5, 5);

            g2d.setColor(Color.WHITE);
            g2d.drawString(text, boxX + padding, boxY + textHeight + padding / 2 - 1);
        }
    }

    private void drawBall(Graphics2D g2d) {
        AffineTransform old = g2d.getTransform();
        g2d.translate(mundo.bola.x, mundo.bola.y);

        g2d.setColor(new Color(255, 140, 0));
        g2d.fillOval((int)-mundo.bola.radius, (int)-mundo.bola.radius, (int)mundo.bola.radius * 2, (int)mundo.bola.radius * 2);

        g2d.setColor(new Color(0, 0, 0, 200));
        g2d.setStroke(new BasicStroke(0.5f));
        g2d.drawOval((int)-mundo.bola.radius, (int)-mundo.bola.radius, (int)mundo.bola.radius * 2, (int)mundo.bola.radius * 2);

        g2d.setTransform(old);
    }

    private void drawAimVector(Graphics2D g2d) {
        double dx = (dragX - mundo.bola.x);
        double dy = (dragY - mundo.bola.y);

        g2d.setColor(new Color(255, 50, 50, 200));
        g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine((int)mundo.bola.x, (int)mundo.bola.y, (int)(mundo.bola.x + dx), (int)(mundo.bola.y + dy));
    }
}
