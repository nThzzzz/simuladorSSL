package Model;

import java.awt.geom.Point2D; // Mudamos a importação para o Point decimal

public class Robot {
    private Point2D.Double posicao; // Agora aceita decimais para a física
    private double theta;
    private boolean isBlue;
    private int id;

    public Robot(Point2D.Double posicao, double theta, boolean isBlue, int id) {
        this.posicao = posicao;
        this.theta = theta;
        this.isBlue = isBlue;
        this.id = id;
    }

    public Point2D.Double getPosicao() {
        return posicao;
    }

    public void setPosicao(double x, double y) {
        this.posicao.setLocation(x, y);
    }

    public double getTheta() {
        return theta;
    }

    public void setTheta(double theta) {
        this.theta = theta;
    }

    public boolean isBlue() {
        return isBlue;
    }

    public int getId() {
        return id;
    }
}