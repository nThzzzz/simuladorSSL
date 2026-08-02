package Engine;

import Model.Bola;
import Model.Robot;
import Model.Time;
import View.Campo;

import java.util.ArrayList;
import java.util.List;

public class Mundo {
    private final double largura;
    private final double altura;

    private Bola bola;
    private List<Robot> robots;
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
        // Atualiza a posição: Posição Atual = Posição Antiga + (Velocidade * Tempo)
        bola.setX(bola.getX() + (bola.getVx() * dt));
        bola.setY(bola.getY() + (bola.getVy() * dt));

        // Atrito: Multiplica a velocidade atual pelo fator de redução
        bola.setVx(bola.getVx() * 0.98);
        bola.setVy(bola.getVy() * 0.98);

        // Zera velocidade se for muito baixa (Evita cálculos desnecessários de micro-movimentos)
        if (Math.hypot(bola.getVx(), bola.getVy()) < 5) {
            bola.setVx(0);
            bola.setVy(0);
        }

        // Colisão com as bordas
        double meiaLargura = largura / 2.0;
        double meiaAltura = altura / 2.0;

        // Bateu nas laterais (Eixo X)
        if (bola.getX() > meiaLargura || bola.getX() < -meiaLargura) {
            bola.setVx(-bola.getVx() * 0.8); // Inverte a direção e perde 20% da força
            bola.setX(Math.max(-meiaLargura, Math.min(bola.getX(), meiaLargura)));
        }

        // Bateu no fundo/topo (Eixo Y)
        if (bola.getY() > meiaAltura || bola.getY() < -meiaAltura) {
            bola.setVy(-bola.getVy() * 0.8);
            bola.setY(Math.max(-meiaAltura, Math.min(bola.getY(), meiaAltura)));
        }
    }

    public Bola getBola() {
        return this.bola;
    }

    public List<Robot> getRobots() {
        return this.robots;
    }

    public double getLargura() {
        return this.largura;
    }

    public double getAltura() {
        return this.altura;
    }
}