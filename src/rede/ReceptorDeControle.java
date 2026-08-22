package rede;

import core.Vec2;
import proto.sim.SslSimulationControl.SimulatorCommand;
import proto.sim.SslSimulationControl.TeleportBall;
import visao.CanalDeControle;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Lado do simulador: escuta {@code SimulatorCommand} e repassa ao canal local.
 *
 * <p>A simetria e proposital -- o receptor so traduz protobuf para chamadas de
 * {@link CanalDeControle}, e quem aplica no mundo continua sendo o mesmo
 * {@code ControleLocal} que a interface local usa. Nao ha um segundo caminho de
 * escrita no mundo para manter em dia.
 */
public final class ReceptorDeControle implements AutoCloseable {

    private static final double MM_POR_M = 1000.0;

    private final DatagramSocket socket;
    private final Thread thread;
    private final CanalDeControle destino;
    private volatile long comandosRecebidos;

    public ReceptorDeControle(int porta, CanalDeControle destino) throws IOException {
        this.destino = destino;
        this.socket = new DatagramSocket(porta);
        this.thread = new Thread(this::escutar, "ssl-controle");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public long getComandosRecebidos() { return comandosRecebidos; }

    private void escutar() {
        byte[] buffer = new byte[Enderecos.TAMANHO_BUFFER];
        while (!socket.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                socket.receive(p);
                SimulatorCommand cmd = SimulatorCommand.parseFrom(
                        java.util.Arrays.copyOf(p.getData(), p.getLength()));
                comandosRecebidos++;
                aplicar(cmd);
            } catch (IOException e) {
                if (!socket.isClosed()) System.err.println("controle: " + e.getMessage());
            }
        }
    }

    private void aplicar(SimulatorCommand cmd) {
        if (!cmd.hasControl() || !cmd.getControl().hasTeleportBall()) return;
        TeleportBall t = cmd.getControl().getTeleportBall();
        // Protocolo em metros, mundo em milimetros.
        destino.reposicionarBola(
                new Vec2(t.getX() * MM_POR_M, t.getY() * MM_POR_M),
                new Vec2(t.getVx() * MM_POR_M, t.getVy() * MM_POR_M));
    }

    @Override
    public void close() { socket.close(); }
}
