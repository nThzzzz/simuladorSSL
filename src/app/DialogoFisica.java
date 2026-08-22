package app;

import app.fisica.Ensaio;
import app.fisica.Ensaios;
import app.fisica.PainelEnsaio;
import app.fisica.Trajetoria;
import model.ParametrosFisica;
import visao.CanalDeControle;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Janela de fisica com previa animada de cada parametro.
 *
 * <p>Um slider marcado "restituicao 0,50" nao diz nada a ninguem. Aqui cada
 * parametro vem com um ensaio que o isola, animado lado a lado: fantasma cinza
 * para o que esta valendo no mundo, laranja para o que o slider propoe, e o
 * numero que resume o efeito embaixo.
 *
 * <p>Os ensaios sao refeitos a cada movimento do slider, o que so e viavel
 * porque cada um e uma simulacao de um ou dois corpos por alguns segundos: roda
 * em microssegundos.
 */
public final class DialogoFisica extends JDialog {

    private static final Color FUNDO   = new Color(32, 32, 36);
    private static final Color TEXTO   = new Color(225, 225, 230);
    private static final Color APAGADO = new Color(150, 150, 160);
    private static final Color SEPARADOR = new Color(48, 48, 54);
    private static final Color ANTES   = new Color(150, 152, 160);
    private static final Color DEPOIS  = new Color(255, 165, 70);

    private static final int PASSOS_DO_SLIDER = 1000;

    private static final int LARGURA_CONTROLES = 290;
    private static final int LARGURA_FAIXA = 430;
    private static final int ALTURA_LINHA = 76;

    private final CanalDeControle controle;
    private final List<Linha> linhas = new ArrayList<>();
    private final JLabel aviso = new JLabel(" ");

    private ParametrosFisica aplicado;
    private ParametrosFisica pendente;

    private record Linha(Ensaio ensaio, JSlider slider, PainelEnsaio painel,
                         JLabel valor, JLabel medida) {}

    private DialogoFisica(Window dono, CanalDeControle controle, ParametrosFisica atual) {
        super(dono, "Fisica do mundo", ModalityType.MODELESS);
        this.controle = controle;
        this.aplicado = atual;
        this.pendente = atual;

        JPanel corpo = new JPanel();
        corpo.setLayout(new BoxLayout(corpo, BoxLayout.Y_AXIS));
        corpo.setBackground(FUNDO);
        corpo.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        for (Ensaio e : Ensaios.todos()) {
            corpo.add(montarLinha(e));
            corpo.add(Box.createVerticalStrut(8));
        }

        JScrollPane rolagem = new JScrollPane(corpo,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rolagem.setBorder(null);
        rolagem.getVerticalScrollBar().setUnitIncrement(18);

        aviso.setForeground(APAGADO);
        aviso.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));

