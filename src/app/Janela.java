package app;

import core.Vec2;
import log.ConfigLog;
import model.Cor;
import model.ParametrosFisica;
import model.Robot;
import sim.ConsoleLocal;
import view.Campo;
import visao.CanalDeControle;
import visao.EstadoRobo;
import visao.FonteDeVisao;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Monta a janela do simulador.
 *
 * <p>Vive num pacote de aplicacao, e nao em {@code view}, porque precisa
 * conhecer skills, log e console local -- coisas que o renderizador puro nao
 * deve conhecer. O {@link Campo} continua dependendo so de {@code core},
 * {@code model} e {@code visao}.
 *
 * <p>Uma janela so, como no grSim: quem simula e quem mostra sao o mesmo
 * processo, e a rede e uma saida a mais, nao a fonte do que se ve.
 */
public final class Janela {

    private static final Color FUNDO_PAINEL = new Color(32, 32, 36);
    private static final Color TEXTO = new Color(225, 225, 230);
    private static final Color APAGADO = new Color(150, 150, 160);

    private enum Ferramenta {
        NENHUMA("Arrastar / chutar"),
        POSICIONAR_BOLA("Posicionar bola"),
        SELECIONAR_ROBO("Inspecionar robo");

        final String rotulo;

        Ferramenta(String rotulo) { this.rotulo = rotulo; }
    }

    private final FonteDeVisao fonte;
    private final CanalDeControle controle;
    private final ConsoleLocal console;
    private final Campo campo;
    private final Rede rede;

    private Ferramenta ferramenta = Ferramenta.NENHUMA;
    private JLabel statusFerramenta;

    private Janela(FonteDeVisao fonte, CanalDeControle controle, ConsoleLocal console,
                   Rede rede) {
        this.fonte = fonte;
        this.controle = controle;
        this.console = console;
        this.rede = rede;
        this.campo = new Campo(fonte);
    }

    /**
     * Abre a janela.
     *
     * @param aCadaQuadro roda antes de cada repaint: avanca a fisica e publica
     * @param rede        presenca na rede, ou {@code null} se rodando offline
     */
    public static JFrame abrir(String titulo, FonteDeVisao fonte, CanalDeControle controle,
                               ConsoleLocal console, Runnable aCadaQuadro, Rede rede) {
        Janela j = new Janela(fonte, controle, console, rede);
        return j.montar(titulo, aCadaQuadro);
    }

