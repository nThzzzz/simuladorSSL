package model;

import core.Angulo;
import core.Vec2;

/**
 * Estado fisico de um robo omnidirecional da SSL.
 *
 * <p>Guarda apenas estado -- pose, velocidades, limites e o ultimo
 * {@link RobotCommand} aplicado. Nao decide nada: a decisao vive na camada de
 * controle (Skill / Tactic / Role / Play) e chega aqui ja na forma de comando.
 */
public final class Robot {

    public static final double RAIO = 90.0;              // mm, envelope de 180 mm de diametro
    public static final double DIST_FACE_FRONTAL = 72.5; // mm, face plana do dribbler
    public static final double MASSA = 2.5;              // kg

    // Limites tipicos de um robo da SSL.
    public static final double VEL_MAX_PADRAO = 3000.0;   // mm/s
    public static final double ACEL_MAX_PADRAO = 3000.0;  // mm/s^2
    public static final double OMEGA_MAX_PADRAO = 20.0;   // rad/s
    public static final double ACEL_ANG_MAX_PADRAO = 50.0;// rad/s^2

    private final int id;
    private final Cor cor;

    private Vec2 posicao;
    private double theta;
    private Vec2 velocidade = Vec2.ZERO;
    private double omega = 0;

    private double velMax = VEL_MAX_PADRAO;
    private double acelMax = ACEL_MAX_PADRAO;
    private double omegaMax = OMEGA_MAX_PADRAO;
    private double acelAngularMax = ACEL_ANG_MAX_PADRAO;

    private RobotCommand comando = RobotCommand.PARADO;
    private boolean bolaNoDribbler = false;

    public Robot(int id, Cor cor, Vec2 posicao, double theta) {
        this.id = id;
        this.cor = cor;
        this.posicao = posicao;
        this.theta = Angulo.normalizar(theta);
    }

    public int getId()   { return id; }
    public Cor getCor()  { return cor; }

    public Vec2 getPosicao()    { return posicao; }
    public double getTheta()    { return theta; }
    public Vec2 getVelocidade() { return velocidade; }
    public double getOmega()    { return omega; }
    public double getRapidez()  { return velocidade.norma(); }

    public void setPosicao(Vec2 p)    { this.posicao = p; }
    public void setTheta(double t)    { this.theta = Angulo.normalizar(t); }
    public void setVelocidade(Vec2 v) { this.velocidade = v; }
    public void setOmega(double w)    { this.omega = w; }

    public double getVelMax()         { return velMax; }
    public double getAcelMax()        { return acelMax; }
    public double getOmegaMax()       { return omegaMax; }
    public double getAcelAngularMax() { return acelAngularMax; }

    public void setVelMax(double v)         { this.velMax = v; }
    public void setAcelMax(double a)        { this.acelMax = a; }
    public void setOmegaMax(double w)       { this.omegaMax = w; }
    public void setAcelAngularMax(double a) { this.acelAngularMax = a; }

    public RobotCommand getComando()             { return comando; }
    public void setComando(RobotCommand c)       { this.comando = c; }

    public boolean temBolaNoDribbler()           { return bolaNoDribbler; }
    public void setBolaNoDribbler(boolean b)     { this.bolaNoDribbler = b; }

    /** Vetor unitario apontando para a frente do robo (direcao do chutador). */
    public Vec2 direcaoFrontal() { return Vec2.dePolar(1.0, theta); }

    /** Ponto no centro da face plana do dribbler, em coordenadas globais. */
    public Vec2 pontoDribbler() {
        return posicao.mais(Vec2.dePolar(DIST_FACE_FRONTAL, theta));
    }

    /** Identificador estavel usado nos logs, ex.: "blue_3". */
    public String chave() { return cor.tag() + "_" + id; }

    @Override
    public String toString() {
        return String.format("Robot[%s pos=%s theta=%.2f]", chave(), posicao, theta);
    }
}
