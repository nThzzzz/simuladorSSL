package teste;

import core.Vec2;
import model.Bola;
import model.Cor;
import model.Geometria;
import model.ParametrosFisica;
import model.Robot;
import proto.sim.SslSimulationControl.SimulatorCommand;
import proto.sim.SslSimulationControl.SimulatorControl;
import proto.sim.SslSimulationControl.TeleportBall;
import proto.sim.SslSimulationRobotControl.MoveLocalVelocity;
import proto.sim.SslSimulationRobotControl.RobotControl;
import proto.sim.SslSimulationRobotControl.RobotMoveCommand;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionFrame;
import proto.vision.MessagesRobocupSslDetection.SSL_DetectionRobot;
import proto.vision.MessagesRobocupSslGeometry.SSL_GeometryFieldSize;
import proto.vision.MessagesRobocupSslWrapper.SSL_WrapperPacket;
import app.Rede;
import rede.ConfigRede;
import rede.PublicadorVisao;
import rede.ReceptorDeComandosRobo;
import rede.ReceptorDeControle;
import sim.ControladorExterno;
import sim.ControleLocal;
import sim.Simulacao;
import sim.VisaoLocal;
import visao.FonteDeVisao;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Verifica o simulador contra o PROTOCOLO, nao contra codigo proprio: fala com
 * ele por sockets UDP crus, montando e lendo protobuf na mao, exatamente como
 * faria um software de time externo.
 *
 * <p>Roda com {@code java -cp out/production/SSL:lib/* teste.AutotesteRede}.
 */
public final class AutotesteRede {

    // Portas fora das oficiais, para nao colidir com um simulador ja rodando.
    private static final String GRUPO = "224.5.23.2";
    private static final int PORTA_VISAO = 11006;
    private static final int PORTA_CONTROLE = 11300;
    private static final int PORTA_ROBOS_AZUL = 11301;

    private static int falhas = 0;
    private static int total = 0;

    public static void main(String[] args) throws Exception {
        Simulacao sim = new Simulacao(Geometria.divisaoB(), ParametrosFisica.padrao(), 1.0 / 60.0);
        sim.inicializarPartida("Azuis", 6, "Amarelos", 6);

        FonteDeVisao fonte = new VisaoLocal(sim);
        ControladorExterno externo = sim.getControladorExterno();

        try (PublicadorVisao pub = new PublicadorVisao(GRUPO, PORTA_VISAO);
             MulticastSocket espia = abrirEspia();
             ReceptorDeControle recCtl =
                     new ReceptorDeControle(PORTA_CONTROLE, new ControleLocal(sim));
             ReceptorDeComandosRobo recRobo =
                     new ReceptorDeComandosRobo(PORTA_ROBOS_AZUL, Cor.AZUL, externo);
             DatagramSocket emissor = new DatagramSocket()) {

            sim.setAposTick(() -> {
                try { pub.publicar(fonte.ultimoQuadro()); }
                catch (IOException e) { throw new RuntimeException(e); }
            });

            // ---------- 1. o simulador publica visao valida? ----------
            // Move um robo por comando direto so para a visao ter o que mostrar.
            Robot alvo = sim.getMundo().getAzul().getRobo(2);
            alvo.setComando(model.RobotCommand.mover(1500, 0, 0));
            for (int i = 0; i < 90; i++) { sim.tick(); Thread.sleep(2); }

            List<SSL_WrapperPacket> recebidos = drenar(espia);
            verdadeiro("o simulador publica pacotes de visao", recebidos.size() > 30);

            SSL_DetectionFrame quadro = ultimaDeteccao(recebidos);
            verdadeiro("chegou um SSL_DetectionFrame", quadro != null);

            if (quadro != null) {
                verdadeiro("quadro traz 6 robos azuis", quadro.getRobotsBlueCount() == 6);
                verdadeiro("quadro traz 6 robos amarelos", quadro.getRobotsYellowCount() == 6);
                verdadeiro("quadro traz a bola", quadro.getBallsCount() == 1);

                SSL_DetectionRobot visto = null;
                for (SSL_DetectionRobot r : quadro.getRobotsBlueList()) {
                    if (r.getRobotId() == 2) visto = r;
                }
                verdadeiro("robo azul 2 presente", visto != null);
                if (visto != null) {
                    aproximado("posicao publicada em mm (x)",
                            visto.getX(), alvo.getPosicao().x(), 60);
                    aproximado("posicao publicada em mm (y)",
                            visto.getY(), alvo.getPosicao().y(), 60);
                    aproximado("orientacao publicada em rad",
                            visto.getOrientation(), alvo.getTheta(), 0.05);
                }
            }

            SSL_GeometryFieldSize geo = ultimaGeometria(recebidos);
            verdadeiro("geometria foi publicada", geo != null);
            if (geo != null) {
                aproximado("geometria: comprimento", geo.getFieldLength(), 9000, 1);
                aproximado("geometria: circulo central", geo.getCenterCircleRadius(), 500, 1);
                aproximado("geometria: raio do robo", geo.getMaxRobotRadius(), 90, 0.1);
                // A altura do gol tem campo no protocolo e antes saia zerada; a
                // espessura da parede nao tem campo nenhum e nao sai daqui.
                aproximado("geometria: altura do gol", geo.getGoalHeight(), 155, 0.1);
            }

            // ---------- 2. SimulatorCommand move o mundo? ----------
            // Protocolo em metros; o mundo em milimetros.
            enviar(emissor, PORTA_CONTROLE, SimulatorCommand.newBuilder()
                    .setControl(SimulatorControl.newBuilder()
                            .setTeleportBall(TeleportBall.newBuilder()
                                    .setX(-2.5f).setY(0.8f).setVx(1.2f)))
                    .build().toByteArray());
            Thread.sleep(300);
            sim.tick();

            verdadeiro("SimulatorCommand chegou", recCtl.getComandosRecebidos() > 0);
            aproximado("teleporte converteu metros para mm (x)",
                    sim.getMundo().getBola().getPosicao().x(), -2500, 60);
            aproximado("teleporte converteu metros para mm (y)",
                    sim.getMundo().getBola().getPosicao().y(), 800, 60);

            // ---------- 3. RobotControl pilota um robo? ----------
            // E o teste que importa: prova que um software de time externo
            // consegue dirigir este simulador como dirigiria o grSim.
            // Tira o robo da cruz antes de acelerar: na formacao inicial ele tem
            // um companheiro 810 mm a frente e bateria antes de atingir a velocidade.
            Robot pilotado = sim.getMundo().getAzul().getRobo(5);
            pilotado.setPosicao(new Vec2(-4000, 2500));
            pilotado.setTheta(0);
            pilotado.setVelocidade(Vec2.ZERO);
            Vec2 partida = pilotado.getPosicao();

            enviar(emissor, PORTA_ROBOS_AZUL, RobotControl.newBuilder()
                    .addRobotCommands(proto.sim.SslSimulationRobotControl.RobotCommand.newBuilder()
                            .setId(5)
                            .setMoveCommand(RobotMoveCommand.newBuilder()
                                    .setLocalVelocity(MoveLocalVelocity.newBuilder()
                                            .setForward(2.0f).setLeft(0f).setAngular(0f))))
                    .build().toByteArray());
            Thread.sleep(300);

            verdadeiro("RobotControl chegou", recRobo.getPacotesRecebidos() > 0);
            for (int i = 0; i < 60; i++) sim.tick();   // 1 s

            // Nao sao 2 m: o robo acelera a 3 m/s^2, entao leva 0,667 s para
            // chegar aos 2 m/s (666 mm) e so o resto do segundo e em cruzeiro
            // (666 mm). O total previsto e 1333 mm.
            double andou = pilotado.getPosicao().distancia(partida);
            aproximado("robo pilotado de fora andou o previsto pela rampa + cruzeiro",
                    andou, 1333, 120);
            aproximado("velocidade do robo bate com o comando (m/s -> mm/s)",
                    pilotado.getRapidez(), 2000, 100);

            sim.setAposTick(null);
        }

        chipPelaRede();
        reconfiguracao();

        unicastEntregaSemMulticast();
        interfaceInexistenteFalhaCedo();

        System.out.printf("%n%d/%d verificacoes passaram%n", total - falhas, total);
        if (falhas > 0) System.exit(1);
    }

    /**
     * Um chip comandado de fora: {@code kick_angle} entra, altura sai na visao.
     *
     * <p>Prova o par completo. O protocolo manda elevacao em graus, o simulador
     * poe a bola no ar, e o {@code SSL_DetectionBall.z} devolve a altura para
     * quem estiver assistindo.
     */
    private static void chipPelaRede() throws Exception {
        System.out.println("\n  -- chip pela rede --");

        Simulacao sim = new Simulacao(Geometria.divisaoB(), ParametrosFisica.padrao(), 1.0 / 60.0);
        sim.inicializarPartida("A", 6, "B", 0);
        FonteDeVisao fonte = new VisaoLocal(sim);
        ControladorExterno externo = sim.getControladorExterno();

        Robot chutador = sim.getMundo().getAzul().getRobo(0);
        chutador.setPosicao(new Vec2(-3000, 2000));
        chutador.setTheta(0);
        // Bola encostada na boca do dribbler, para o chute engatar.
        sim.getMundo().getBola().reposicionar(
                chutador.pontoDribbler().mais(new Vec2(Bola.RAIO, 0)));

        try (PublicadorVisao pub = new PublicadorVisao(GRUPO, 11206);
             MulticastSocket espia = espiar(11206);
             ReceptorDeComandosRobo _ =
                     new ReceptorDeComandosRobo(11207, Cor.AZUL, externo); // so escuta
             DatagramSocket emissor = new DatagramSocket()) {

            sim.setAposTick(() -> {
                try { pub.publicar(fonte.ultimoQuadro()); }
                catch (IOException e) { throw new RuntimeException(e); }
            });

            enviar(emissor, 11207, RobotControl.newBuilder()
                    .addRobotCommands(proto.sim.SslSimulationRobotControl.RobotCommand.newBuilder()
                            .setId(0)
                            .setKickSpeed(5.0f)      // m/s
                            .setKickAngle(45f))      // graus
                    .build().toByteArray());
            Thread.sleep(300);

            double alturaMaxima = 0;
            for (int i = 0; i < 40; i++) {
                sim.tick();
                alturaMaxima = Math.max(alturaMaxima, sim.getMundo().getBola().getZ());
                Thread.sleep(2);
            }

            verdadeiro("kick_angle do protocolo poe a bola no ar", alturaMaxima > 100);

            // 5 m/s a 45 graus: vz0 = 3536 mm/s, apice = vz0^2/2g = 637 mm.
            double vz0 = 5000 * Math.sin(Math.toRadians(45));
            double apiceEsperado = vz0 * vz0 / (2 * ParametrosFisica.padrao().gravidade());
            aproximado("altura atingida bate com o angulo comandado",
                    alturaMaxima, apiceEsperado, apiceEsperado * 0.05);

            double zMaximoNaVisao = 0;
            for (SSL_WrapperPacket w : drenar(espia)) {
                if (w.hasDetection() && w.getDetection().getBallsCount() > 0) {
                    zMaximoNaVisao = Math.max(zMaximoNaVisao, w.getDetection().getBalls(0).getZ());
                }
            }
            // A visao reporta a altura do CENTRO, entao vem um raio acima da base.
            aproximado("SSL_DetectionBall.z transmite a altura",
                    zMaximoNaVisao, alturaMaxima + Bola.RAIO, 60);

            sim.setAposTick(null);
        }
    }

    /**
     * Trocar portas com o simulador no ar, e o que acontece quando a troca falha.
     *
     * <p>O caso critico e o ultimo: se a porta nova estiver ocupada, o simulador
     * nao pode ficar mudo -- a configuracao anterior tem de voltar sozinha.
     */
    private static void reconfiguracao() throws Exception {
        System.out.println("\n  -- reconfiguracao --");

        Simulacao sim = new Simulacao(Geometria.divisaoB(), ParametrosFisica.padrao(), 1.0 / 60.0);
        sim.inicializarPartida("A", 6, "B", 6);
        FonteDeVisao fonte = new VisaoLocal(sim);

        ConfigRede antes = new ConfigRede(GRUPO, 11106, 11400, 11401, 11402, "", "");
        ConfigRede depois = new ConfigRede(GRUPO, 11107, 11403, 11404, 11405, "", "");

        try (Rede rede = new Rede(sim, fonte, antes);
             MulticastSocket espiaAntes = espiar(11106);
             MulticastSocket espiaDepois = espiar(11107)) {

            for (int i = 0; i < 20; i++) { sim.tick(); Thread.sleep(2); }
            verdadeiro("publica na porta inicial", !drenar(espiaAntes).isEmpty());
            drenar(espiaDepois);

            rede.reconfigurar(depois);
            for (int i = 0; i < 20; i++) { sim.tick(); Thread.sleep(2); }

            verdadeiro("apos reconfigurar, publica na porta nova",
                    !drenar(espiaDepois).isEmpty());
            verdadeiro("apos reconfigurar, para de publicar na antiga",
                    drenar(espiaAntes).isEmpty());
            verdadeiro("a config em vigor e a nova", rede.getConfig().equals(depois));

            // Porta ocupada por outro processo: a troca tem de falhar e voltar atras.
            try (DatagramSocket _ = new DatagramSocket(11500)) { // ocupa a porta
                boolean recusou = false;
                try {
                    rede.reconfigurar(depois.comPortaControle(11500));
                } catch (IOException esperado) {
                    recusou = true;
                }
                verdadeiro("porta ocupada e recusada", recusou);
                verdadeiro("apos a recusa, a config anterior continua valendo",
                        rede.getConfig().equals(depois));

                for (int i = 0; i < 20; i++) { sim.tick(); Thread.sleep(2); }
                verdadeiro("apos a recusa, o simulador continua publicando",
                        !drenar(espiaDepois).isEmpty());
            }

            // Config invalida nem chega a tocar em socket.
            boolean recusouInvalida = false;
            try {
                rede.reconfigurar(depois.comGrupo("10.0.0.1")); // nao e multicast
            } catch (IOException esperado) {
                recusouInvalida = true;
            }
            verdadeiro("endereco fora da faixa multicast e recusado", recusouInvalida);
            verdadeiro("config invalida nao derruba a que estava valendo",
                    rede.getConfig().equals(depois));
        }
    }

    // ------------------------------------------------------------------- apoio

    /**
     * Entra no grupo por todas as interfaces com multicast, loopback incluida.
     *
     * <p>Escolher "a interface certa" e o classico ponto de falha do multicast:
     * com emissor e receptor na mesma maquina o trafego passa pela loopback, que
     * quase nenhuma heuristica escolhe. Entrar em todas evita o sintoma pior --
     * nao chegar nada e nao haver erro.
     */
    /**
     * O envio unicast entrega sem multicast nenhum no caminho.
     *
     * <p>E o conserto do caso "um no cabo, outro no Wi-Fi": a ponte do roteador
     * frequentemente nao repassa multicast entre os dois meios, e em rede de
     * faculdade ele costuma ser bloqueado por politica. O receptor aqui NAO entra
     * no grupo de proposito -- se o pacote chegar, so pode ter vindo por unicast.
     *
     * <p>Confirma tambem que o outro lado nao precisa de mudanca nenhuma: o
     * socket e um {@link MulticastSocket} preso a porta, exatamente como o
     * {@code ReceptorDeVisao}, e ele recebe unicast do mesmo jeito.
     */
    private static void unicastEntregaSemMulticast() throws Exception {
        final int porta = 11506;
        Simulacao sim = new Simulacao(Geometria.divisaoB(), ParametrosFisica.padrao(), 1.0 / 60.0);
        sim.inicializarPartida("Azuis", 6, "Amarelos", 6);
        FonteDeVisao fonte = new VisaoLocal(sim);

        // Grupo que ninguem escuta: se chegar algo, veio pelo unicast.
        try (PublicadorVisao pub = new PublicadorVisao("224.5.23.77", porta,
                     "", List.of("127.0.0.1"));
             MulticastSocket rx = new MulticastSocket(porta)) {

            rx.setSoTimeout(1500);
            for (int i = 0; i < 5; i++) { sim.tick(); pub.publicar(fonte.ultimoQuadro()); }

            SSL_WrapperPacket recebido = null;
            try {
                DatagramPacket p = new DatagramPacket(new byte[65535], 65535);
                rx.receive(p);
                recebido = SSL_WrapperPacket.parseFrom(
                        java.util.Arrays.copyOf(p.getData(), p.getLength()));
            } catch (java.net.SocketTimeoutException ignorado) {
                // fica nulo, e a verificacao abaixo falha com a mensagem certa
            }

            verdadeiro("unicast: a visao chega sem o receptor entrar no grupo",
                    recebido != null && recebido.hasDetection());
            verdadeiro("unicast: e o quadro vem completo",
                    recebido != null && recebido.getDetection().getRobotsBlueCount() == 6);
        }
    }

    /** Interface que nao existe tem de falhar na hora, e nao publicar no vazio. */
    private static void interfaceInexistenteFalhaCedo() {
        boolean reclamou = false;
        try (PublicadorVisao ignorado = new PublicadorVisao(GRUPO, 11507,
                "nao-existe-mesmo", List.of())) {
            // nao deveria chegar aqui
        } catch (Exception e) {
            reclamou = e.getMessage() != null && e.getMessage().contains("nao existe");
        }
        verdadeiro("rede: interface inexistente falha na hora, com nome no erro", reclamou);
    }

    private static MulticastSocket abrirEspia() throws IOException {
        return espiar(PORTA_VISAO);
    }

    private static MulticastSocket espiar(int porta) throws IOException {
        MulticastSocket s = new MulticastSocket(porta);
        s.setReuseAddress(true);
        s.setSoTimeout(50);
        InetSocketAddress grupo = new InetSocketAddress(InetAddress.getByName(GRUPO), porta);
        boolean algum = false;
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || !ni.supportsMulticast()) continue;
            try { s.joinGroup(grupo, ni); algum = true; } catch (IOException ignorado) { }
        }
        if (!algum) throw new IOException("nenhuma interface aceitou o grupo " + GRUPO);
        return s;
    }

    private static List<SSL_WrapperPacket> drenar(MulticastSocket s) {
        List<SSL_WrapperPacket> saida = new ArrayList<>();
        byte[] buffer = new byte[8192];
        while (true) {
            try {
                DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                s.receive(p);
                saida.add(SSL_WrapperPacket.parseFrom(
                        java.util.Arrays.copyOf(p.getData(), p.getLength())));
            } catch (IOException fim) {
                return saida; // timeout: acabou a fila
            }
        }
    }

    private static SSL_DetectionFrame ultimaDeteccao(List<SSL_WrapperPacket> ps) {
        SSL_DetectionFrame ultima = null;
        for (SSL_WrapperPacket p : ps) if (p.hasDetection()) ultima = p.getDetection();
        return ultima;
    }

    private static SSL_GeometryFieldSize ultimaGeometria(List<SSL_WrapperPacket> ps) {
        SSL_GeometryFieldSize ultima = null;
        for (SSL_WrapperPacket p : ps) if (p.hasGeometry()) ultima = p.getGeometry().getField();
        return ultima;
    }

    private static void enviar(DatagramSocket s, int porta, byte[] dados) throws IOException {
        s.send(new DatagramPacket(dados, dados.length,
                InetAddress.getByName("127.0.0.1"), porta));
    }

    private static void verdadeiro(String nome, boolean condicao) {
        total++;
        if (condicao) System.out.println("  ok    " + nome);
        else { falhas++; System.out.println("  FALHA " + nome); }
    }

    private static void aproximado(String nome, double obtido, double esperado, double tol) {
        total++;
        if (Math.abs(obtido - esperado) <= tol) System.out.println("  ok    " + nome);
        else {
            falhas++;
            System.out.printf("  FALHA %s  (obtido %.3f, esperado %.3f +- %.3f)%n",
                    nome, obtido, esperado, tol);
        }
    }
}
