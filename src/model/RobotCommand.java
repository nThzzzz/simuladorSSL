package model;

/**
 * Setpoint enviado aos atuadores de um robo, no referencial LOCAL do robo --
 * mesma convencao do grSim e do protocolo ssl_simulation_robot_control.
 *
 * <p>Esta e a unica porta de entrada de atuacao no mundo: qualquer Skill,
 * Tactic ou controle manual precisa produzir um RobotCommand. E por isso que o
 * log de acoes e confiavel -- registramos o comando emitido, nao uma inferencia
 * feita depois a partir da trajetoria.
 *
 * @param velTangencial mm/s no eixo frontal do robo (+x local = frente)
 * @param velNormal     mm/s no eixo lateral do robo (+y local = esquerda)
 * @param velAngular    rad/s, positivo no sentido anti-horario
 * @param velChute      mm/s do chute; 0 = sem chute neste quadro
 * @param anguloChute   elevacao do chute em radianos; 0 = rasteiro, >0 = chip
 * @param dribbler      true se o rolo dribbler esta acionado
 */
public record RobotCommand(
        double velTangencial,
        double velNormal,
        double velAngular,
        double velChute,
        double anguloChute,
        boolean dribbler
) {
    /** Elevacao tipica do mecanismo de chip de um robo da SSL. */
    public static final double ANGULO_CHIP_PADRAO = Math.toRadians(45);

    public static final RobotCommand PARADO = new RobotCommand(0, 0, 0, 0, 0, false);

    public static RobotCommand mover(double velTangencial, double velNormal, double velAngular) {
        return new RobotCommand(velTangencial, velNormal, velAngular, 0, 0, false);
    }

    public boolean temChute() { return velChute > 0; }

    /** True se o chute sai do chao. */
    public boolean ehChip() { return velChute > 0 && anguloChute > 0; }

    public RobotCommand comChute(double velChute) {
        return new RobotCommand(velTangencial, velNormal, velAngular, velChute, 0, dribbler);
    }

    public RobotCommand comChip(double velChute, double anguloChute) {
        return new RobotCommand(velTangencial, velNormal, velAngular,
                velChute, anguloChute, dribbler);
    }

    public RobotCommand comDribbler(boolean ligado) {
        return new RobotCommand(velTangencial, velNormal, velAngular,
                velChute, anguloChute, ligado);
    }
}
