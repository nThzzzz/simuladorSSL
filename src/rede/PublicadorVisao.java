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
import java.util.List;
import java.util.ArrayList;
import java.net.NetworkInterface;
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

    /**
     * Copias unicast do mesmo pacote.
     *
     * <p>Multicast e o protocolo da liga e continua saindo -- estes destinos sao
     * ADICIONAIS. Entre duas maquinas, uma no cabo e outra no Wi-Fi, a ponte do
     * roteador frequentemente nao repassa multicast, e em rede de faculdade ele
     * costuma ser bloqueado por politica. Unicast sai como qualquer UDP comum, e
     * nao exige nada do outro lado: um socket multicast preso a uma porta recebe
     * unicast nela do mesmo jeito.
     */
    private final List<InetSocketAddress> unicast = new ArrayList<>();

    /** O que dizer na tela sobre por onde a visao esta saindo. */
    private final String descricao;

    private long ultimaGeometria = -1;
    private long pacotesEnviados;

    public PublicadorVisao(String grupo, int porta) throws IOException {
        this(grupo, porta, "", List.of(), false);
    }

    public PublicadorVisao(String grupo, int porta, String interfaceDeSaida,
                           List<String> destinos) throws IOException {
        this(grupo, porta, interfaceDeSaida, destinos, false);
    }

    /**
     * @param interfaceDeSaida nome da interface por onde o multicast sai; vazio
     *        deixa o SO escolher -- que e o que falha numa maquina com Docker,
     *        VPN ou VirtualBox, onde a rota padrao de multicast quase nunca e a
     *        da LAN. Do lado que RECEBE isto ja era tratado: o cliente entra no
     *        grupo em TODAS as interfaces, e so o lado que envia ficou para tras.
     * @param destinos IPs que recebem a visao tambem por unicast
     */
    public PublicadorVisao(String grupo, int porta, String interfaceDeSaida,
                           List<String> destinos, boolean broadcastLocal) throws IOException {
        this.socket = new MulticastSocket();
        this.socket.setTimeToLive(2);
        this.destino = new InetSocketAddress(InetAddress.getByName(grupo), porta);

        String saida = "automatica";
        if (interfaceDeSaida != null && !interfaceDeSaida.isBlank()) {
            // O combo mostra "en0 (192.168.0.14)"; o nome e o que vem antes.
            String nome = interfaceDeSaida.split(" ")[0];
            NetworkInterface ni = NetworkInterface.getByName(nome);
            if (ni == null) throw new IOException("interface \"" + nome + "\" nao existe");
            if (!ni.isUp()) throw new IOException("interface \"" + nome + "\" esta fora do ar");
            socket.setNetworkInterface(ni);
            saida = nome;
        }

        for (String ip : destinos) {
            InetSocketAddress a = new InetSocketAddress(InetAddress.getByName(ip), porta);
            if (a.isUnresolved()) throw new IOException("nao consegui resolver \"" + ip + "\"");
            unicast.add(a);
        }

        int digitados = unicast.size();
        int redes = 0;
        if (broadcastLocal) {
            socket.setBroadcast(true);
            for (InetAddress b : broadcastsLocais()) {
                unicast.add(new InetSocketAddress(b, porta));
                redes++;
            }
        }

        this.descricao = "multicast por " + saida
                + (digitados == 0 ? "" : " + " + digitados + " unicast")
                + (redes == 0 ? "" : " + broadcast em " + redes + " rede(s)");
    }

    /**
     * O endereco de broadcast de cada rede IPv4 desta maquina.
     *
     * <p>Broadcast DIRIGIDO (192.168.15.255), e nao o limitado (255.255.255.255):
     * o limitado costuma nao sair de interface nenhuma quando ha mais de uma, e e
     * justamente a maquina com varias interfaces que precisa disto.
     *
     * <p>Nao chega a lugar nenhum fora da sub-rede -- roteador nao repassa
     * broadcast, e isso e por design: se repassasse, um pacote de qualquer um
     * chegaria a todo mundo. Para alem da sub-rede so o IP escrito a mao serve.
     */
    private static List<InetAddress> broadcastsLocais() {
        List<InetAddress> saida = new ArrayList<>();
        try {
            for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.net.InterfaceAddress a : ni.getInterfaceAddresses()) {
                    InetAddress b = a.getBroadcast();   // so IPv4 tem; IPv6 devolve null
                    if (b != null) saida.add(b);
                }
            }
        } catch (Exception ignorado) {
            // Sem lista, sobra o multicast -- o comportamento de antes.
        }
        return saida;
    }

    /** Por onde a visao esta saindo, para a tela mostrar. */
    public String descricao() { return descricao; }

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
        for (InetSocketAddress a : unicast) {
            socket.send(new DatagramPacket(dados, dados.length, a));
        }
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