    private JFrame montar(String titulo, Runnable aCadaQuadro) {
        instalarMouse();

        JFrame frame = new JFrame(titulo);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(campo, BorderLayout.CENTER);
        frame.add(painelInferior(), BorderLayout.SOUTH);

        JScrollPane lateral = new JScrollPane(painelLateral(),
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        lateral.setPreferredSize(new Dimension(262, 0));
        lateral.setBorder(null);
        lateral.getVerticalScrollBar().setUnitIncrement(16);
        frame.add(lateral, BorderLayout.EAST);

        new Timer(16, e -> {
            if (aCadaQuadro != null) aCadaQuadro.run();
            campo.repaint();
        }).start();

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;
    }

    // ------------------------------------------------------------------- mouse

    private void instalarMouse() {
        MouseAdapter mouse = new MouseAdapter() {
            private int ultimoX, ultimoY;

            @Override
            public void mousePressed(MouseEvent e) {
                ultimoX = e.getX();
                ultimoY = e.getY();
                if (SwingUtilities.isRightMouseButton(e)) return;

                Vec2 p = campo.telaParaMundo(e.getX(), e.getY());
                switch (ferramenta) {
                    case POSICIONAR_BOLA -> {
                        controle.reposicionarBola(p, Vec2.ZERO);
                        selecionar(Ferramenta.NENHUMA);
                    }
                    case SELECIONAR_ROBO -> campo.selecionar(roboSob(p));
                    case NENHUMA -> {
                        if (p.distancia(campo.quadro().bola().posicao()) < 250) {
                            campo.setMira(true, p);
                        }
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                campo.setMouseTela(e.getX(), e.getY());
                if (SwingUtilities.isRightMouseButton(e)) {
                    campo.deslocar(e.getX() - ultimoX, e.getY() - ultimoY);
                    ultimoX = e.getX();
                    ultimoY = e.getY();
                    return;
                }
                if (campo.isMostrarMira()) {
                    campo.setMira(true, campo.telaParaMundo(e.getX(), e.getY()));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!campo.isMostrarMira()) return;
                campo.setMira(false, null);
                Vec2 bola = campo.quadro().bola().posicao();
                controle.reposicionarBola(bola, campo.velocidadeDeMira(bola));
            }

            @Override
            public void mouseMoved(MouseEvent e) { campo.setMouseTela(e.getX(), e.getY()); }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                campo.aplicarZoom(Math.pow(1.1, -e.getPreciseWheelRotation()));
            }
        };
        campo.addMouseListener(mouse);
        campo.addMouseMotionListener(mouse);
        campo.addMouseWheelListener(mouse);
    }

    private EstadoRobo roboSob(Vec2 p) {
        EstadoRobo r = campo.quadro().maisProximo(p, null);
        return (r != null && r.posicao().distancia(p) <= Robot.RAIO * 1.5) ? r : null;
    }

    // ----------------------------------------------------------------- paineis

    private JPanel painelInferior() {
        JPanel painel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        painel.setBackground(FUNDO_PAINEL);

        JTextField nomeAzul = new JTextField(campo.quadro().nomeAzul(), 9);
        JTextField qtdAzul = new JTextField(
                String.valueOf(campo.quadro().quantidade(Cor.AZUL)), 2);
        JTextField nomeAmarelo = new JTextField(campo.quadro().nomeAmarelo(), 9);
        JTextField qtdAmarelo = new JTextField(
                String.valueOf(campo.quadro().quantidade(Cor.AMARELO)), 2);

        JButton aplicar = new JButton("Reiniciar partida");
        aplicar.addActionListener(e -> {
            try {
                controle.reiniciarPartida(
                        nomeAzul.getText(), Integer.parseInt(qtdAzul.getText().trim()),
                        nomeAmarelo.getText(), Integer.parseInt(qtdAmarelo.getText().trim()));
                campo.limparSelecao();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(painel,
                        "A quantidade de robos deve ser um numero inteiro.",
                        "Valor invalido", JOptionPane.ERROR_MESSAGE);
            }
        });

        painel.add(rotulo("Azul", new Color(90, 150, 255)));
        painel.add(nomeAzul);
        painel.add(rotulo("qtd", TEXTO));
        painel.add(qtdAzul);
        painel.add(Box.createHorizontalStrut(16));
        painel.add(rotulo("Amarelo", new Color(255, 210, 60)));
        painel.add(nomeAmarelo);
        painel.add(rotulo("qtd", TEXTO));
        painel.add(qtdAmarelo);
        painel.add(Box.createHorizontalStrut(16));
        painel.add(aplicar);
        return painel;
    }

    private JPanel painelLateral() {
        JPanel painel = coluna();
        painel.setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));

        painel.add(titulo("Ferramentas"));
        for (Ferramenta f : Ferramenta.values()) {
            JButton b = new JButton(f.rotulo);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            b.addActionListener(e -> selecionar(f));
            painel.add(b);
            painel.add(Box.createVerticalStrut(4));
        }

        statusFerramenta = rotulo("ativa: " + ferramenta.rotulo, new Color(150, 220, 150));
        painel.add(Box.createVerticalStrut(4));
        painel.add(statusFerramenta);

        painel.add(Box.createVerticalStrut(20));
        painel.add(painelLog());
        painel.add(Box.createVerticalStrut(20));
        painel.add(painelFisica());
        painel.add(Box.createVerticalStrut(20));
        painel.add(painelRede());

        painel.add(Box.createVerticalStrut(20));
        painel.add(rotulo("<html>botao direito arrasta<br>scroll aplica zoom</html>", APAGADO));
        painel.add(Box.createVerticalGlue());
        return painel;
    }

