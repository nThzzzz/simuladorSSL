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
 * @param velChute      mm/s do chute plano; 0 = sem chute neste quadro
 * @param velChip       mm/s do chute por elevacao; 0 = sem chip
 * @param dribbler      true se o rolo dribbler esta acionado
 */
public record RobotCommand(
        double velTangencial,
        double velNormal,
        double velAngular,
        double velChute,
        double velChip,
        boolean dribbler
) {
    public static final RobotCommand PARADO = new RobotCommand(0, 0, 0, 0, 0, false);

    public static RobotCommand mover(double velTangencial, double velNormal, double velAngular) {
        return new RobotCommand(velTangencial, velNormal, velAngular, 0, 0, false);
    }

    public boolean temChute() { return velChute > 0 || velChip > 0; }

    public RobotCommand comChute(double velChute) {
        return new RobotCommand(velTangencial, velNormal, velAngular, velChute, velChip, dribbler);
    }

    public RobotCommand comDribbler(boolean ligado) {
        return new RobotCommand(velTangencial, velNormal, velAngular, velChute, velChip, ligado);
    }
}
