package Model;

public class Bola {
    double x = 0, y = 0;
    double vx = 0, vy = 0;
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

    public double getVelocidade() {
        return Math.hypot(vx, vy);
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
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