    private JPanel painelLog() {
        JPanel painel = coluna();
        painel.add(titulo("Log"));

        JCheckBox comTracking = caixa("posicoes (tracking)", true);
        JCheckBox comEventos = caixa("acoes (eventos)", true);

        JPanel linha = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        linha.setBackground(FUNDO_PAINEL);
        linha.setAlignmentX(Component.LEFT_ALIGNMENT);
        linha.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JSpinner intervalo = new JSpinner(new SpinnerNumberModel(1, 1, 60, 1));
        intervalo.setPreferredSize(new Dimension(52, 22));
        linha.add(rotulo("1 a cada", TEXTO));
        linha.add(intervalo);
        linha.add(rotulo("quadros", TEXTO));

        painel.add(comTracking);
        painel.add(comEventos);
        painel.add(linha);
        painel.add(Box.createVerticalStrut(6));

        JLabel volume = rotulo(" ", APAGADO);
        JButton gravar = new JButton("●  Iniciar gravacao");
        gravar.setAlignmentX(Component.LEFT_ALIGNMENT);
        gravar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        gravar.setFont(gravar.getFont().deriveFont(Font.BOLD, 13f));
        gravar.setForeground(new Color(190, 60, 60));
        gravar.addActionListener(e -> {
            if (console.estaGravando()) {
                System.out.printf("gravados %d quadros e %d eventos%n",
                        console.getQuadrosGravados(), console.getEventosGravados());
                console.pararGravacao();
                gravar.setText("●  Iniciar gravacao");
                gravar.setForeground(new Color(190, 60, 60));
                travar(true, comTracking, comEventos, intervalo);
            } else {
                ConfigLog config = new ConfigLog(comTracking.isSelected(),
                        comEventos.isSelected(), (Integer) intervalo.getValue());
                if (!config.gravaAlgo()) {
                    JOptionPane.showMessageDialog(painel,
                            "Marque ao menos um dos dois streams para gravar.",
                            "Nada a gravar", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                Path destino = Path.of("logs", carimbo());
                console.iniciarGravacao(destino, config);
                gravar.setText("■  Parar gravacao");
                gravar.setForeground(new Color(215, 70, 70));
                // A config vale para a corrida inteira: mudar no meio invalidaria
                // o meta.json ja escrito.
                travar(false, comTracking, comEventos, intervalo);
                System.out.println("gravando em " + destino.toAbsolutePath());
            }
        });
        painel.add(gravar);
        painel.add(Box.createVerticalStrut(4));
        painel.add(volume);

        new Timer(250, e -> volume.setText(console.estaGravando()
                ? String.format("%,d quadros - %,d eventos",
                        console.getQuadrosGravados(), console.getEventosGravados())
                : " ")).start();
        return painel;
    }

    private JPanel painelFisica() {
        JPanel painel = coluna();
        painel.add(titulo("Fisica"));

        JSlider ganhoChute = new JSlider(0, 1000);
        painel.add(rotulo("<html><i>ajuste ao vivo; a troca<br>entra no log</i></html>", APAGADO));
        painel.add(Box.createVerticalStrut(6));

        ParametrosFisica base = ParametrosFisica.padrao();
        JSlider restParede   = new JSlider(0, 1000);
        JSlider restRobo     = new JSlider(0, 1000);
        JSlider restRoboRobo = new JSlider(0, 1000);
        JSlider atritoRol    = new JSlider(0, 1000);

        Runnable aplicar = () -> controle.ajustarFisica(new ParametrosFisica(
                base.gravidade(), base.atritoDeslizamento(),
                valor(atritoRol, 0, 0.20), valor(restParede, 0, 1),
                valor(restRobo, 0, 1), valor(restRoboRobo, 0, 1),
                base.atritoTangencialRobo(), base.velocidadeMinimaBola(),
                base.alcanceDribbler(), base.forcaDribbler()));

        painel.add(linhaSlider(restParede, "quique na parede", 0, 1,
                base.restituicaoParede(), "%.2f", aplicar));
        painel.add(linhaSlider(restRobo, "quique no robo", 0, 1,
                base.restituicaoRobo(), "%.2f", aplicar));
        painel.add(linhaSlider(restRoboRobo, "quique robo-robo", 0, 1,
                base.restituicaoRoboRobo(), "%.2f", aplicar));
        painel.add(linhaSlider(atritoRol, "atrito de rolamento", 0, 0.20,
                base.atritoRolamento(), "%.3f", aplicar));
        painel.add(linhaSlider(ganhoChute, "forca do chute (mouse)", 0.25, 3.0,
                Campo.GANHO_CHUTE_PADRAO, "%.2f",
                () -> campo.setGanhoChute(valor(ganhoChute, 0.25, 3.0))));

        JButton restaurar = new JButton("Restaurar padrao");
        restaurar.setAlignmentX(Component.LEFT_ALIGNMENT);
        restaurar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        restaurar.addActionListener(e -> {
            posicionar(restParede, base.restituicaoParede(), 0, 1);
            posicionar(restRobo, base.restituicaoRobo(), 0, 1);
            posicionar(restRoboRobo, base.restituicaoRoboRobo(), 0, 1);
            posicionar(atritoRol, base.atritoRolamento(), 0, 0.20);
            posicionar(ganhoChute, Campo.GANHO_CHUTE_PADRAO, 0.25, 3.0);
            campo.setGanhoChute(Campo.GANHO_CHUTE_PADRAO);
            aplicar.run();
        });
        painel.add(Box.createVerticalStrut(4));
        painel.add(restaurar);
        return painel;
    }

    private JPanel painelRede() {
        JPanel painel = coluna();
        painel.add(titulo("Rede"));

        if (rede == null) {
            painel.add(rotulo("<html><i>desligada (--sem-rede)</i></html>", APAGADO));
            return painel;
        }

        JLabel linha = rotulo(" ", APAGADO);
        painel.add(linha);
        painel.add(Box.createVerticalStrut(6));

        JButton configurar = new JButton("Configurar...");
        configurar.setAlignmentX(Component.LEFT_ALIGNMENT);
        configurar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        configurar.addActionListener(e -> DialogoRede.abrir(campo, rede));
        painel.add(configurar);

        new Timer(500, e -> linha.setText(rede.status())).start();
        return painel;
    }

    // ------------------------------------------------------------------- apoio

    static String carimbo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    private void selecionar(Ferramenta f) {
        ferramenta = f;
        if (statusFerramenta != null) statusFerramenta.setText("ativa: " + f.rotulo);
    }

    private static JPanel coluna() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(FUNDO_PAINEL);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JCheckBox caixa(String texto, boolean marcada) {
        JCheckBox c = new JCheckBox(texto, marcada);
        c.setBackground(FUNDO_PAINEL);
        c.setForeground(TEXTO);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static void travar(boolean habilitado, JComponent... componentes) {
        for (JComponent c : componentes) c.setEnabled(habilitado);
    }

    private static JLabel rotulo(String texto, Color cor) {
        JLabel l = new JLabel(texto);
        l.setForeground(cor);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JComponent titulo(String texto) {
        JLabel l = rotulo(texto, TEXTO);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        return l;
    }

    private static double valor(JSlider s, double min, double max) {
        return min + (max - min) * s.getValue() / 1000.0;
    }

    private static void posicionar(JSlider s, double valor, double min, double max) {
        s.setValue((int) Math.round((valor - min) / (max - min) * 1000));
    }

    /**
     * Linha com nome, valor corrente e slider. O callback so dispara quando o
     * arrasto termina -- aplicar a cada pixel encheria o log de eventos
     * PARAMETROS_ALTERADOS.
     */
    private static JPanel linhaSlider(JSlider slider, String nome, double min, double max,
                                      double inicial, String formato, Runnable aoSoltar) {
        JPanel linha = coluna();
        linha.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JLabel texto = rotulo(String.format(nome + "   " + formato, inicial), TEXTO);
        texto.setFont(texto.getFont().deriveFont(11f));

        posicionar(slider, inicial, min, max);
        slider.setBackground(FUNDO_PAINEL);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.addChangeListener(e -> {
            texto.setText(String.format(nome + "   " + formato, valor(slider, min, max)));
            if (!slider.getValueIsAdjusting()) aoSoltar.run();
        });

        linha.add(texto);
        linha.add(slider);
        return linha;
    }
}
