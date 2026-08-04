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
        double novoX = bola.getPosicao().getX() + (bola.getVx() * dt);
        double novoY = bola.getPosicao().getY() + (bola.getVy() * dt);

        bola.setPosicao(novoX, novoY);

        // Multiplica a velocidade atual pelo fator de redução
        bola.setVx(bola.getVx() * 0.97);
        bola.setVy(bola.getVy() * 0.97);

        // Zera velocidade se for muito baixa
        if (Math.hypot(bola.getVx(), bola.getVy()) < 5) {
            bola.setVx(0);
            bola.setVy(0);
        }

        double meiaLargura = largura / 2.0;
        double meiaAltura = altura / 2.0;

        // Bateu nas laterais (Eixo X)
        if (bola.getPosicao().getX() > meiaLargura || bola.getPosicao().getX() < -meiaLargura) {
            bola.setVx(-bola.getVx() * 0.8); // Inverte a direção e perde 20% da força

            double xCorrigido = Math.max(-meiaLargura, Math.min(bola.getPosicao().getX(), meiaLargura));
            bola.setPosicao(xCorrigido, bola.getPosicao().getY());
        }

        // Bateu no fundo/topo (Eixo Y)
        if (bola.getPosicao().getY() > meiaAltura || bola.getPosicao().getY() < -meiaAltura) {
            bola.setVy(-bola.getVy() * 0.8);

            double yCorrigido = Math.max(-meiaAltura, Math.min(bola.getPosicao().getY(), meiaAltura));
            bola.setPosicao(bola.getPosicao().getX(), yCorrigido);
        }

        // Colisão Bola com os Robôs
        for (Robot r : getRobots()) {
            double distancia = r.getPosicao().distance(bola.getPosicao());

            if (distancia < 11) {
                double dx = bola.getPosicao().getX() - r.getPosicao().getX();
                double dy = bola.getPosicao().getY() - r.getPosicao().getY();

                double cosT = Math.cos(r.getTheta());
                double sinT = Math.sin(r.getTheta());

                // Espaço Local (-theta)
                double localX = dx * cosT + dy * sinT;
                double localY = -dx * sinT + dy * cosT;
                double localVx = bola.getVx() * cosT + bola.getVy() * sinT;
                double localVy = -bola.getVx() * sinT + bola.getVy() * cosT;

                double linhaDoChutador = 6.89;
                boolean houveColisao = false;

                if (localX > linhaDoChutador) {
                    // Frente Plana
                    if (localX <= linhaDoChutador + 2.0) {
                        if (localVx < 0) {
                            localVx = -localVx * 0.8;
                            localVy = localVy * 0.8;
                        }
                        localX = linhaDoChutador + 2.01; // Desgruda só 1mm
                        houveColisao = true;
                    }
                } else {
                    // Capa
                    double nx = localX / distancia;
                    double ny = localY / distancia;
                    double dotProduct = (localVx * nx) + (localVy * ny);

                    if (dotProduct < 0) {
                        localVx = (localVx - 2 * dotProduct * nx) * 0.8;
                        localVy = (localVy - 2 * dotProduct * ny) * 0.8;
                    }
                    localX = nx * 11.01;
                    localY = ny * 11.01;
                    houveColisao = true;
                }

                // Se a bola só estava no espaço vazio
                if (houveColisao) {
                    // Retorno para o Espaço Global (+theta)
                    double novoDx = localX * cosT - localY * sinT;
                    double novoDy = localX * sinT + localY * cosT;
                    double novaVx = localVx * cosT - localVy * sinT;
                    double novaVy = localVx * sinT + localVy * cosT;

                    bola.setVx(novaVx);
                    bola.setVy(novaVy);
                    bola.setPosicao(r.getPosicao().getX() + novoDx, r.getPosicao().getY() + novoDy);
                }
            }
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