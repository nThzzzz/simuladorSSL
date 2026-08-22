package model;

import core.Vec2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Uma das duas equipes da partida.
 *
 * <p>Antes chamava-se {@code Time}, o que colidia conceitualmente com o tempo de
 * simulacao. A formacao inicial agora e calculada a partir da {@link Geometria}
 * do campo, e nao das dimensoes do painel Swing -- que incluiam a margem e
 * deslocavam toda a formacao.
 */
public final class Equipe {

    private final Cor cor;
    private String nome;
    private final List<Robot> robos = new ArrayList<>();

    public Equipe(String nome, Cor cor) {
        this.nome = nome;
        this.cor = cor;
    }

    public String getNome()  { return nome; }
    public Cor getCor()      { return cor; }
    public List<Robot> getRobos() { return Collections.unmodifiableList(robos); }
    public int getNumRobos() { return robos.size(); }

    public void setNome(String nome) { this.nome = nome; }

    public Robot getRobo(int id) {
        for (Robot r : robos) if (r.getId() == id) return r;
        return null;
    }

    /**
     * Recria os robos numa cruz, todos encarando o centro do campo.
     *
     * <p>A cruz cresce em aneis a partir do proprio centro: primeiro o robo do
     * meio, depois os quatro bracos, depois o segundo anel de bracos, e assim por
     * diante. Escrever assim em vez de tabelar coordenadas faz a formacao valer
     * para qualquer quantidade de robos sem lista magica -- 6 na Divisao B, 11 na
     * Divisao A -- e mantem o desenho de cruz em todos os casos.
     *
     * <p>Tudo e calculado em fracao da {@link Geometria}, entao a cruz acompanha o
     * tamanho do campo. O eixo X e montado no referencial da equipe (+x aponta
     * para o proprio gol) e so no fim recebe o sinal do lado defendido.
     *
     * <p>Os bracos encolhem conforme a quantidade de robos para que o anel mais
     * externo ainda caiba no proprio campo. Sem isso, 11 robos na Divisao A
     * jogariam o ultimo anel para fora da linha de fundo e para dentro do campo
     * adversario -- posicao ilegal num inicio de partida.
     */
    public void posicionarFormacao(Geometria geo, int quantidade) {
        robos.clear();

        int lado = Geometria.ladoDefendido(cor);
        int n = Math.max(0, quantidade);
        if (n == 0) return;

        double centroX = geo.meioComprimento() * 0.49;   // 2205 mm na Divisao B
        double passoX = geo.meioComprimento() * 0.18;    //  810 mm
        double passoY = geo.meiaLargura() * 0.47;        // 1410 mm

        // Quantos aneis de quatro bracos sao necessarios, e o quanto cada braco
        // pode medir sem que o anel mais externo saia do proprio campo.
        int aneis = Math.max(1, (n - 1 + 3) / 4);
        double limiteFundo = (geo.meioComprimento() - Robot.RAIO - centroX) / aneis;
        double limiteFrente = (centroX - Robot.RAIO) / aneis;
        double limiteLateral = (geo.meiaLargura() - Robot.RAIO) / aneis;

        passoX = Math.min(passoX, Math.min(limiteFundo, limiteFrente));
        passoY = Math.min(passoY, limiteLateral);

        // Ordem dos bracos: fundo, frente, cima, baixo. Repete a cada anel.
        Vec2[] bracos = {
                new Vec2(passoX, 0), new Vec2(-passoX, 0),
                new Vec2(0, passoY), new Vec2(0, -passoY)
        };
        for (int i = 0; i < n; i++) {
            Vec2 local;
            if (i == 0) {
                local = new Vec2(centroX, 0);
            } else {
                int anel = (i - 1) / bracos.length + 1;
                Vec2 braco = bracos[(i - 1) % bracos.length];
                local = new Vec2(centroX + braco.x() * anel, braco.y() * anel);
            }

            Vec2 posicao = new Vec2(lado * local.x(), local.y());
            robos.add(new Robot(i, cor, posicao, anguloParaOCentro(posicao)));
        }
    }

    /** Orientacao que faz a face do dribbler encarar o meio do campo. */
    private static double anguloParaOCentro(Vec2 posicao) {
        return posicao.negado().angulo();
    }
}
