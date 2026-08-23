package app;

import core.Vec2;
import demo.Cenarios;
import demo.ExecutorDeCenario;
import demo.Roteiro;
import log.ConfigLog;
import model.Cor;
import model.ParametrosFisica;
import model.Robot;
import model.RobotCommand;
import sim.ConsoleLocal;
import view.Campo;
import view.Estilo;
import visao.CanalDeControle;
import visao.EstadoRobo;
import visao.FonteDeVisao;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.nio.file.Path;
import java.util.List;
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

    private static final Color FUNDO_PAINEL = Estilo.PAINEL;
    private static final Color TEXTO = Estilo.TEXTO;
    private static final Color APAGADO = Estilo.APAGADO;

    private enum Ferramenta {
        NENHUMA("Arrastar / chutar rasteiro"),
        CHUTAR_ALTO("Arrastar / chutar alto (chip)"),
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
    private final ExecutorDeCenario cenarios;

    private Ferramenta ferramenta = Ferramenta.NENHUMA;
    private JLabel statusFerramenta;

    private Janela(FonteDeVisao fonte, CanalDeControle controle, ConsoleLocal console,
                   Rede rede, ExecutorDeCenario cenarios) {
        this.fonte = fonte;
        this.controle = controle;
        this.console = console;
        this.rede = rede;
        this.cenarios = cenarios;
        this.campo = new Campo(fonte);
    }

    /**
     * Abre a janela.
     *
     * @param aCadaQuadro roda antes de cada repaint: avanca a fisica e publica
     * @param rede        presenca na rede, ou {@code null} se rodando offline
     * @param cenarios     tocador de cenarios de teste
     */
    public static JFrame abrir(String titulo, FonteDeVisao fonte, CanalDeControle controle,
                               ConsoleLocal console, Runnable aCadaQuadro, Rede rede,
                               ExecutorDeCenario cenarios) {
        Janela j = new Janela(fonte, controle, console, rede, cenarios);
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
                    case NENHUMA, CHUTAR_ALTO -> {
                        if (p.distancia(campo.quadro().bola().posicao()) < 250) {
                            campo.setMirandoChip(ferramenta == Ferramenta.CHUTAR_ALTO);
                            campo.setMira(true, p);
                        }
                    }
                }
            }

            /**
             * Arrastar move o campo, a menos que o gesto tenha comecado uma mira.
             *
             * <p>Antes so o botao direito arrastava. No trackpad do mac nao ha
             * botao direito para segurar -- clique de dois dedos nao se sustenta
             * enquanto um terceiro arrasta -- e o campo era, na pratica, fixo.
             * Nao ha conflito com o chute porque a mira so comeca com o clique a
             * menos de 250 mm da bola; fora disso o arrasto nao fazia nada.
             */
            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - ultimoX;
                int dy = e.getY() - ultimoY;
                ultimoX = e.getX();
                ultimoY = e.getY();
                campo.setMouseTela(e.getX(), e.getY());

                if (campo.isMostrarMira()) {
                    campo.setMira(true, campo.telaParaMundo(e.getX(), e.getY()));
                    return;
                }
                campo.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                campo.deslocar(dx, dy);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                campo.setCursor(Cursor.getDefaultCursor());
                if (!campo.isMostrarMira()) return;
                campo.setMira(false, null);

                Vec2 bola = campo.quadro().bola().posicao();
                Vec2 impulso = campo.velocidadeDeMira(bola);

                if (ferramenta == Ferramenta.CHUTAR_ALTO) {
                    // A elevacao reparte a velocidade mirada entre plano e vertical,
                    // igual ao chutador do robo: mirar mais longe sobe mais alto.
                    double elevacao = RobotCommand.ANGULO_CHIP_PADRAO;
                    controle.reposicionarBola(bola, 0,
                            impulso.escala(Math.cos(elevacao)),
                            impulso.norma() * Math.sin(elevacao));
                } else {
                    controle.reposicionarBola(bola, impulso);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) { campo.setMouseTela(e.getX(), e.getY()); }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                campo.aplicarZoom(Campo.fatorDeZoom(e), e.getX(), e.getY());
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

        // 12 colunas, e nao 9: com a fonte monoespacada da Estilo, coluna e
        // caractere na regua, sem a folga que uma proporcional dava de graca.
        // "Adversario" tem 10 e desaparecia pela esquerda no campo de 9.
        JTextField nomeAzul = new JTextField(campo.quadro().nomeAzul(), 12);
        JTextField qtdAzul = new JTextField(
                String.valueOf(campo.quadro().quantidade(Cor.AZUL)), 2);
        JTextField nomeAmarelo = new JTextField(campo.quadro().nomeAmarelo(), 12);
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
        painel.add(painelCenarios());
        painel.add(Box.createVerticalStrut(20));
        painel.add(painelLog());
        painel.add(Box.createVerticalStrut(20));
        painel.add(painelFisica());
        painel.add(Box.createVerticalStrut(20));
        painel.add(painelRede());

        painel.add(Box.createVerticalStrut(20));
        painel.add(rotulo("<html>arraste para mover o campo<br>scroll aplica zoom</html>", APAGADO));
        painel.add(Box.createVerticalGlue());
        return painel;
    }

    private static final String NENHUM = "(nenhum)";

    private JPanel painelCenarios() {
        JPanel painel = coluna();
        painel.add(titulo("Cenario de teste"));
        painel.add(rotulo("<html><i>roteiro fixo; faz o papel de<br>"
                + "um software de time conectado</i></html>", APAGADO));
        painel.add(Box.createVerticalStrut(6));

        List<Roteiro> disponiveis = Cenarios.todos(ParametrosFisica.padrao().gravidade());

        JComboBox<Object> escolha = new JComboBox<>();
        escolha.addItem(NENHUM);
        for (Roteiro r : disponiveis) escolha.addItem(r);
        // O combo guarda o Roteiro inteiro, mas mostra so o nome.
        escolha.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    javax.swing.JList<?> lista, Object valor, int i, boolean sel, boolean foco) {
                Object texto = valor instanceof Roteiro r ? r.nome() : valor;
                return super.getListCellRendererComponent(lista, texto, i, sel, foco);
            }
        });
        escolha.setAlignmentX(Component.LEFT_ALIGNMENT);
        escolha.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel descricao = rotulo(" ", APAGADO);
        JLabel progresso = rotulo(" ", new Color(150, 210, 150));

        escolha.addActionListener(e -> {
            Roteiro r = escolha.getSelectedItem() instanceof Roteiro sel ? sel : null;
            cenarios.selecionar(r);
            descricao.setText(r == null ? " "
                    : "<html><body style='width:210px'>" + r.descricao() + "</body></html>");
        });
        if (cenarios.getRoteiro() != null) escolha.setSelectedItem(cenarios.getRoteiro());

        JCheckBox recolher = caixa("recolher robos que atrapalham", true);
        recolher.setToolTipText("A formacao inicial tem quatro robos sobre o eixo X, "
                + "por onde os cenarios mandam a bola");
        recolher.addActionListener(e -> cenarios.setRecolherRobos(recolher.isSelected()));

        painel.add(escolha);
        painel.add(Box.createVerticalStrut(4));
        painel.add(recolher);
        painel.add(descricao);
        painel.add(progresso);

        new Timer(200, e -> {
            Roteiro r = cenarios.getRoteiro();
            progresso.setText(r == null ? " "
                    : String.format("ciclo %.1f / %.1f s",
                            cenarios.getTempoDoCiclo(), r.duracao()));
        }).start();

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

        JButton configurar = new JButton("Configurar...");
        configurar.setAlignmentX(Component.LEFT_ALIGNMENT);
        configurar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        configurar.addActionListener(e -> DialogoFisica.abrir(campo, controle,
                () -> campo.quadro().parametros()));
        painel.add(configurar);
        painel.add(Box.createVerticalStrut(8));

        // O ganho do chute fica aqui, e nao no dialogo de fisica: ele governa a
        // sensibilidade do arrasto do mouse, nao o comportamento do mundo.
        JSlider ganhoChute = new JSlider(0, 1000);
        painel.add(linhaSlider(ganhoChute, "forca do chute (mouse)", 0.25, 3.0,
                Campo.GANHO_CHUTE_PADRAO, "%.2f",
                () -> campo.setGanhoChute(valor(ganhoChute, 0.25, 3.0))));
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
