package rede;

import model.Bola;
import model.Cor;
import model.Geometria;
import model.Robot;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionBall;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionFrame;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionRobot;
import proto.vision.MessagesRobocupSslGeometry.SSL_GeometryData;
import proto.vision.MessagesRobocupSslGeometry.SSL_GeometryFieldSize;
import proto.vision.MessagesRobocupSslWrapper.SSL_WrapperPacket;
import visao.EstadoMundo;
import visao.EstadoRobo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;

/**
 * Publica o estado do simulador como {@code SSL_WrapperPacket} oficial.
 *
 * <p>Com isso o simulador vira um substituto do grSim: qualquer cliente ou
 * software de time que hoje escuta a {@code ssl-vision} funciona sem alteracao.
 *
 * <p>O pacote oficial descreve o que uma CAMERA ve: so posicao e orientacao.
 * Velocidade, comando aplicado, posse de bola e anotacoes de skill nao tem campo
 * no protocolo e nao saem daqui. Quem consome tipicamente reconstroi a
 * velocidade com um filtro sobre quadros consecutivos, como faz todo time da liga.
 */
public final class PublicadorVisao implements AutoCloseable {

    /** A geometria muda raramente; reenviada de tempos em tempos para quem chegou depois. */
    private static final long INTERVALO_GEOMETRIA_QUADROS = 60;

    private final MulticastSocket socket;
    private final InetSocketAddress destino;

    private long ultimaGeometria = -1;
    private long pacotesEnviados;

    public PublicadorVisao(String grupo, int porta) throws IOException {
        this.socket = new MulticastSocket();
        this.socket.setTimeToLive(2);
        this.destino = new InetSocketAddress(InetAddress.getByName(grupo), porta);
    }

    public long getPacotesEnviados() { return pacotesEnviados; }

    public void publicar(EstadoMundo q) throws IOException {
        enviar(SSL_WrapperPacket.newBuilder().setDetection(deteccao(q)).build());

        // O teste de "nunca enviou" e separado: com um sentinela negativo, a
        // subtracao de long estouraria e a geometria jamais seria publicada.
        if (ultimaGeometria < 0 || q.frame() - ultimaGeometria >= INTERVALO_GEOMETRIA_QUADROS) {
            ultimaGeometria = q.frame();
            enviar(SSL_WrapperPacket.newBuilder().setGeometry(geometria(q.geometria())).build());
        }
    }

    private void enviar(SSL_WrapperPacket pacote) throws IOException {
        byte[] dados = pacote.toByteArray();
        socket.send(new DatagramPacket(dados, dados.length, destino));
        pacotesEnviados++;
    }

    private static SSL_DetectionFrame deteccao(EstadoMundo q) {
        SSL_DetectionFrame.Builder f = SSL_DetectionFrame.newBuilder()
                .setFrameNumber((int) q.frame())
                .setTCapture(q.tempo())
                .setTSent(q.tempo())
                .setCameraId(0);

        // pixel_x/pixel_y sao required no proto2 mas nao existem num simulador --
        // nao ha camera. O grSim tambem preenche com zero.
        // z e a altura do CENTRO da bola, para ser coerente com x e y, que
        // tambem sao do centro. Uma bola parada reporta o proprio raio.
        f.addBalls(SSL_DetectionBall.newBuilder()
                .setConfidence(1f)
                .setX((float) q.bola().posicao().x())
                .setY((float) q.bola().posicao().y())
                .setZ((float) q.bola().zCentro())
                .setPixelX(0f).setPixelY(0f));

        for (EstadoRobo r : q.robos()) {
            SSL_DetectionRobot.Builder b = SSL_DetectionRobot.newBuilder()
                    .setConfidence(1f)
                    .setRobotId(r.id())
                    .setX((float) r.posicao().x())
                    .setY((float) r.posicao().y())
                    .setOrientation((float) r.theta())
                    .setPixelX(0f).setPixelY(0f)
                    .setHeight(150f);
            if (r.cor() == Cor.AZUL) f.addRobotsBlue(b); else f.addRobotsYellow(b);
        }
        return f.build();
    }

    private static SSL_GeometryData geometria(Geometria g) {
        return SSL_GeometryData.newBuilder()
                .setField(SSL_GeometryFieldSize.newBuilder()
                        .setFieldLength((int) g.comprimento())
                        .setFieldWidth((int) g.largura())
                        .setGoalWidth((int) g.golLargura())
                        .setGoalDepth((int) g.golProfundidade())
                        .setGoalHeight((int) g.golAltura())
                        .setBoundaryWidth((int) g.faixaExterna())
                        .setPenaltyAreaDepth((int) g.areaDefesaProfundidade())
                        .setPenaltyAreaWidth((int) g.areaDefesaLargura())
                        .setCenterCircleRadius((int) g.raioCirculoCentral())
                        .setLineThickness((int) g.espessuraLinha())
                        .setBallRadius((float) Bola.RAIO)
                        .setMaxRobotRadius((float) Robot.RAIO))
                .build();
    }

    @Override
    public void close() { socket.close(); }
}
