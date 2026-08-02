public class Bola {
    public double x = 0, y = 0;
    public double vx = 0, vy = 0;
    public final double radius = 2.0;

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
}