        JButton aplicar = new JButton("Aplicar no mundo");
        aplicar.setFont(aplicar.getFont().deriveFont(Font.BOLD));
        aplicar.addActionListener(e -> aplicar());
        JButton restaurar = new JButton("Restaurar padrao");
        restaurar.addActionListener(e -> carregar(ParametrosFisica.padrao()));
        JButton fechar = new JButton("Fechar");
        fechar.addActionListener(e -> dispose());

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        botoes.setBackground(FUNDO);
        botoes.add(restaurar);
        botoes.add(aplicar);
        botoes.add(fechar);

        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setBackground(FUNDO);
        rodape.add(aviso, BorderLayout.NORTH);
        rodape.add(botoes, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(rolagem, BorderLayout.CENTER);
        add(rodape, BorderLayout.SOUTH);

        recalcularTudo();
        atualizarAviso();

        // Um relogio unico para as sete faixas, em vez de um por painel.
        Timer animacao = new Timer(33, e -> linhas.forEach(l -> l.painel().avancar()));
        animacao.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { animacao.stop(); }
        });

        setPreferredSize(new Dimension(
                LARGURA_CONTROLES + LARGURA_FAIXA + 70,
                Ensaios.todos().size() * (ALTURA_LINHA + 8) + 116));
        pack();
        limitarATela();
        setLocationRelativeTo(dono);
    }

    /**
     * Impede a janela de nascer maior que a area util da tela.
     *
     * <p>A altura preferida cresce com o numero de ensaios; num monitor pequeno
     * ela passaria da borda e os ultimos parametros ficariam inalcancaveis. Se
     * nao couber, a rolagem assume.
     */
    private void limitarATela() {
        java.awt.Rectangle tela = getGraphicsConfiguration() != null
                ? getGraphicsConfiguration().getBounds()
                : new java.awt.Rectangle(0, 0, 1280, 800);
        java.awt.Insets bordas = getGraphicsConfiguration() != null
                ? java.awt.Toolkit.getDefaultToolkit()
                        .getScreenInsets(getGraphicsConfiguration())
                : new java.awt.Insets(0, 0, 0, 0);

        int alturaUtil = tela.height - bordas.top - bordas.bottom - 40;
        int larguraUtil = tela.width - bordas.left - bordas.right - 40;
        setSize(Math.min(getWidth(), larguraUtil), Math.min(getHeight(), alturaUtil));
    }

    public static void abrir(Component origem, CanalDeControle controle,
                             Supplier<ParametrosFisica> atual) {
        new DialogoFisica(SwingUtilities.getWindowAncestor(origem), controle, atual.get())
                .setVisible(true);
    }

    // ------------------------------------------------------------------ linha

    /**
     * Uma linha por parametro, deitada: controles a esquerda, filme a direita.
     *
     * <p>Empilhado na vertical, cada parametro ocupava umas 150 linhas de altura
     * e os sete nao cabiam na tela; deitar cada um resolve a altura e ainda sobra
     * largura para a faixa, que e onde os traçados longos precisam de espaco.
     */
    private JPanel montarLinha(Ensaio e) {
        JLabel nome = etiqueta(e.nome(), TEXTO);
        nome.setFont(nome.getFont().deriveFont(Font.BOLD, 12f));
        JLabel pergunta = etiqueta(e.pergunta(), APAGADO);
        pergunta.setFont(pergunta.getFont().deriveFont(11f));

        JLabel valor = etiqueta(" ", TEXTO);
        valor.setFont(valor.getFont().deriveFont(Font.BOLD, 12f));

        JSlider slider = new JSlider(0, PASSOS_DO_SLIDER);
        slider.setBackground(FUNDO);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JPanel esquerda = new JPanel();
        esquerda.setLayout(new BoxLayout(esquerda, BoxLayout.Y_AXIS));
        esquerda.setBackground(FUNDO);
        esquerda.setPreferredSize(new Dimension(LARGURA_CONTROLES, ALTURA_LINHA));
        esquerda.add(nome);
        esquerda.add(pergunta);
        esquerda.add(Box.createVerticalGlue());
        esquerda.add(valor);
        esquerda.add(slider);

        PainelEnsaio faixa = new PainelEnsaio(e);
        faixa.setPreferredSize(new Dimension(LARGURA_FAIXA, ALTURA_LINHA - 18));

        JLabel medida = etiqueta(" ", APAGADO);
        medida.setFont(medida.getFont().deriveFont(11f));

        JPanel direita = new JPanel(new BorderLayout(0, 2));
        direita.setBackground(FUNDO);
        direita.add(faixa, BorderLayout.CENTER);
        direita.add(medida, BorderLayout.SOUTH);

        JPanel linha = new JPanel(new BorderLayout(14, 0));
        linha.setBackground(FUNDO);
        linha.setAlignmentX(Component.LEFT_ALIGNMENT);
        linha.setPreferredSize(new Dimension(
                LARGURA_CONTROLES + LARGURA_FAIXA + 14, ALTURA_LINHA));
        linha.setMaximumSize(new Dimension(Integer.MAX_VALUE, ALTURA_LINHA));
        linha.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, SEPARADOR));
        linha.add(esquerda, BorderLayout.WEST);
        linha.add(direita, BorderLayout.CENTER);

        Linha registro = new Linha(e, slider, faixa, valor, medida);
        linhas.add(registro);
        slider.addChangeListener(ev -> {
            pendente = e.aplicar(pendente, valorDoSlider(registro));
            recalcular(registro);
            atualizarAviso();
        });
        return linha;
    }

    private double valorDoSlider(Linha l) {
        Ensaio e = l.ensaio();
        return e.min() + (e.max() - e.min()) * l.slider().getValue() / (double) PASSOS_DO_SLIDER;
    }

    private void posicionarSlider(Linha l, double valor) {
        Ensaio e = l.ensaio();
        int passo = (int) Math.round((valor - e.min()) / (e.max() - e.min()) * PASSOS_DO_SLIDER);
        l.slider().setValue(Math.max(0, Math.min(PASSOS_DO_SLIDER, passo)));
    }

    // -------------------------------------------------------------- calculos

    private void recalcularTudo() {
        for (Linha l : linhas) {
            posicionarSlider(l, l.ensaio().valorDe(pendente));
            recalcular(l);
        }
    }

    private void recalcular(Linha l) {
        Ensaio e = l.ensaio();
        Trajetoria antes = e.roda().apply(aplicado);
        Trajetoria depois = e.roda().apply(pendente);

        l.valor().setText(String.format(e.formato(), e.valorDe(pendente)));
        l.painel().mostrar(antes, depois);
        l.medida().setText(String.format(
                "<html><font color='#%06x'>antes %s %s</font>"
                        + " &nbsp;&rarr;&nbsp; <font color='#%06x'><b>depois %s %s</b></font></html>",
                ANTES.getRGB() & 0xFFFFFF, numero(antes.medida()), e.unidade(),
                DEPOIS.getRGB() & 0xFFFFFF, numero(depois.medida()), e.unidade()));
    }

    private static String numero(double v) {
        return Math.abs(v) >= 100 ? String.format("%.0f", v) : String.format("%.2f", v);
    }

    private void aplicar() {
        controle.ajustarFisica(pendente);
        aplicado = pendente;
        recalcularTudo();
        atualizarAviso();
    }

    private void atualizarAviso() {
        boolean mudou = !pendente.equals(aplicado);
        aviso.setForeground(mudou ? new Color(230, 190, 110) : APAGADO);
        aviso.setText(mudou
                ? "ha mudancas nao aplicadas; o mundo ainda usa a fisica em cinza"
                : "o mundo esta usando exatamente esta fisica");
    }

    private void carregar(ParametrosFisica novo) {
        pendente = novo;
        recalcularTudo();
        atualizarAviso();
    }

    private static JLabel etiqueta(String texto, Color cor) {
        JLabel l = new JLabel(texto);
        l.setForeground(cor);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }
}
