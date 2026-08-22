package rede;

import model.Cor;
import sim.ControladorExterno;
import model.RobotCommand;
import proto.sim.SslSimulationRobotControl.MoveLocalVelocity;
import proto.sim.SslSimulationRobotControl.RobotControl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Lado do simulador: escuta {@code RobotControl} de uma equipe.
 *
 * <p>E por aqui que um software de time se conecta. Nao ha IA neste projeto, mas
 * a porta existe -- e o que faz este simulador ser um substituto do grSim em vez
 * de um brinquedo fechado.
 *
 * <p>{@code MoveLocalVelocity{forward, left, angular}} bate exatamente com o
 * {@link RobotCommand} daqui; so muda a unidade (m/s no protocolo, mm/s no
 * mundo). Os outros dois modos de movimento do protocolo -- velocidade global e
 * velocidade de roda -- ainda nao sao tratados.
 */
public final class ReceptorDeComandosRobo implements AutoCloseable {

    private static final double MM_POR_M = 1000.0;

    private final DatagramSocket socket;
    private final Thread thread;
    private final ControladorExterno alvo;
    private final Cor cor;
    private volatile long pacotesRecebidos;

    public ReceptorDeComandosRobo(int porta, Cor cor, ControladorExterno alvo)
            throws IOException {
        this.cor = cor;
        this.alvo = alvo;
        this.socket = new DatagramSocket(porta);
        this.thread = new Thread(this::escutar, "ssl-comandos-" + cor.tag());
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public long getPacotesRecebidos() { return pacotesRecebidos; }

    private void escutar() {
        byte[] buffer = new byte[Enderecos.TAMANHO_BUFFER];
        while (!socket.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                socket.receive(p);
                RobotControl rc = RobotControl.parseFrom(
                        java.util.Arrays.copyOf(p.getData(), p.getLength()));
                pacotesRecebidos++;
                for (var c : rc.getRobotCommandsList()) alvo.receber(cor, c.getId(), traduzir(c));
            } catch (IOException e) {
                if (!socket.isClosed()) System.err.println("comandos: " + e.getMessage());
            }
        }
    }

    private static RobotCommand traduzir(proto.sim.SslSimulationRobotControl.RobotCommand c) {
        double vt = 0, vn = 0, w = 0;
        if (c.hasMoveCommand() && c.getMoveCommand().hasLocalVelocity()) {
            MoveLocalVelocity m = c.getMoveCommand().getLocalVelocity();
            vt = m.getForward() * MM_POR_M;
            vn = m.getLeft() * MM_POR_M;
            w = m.getAngular();
        }

        // No protocolo o chute e uma velocidade mais um angulo em graus; aqui sao
        // dois campos separados. Angulo zero e chute rasteiro, o resto e chip.
        double chute = 0, chip = 0;
        if (c.hasKickSpeed() && c.getKickSpeed() > 0) {
            if (c.getKickAngle() > 0) chip = c.getKickSpeed() * MM_POR_M;
            else chute = c.getKickSpeed() * MM_POR_M;
        }

        // Dribbler e RPM no protocolo e booleano aqui.
        boolean dribbler = c.hasDribblerSpeed() && c.getDribblerSpeed() > 0;

        return new RobotCommand(vt, vn, w, chute, chip, dribbler);
    }

    @Override
    public void close() { socket.close(); }
}
