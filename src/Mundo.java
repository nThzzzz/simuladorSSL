import java.util.ArrayList;
import java.util.List;

public class Mundo {
    public final double largura;
    public final double altura;
    
    public Bola bola;
    public List<Robot> robots;
    private Time timeAzul;
    private Time timeAmarelo;

    public Mundo(double largura, double altura) {
        this.largura = largura;
        this.altura = altura;
        this.bola = new Bola();
        this.robots = new ArrayList<>();
    }

    public void inicializarPartida(String nomeAzul, int qtdAzul, String nomeAmarelo, int qtdAmarelo, Campo dimensoes) {
        this.timeAzul = new Time(nomeAzul, qtdAzul, true);
        this.timeAmarelo = new Time(nomeAmarelo, qtdAmarelo, false);
        
        this.robots.clear();
        this.robots.addAll(timeAzul.istanciarRobos(dimensoes));
        this.robots.addAll(timeAmarelo.istanciarRobos(dimensoes));
    }

    public void updatePhysics(double dt) {
        bola.x += bola.vx * dt;
        bola.y += bola.vy * dt;

        // Atrito
        bola.vx *= 0.98;
        bola.vy *= 0.98;

        // Zera velocidade se for muito baixa
        if (Math.hypot(bola.vx, bola.vy) < 5) {
            bola.vx = 0; bola.vy = 0;
        }

        // Colisão com as bordas
        double meiaLargura = largura / 2.0;
        double meiaAltura = altura / 2.0;

        if (bola.x > meiaLargura || bola.x < -meiaLargura) {
            bola.vx = -bola.vx * 0.8; 
            bola.x = Math.max(-meiaLargura, Math.min(bola.x, meiaLargura)); 
        }
        if (bola.y > meiaAltura || bola.y < -meiaAltura) {
            bola.vy = -bola.vy * 0.8;
            bola.y = Math.max(-meiaAltura, Math.min(bola.y, meiaAltura));
        }
    }
}