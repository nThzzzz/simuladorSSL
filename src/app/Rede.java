package app;

import model.Cor;
import rede.ConfigRede;
import rede.PublicadorVisao;
import rede.ReceptorDeComandosRobo;
import rede.ReceptorDeControle;
import sim.ControladorExterno;
import sim.ControleLocal;
import sim.Simulacao;
import visao.CanalDeControle;
import visao.EstadoMundo;
import visao.FonteDeVisao;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Presenca do simulador na rede, no mesmo papel que o grSim ocupa.
 *
 * <p>Uma saida e tres entradas:
 *
 * <ul>
 *   <li>publica {@code SSL_WrapperPacket} no multicast da visao
 *   <li>escuta {@code SimulatorCommand} para manipular o mundo
 *   <li>escuta {@code RobotControl} de cada equipe -- e por onde um software de
 *       time pilota os robos
 * </ul>
 *
 * <p>O simulador nao recebe visao de ninguem: ele e a fonte. O que se ve na
 * janela vem direto do mundo simulado, nao de um pacote que deu a volta pela rede.
 *
 * <p>As portas podem ser trocadas com o simulador rodando via
 * {@link #reconfigurar}. O {@link ControladorExterno} e o gancho de publicacao
 * sobrevivem a troca -- so os sockets sao refeitos.
 */
public final class Rede implements AutoCloseable {

    private final FonteDeVisao fonte;
    private final CanalDeControle destinoDeControle;
    private final ControladorExterno externo;

    private ConfigRede config;
    private volatile PublicadorVisao visao;
    private ReceptorDeControle controle;
    private ReceptorDeComandosRobo azul;
    private ReceptorDeComandosRobo amarelo;

    public Rede(Simulacao sim, FonteDeVisao fonte, ConfigRede config) throws IOException {
        this.fonte = fonte;
        this.destinoDeControle = new ControleLocal(sim);
        this.externo = sim.getControladorExterno();

        abrir(config);

        // Publicar por tick, nao por repaint: a visao sai uma vez por quadro
        // simulado, independente da taxa de desenho.
        sim.setAposTick(this::publicarQuadroAtual);
    }

    public ConfigRede getConfig() { return config; }

    /**
     * Troca enderecos e portas com o simulador rodando.
     *
     * <p>Fecha os sockets antigos antes de abrir os novos: nao da para manter os
     * dois conjuntos no ar ao mesmo tempo, porque uma porta que continua igual
     * falharia no bind com a anterior ainda aberta. Se a abertura nova falhar --
     * porta ocupada por outro processo, tipicamente -- a configuracao anterior e
     * restaurada, para nao deixar o simulador mudo.
     *
     * @throws IOException se a nova configuracao falhou; a mensagem diz se a
     *                     anterior foi recuperada
     */
    public synchronized void reconfigurar(ConfigRede nova) throws IOException {
        String problema = nova.problema();
        if (problema != null) throw new IOException(problema);
        if (nova.equals(config)) return;

        ConfigRede anterior = config;
        fechar();
        try {
            abrir(nova);
        } catch (IOException falha) {
            try {
                abrir(anterior);
                throw new IOException(falha.getMessage()
                        + " -- a configuracao anterior foi mantida", falha);
            } catch (IOException perdida) {
                throw new IOException(falha.getMessage()
                        + " -- e a configuracao anterior tambem nao voltou; rede fora do ar",
                        falha);
            }
        }
    }

    private void abrir(ConfigRede c) throws IOException {
        PublicadorVisao v = null;
        ReceptorDeControle ct = null;
        ReceptorDeComandosRobo az = null;
        ReceptorDeComandosRobo am = null;
        try {
            v = new PublicadorVisao(c.grupoVisao(), c.portaVisao(),
                    c.interfaceDeSaida(), c.destinos(), c.broadcastLocal());
            ct = new ReceptorDeControle(c.portaControle(), destinoDeControle);
            az = new ReceptorDeComandosRobo(c.portaAzul(), Cor.AZUL, externo);
            am = new ReceptorDeComandosRobo(c.portaAmarelo(), Cor.AMARELO, externo);
        } catch (IOException e) {
            // Nao deixa socket meio aberto se um dos quatro falhou.
            fecharSeAberto(v, ct, az, am);
            throw e;
        }
        this.visao = v;
        this.controle = ct;
        this.azul = az;
        this.amarelo = am;
        this.config = c;
    }

    private void fechar() {
        fecharSeAberto(visao, controle, azul, amarelo);
        visao = null;
        controle = null;
        azul = null;
        amarelo = null;
    }

    private static void fecharSeAberto(AutoCloseable... recursos) {
        for (AutoCloseable r : recursos) {
            if (r == null) continue;
            try { r.close(); } catch (Exception ignorado) { }
        }
    }

    private void publicarQuadroAtual() {
        publicar(fonte.ultimoQuadro());
    }

    public void publicar(EstadoMundo q) {
        PublicadorVisao v = visao;
        if (v == null) return; // no meio de uma reconfiguracao
        try {
            v.publicar(q);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao publicar visao", e);
        }
    }

    public void anunciar() {
        System.out.println("rede:");
        System.out.printf("  visao      -> %s:%d  (SSL_WrapperPacket, multicast por %s)%n",
                config.grupoVisao(), config.portaVisao(),
                config.interfaceDeSaida().isBlank() ? "interface automatica" : config.interfaceDeSaida());
        for (String ip : config.destinos()) {
            System.out.printf("  visao      -> %s:%d  (a mesma coisa, por unicast)%n",
                    ip, config.portaVisao());
        }
        if (config.broadcastLocal()) {
            System.out.printf("  visao      -> broadcast das redes locais:%d%n", config.portaVisao());
        }
        System.out.printf("  controle   <- porta %d  (SimulatorCommand)%n", config.portaControle());
        System.out.printf("  robos azul <- porta %d  (RobotControl)%n", config.portaAzul());
        System.out.printf("  robos amar <- porta %d  (RobotControl)%n", config.portaAmarelo());
    }

    /** Linha curta de estado para a interface. */
    public synchronized String status() {
        if (visao == null) return "<html><i>reconfigurando...</i></html>";
        // O unicast so aparece quando existe. Sem isto nao havia como saber, da
        // tela, se ele tinha pegado -- e "configurei e nao mudou nada" e
        // exatamente o que se ve quando o destino foi digitado no lugar errado.
        StringBuilder extra = new StringBuilder();
        if (!config.interfaceDeSaida().isBlank()) {
            extra.append("<br>saindo por ").append(config.interfaceDeSaida());
        }
        for (String ip : config.destinos()) {
            extra.append("<br>+ unicast para ").append(ip);
        }
        if (config.broadcastLocal()) extra.append("<br>+ broadcast na rede local");

        return String.format("<html>%s:%d%s<br>%,d pacotes enviados<br>"
                        + "%,d comandos - %,d de robo</html>",
                config.grupoVisao(), config.portaVisao(), extra, visao.getPacotesEnviados(),
                controle.getComandosRecebidos(),
                azul.getPacotesRecebidos() + amarelo.getPacotesRecebidos());
    }

    @Override
    public synchronized void close() { fechar(); }
}
