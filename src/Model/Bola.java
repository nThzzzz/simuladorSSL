package Model;

import java.awt.*;
import java.awt.geom.Point2D;

public class Bola {
    private Point2D.Double posicao;
    private double vx = 0, vy = 0;
    double radius = 2.0;

    private static final double VELOCIDADE_MAXIMA = 650.0; // 6.5 m/s em cm/s

    public void aplicarForca(double forcaX, double forcaY) {
        // Calcula a força total solicitada
        double velocidadeDesejada = Math.hypot(forcaX, forcaY);

        // Se a força tentar passar do limite, escalamos o vetor para o máximo permitido
        if (velocidadeDesejada > VELOCIDADE_MAXIMA) {
            double escala = VELOCIDADE_MAXIMA / velocidadeDesejada;
            this.vx = forcaX * escala;
            this.vy = forcaY * escala;
        } else {
            // Se for menor que 6.5 m/s, aplica a força normalmente
            this.vx = forcaX;
            this.vy = forcaY;
        }
    }

    public Bola() {
        this.posicao = new Point2D.Double(0.0, 0.0);
    }

    public Point2D.Double getPosicao() {
        return posicao;
    }

    public void setPosicao(double x, double y) {
        this.posicao.setLocation(x, y);
    }

    public double getVelocidade() {
        return Math.hypot(vx, vy);
    }

    public double getVx() {
        return vx;
    }

    public void setVx(double vx) {
        this.vx = vx;
    }

    public double getVy() {
        return vy;
    }

    public void setVy(double vy) {
        this.vy = vy;
    }

    public double getRadius() {
        return radius;
    }
}