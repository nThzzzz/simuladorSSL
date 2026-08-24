package app.componentes;

import app.Rede;

import rede.ConfigRede;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.IOException;

/**
 * Janela de configuracao das portas de rede, aplicavel com o simulador rodando.
 *
 * <p>A validacao acontece em duas camadas de proposito. A {@link ConfigRede}
 * rejeita o que da para saber sem tocar no sistema -- endereco fora da faixa
 * multicast, porta fora da faixa, duas escutas na mesma porta. Ja "a porta esta
 * ocupada por outro processo" so aparece na hora de abrir o socket, entao vem do
 * {@link Rede#reconfigurar} como excecao e e mostrada aqui.
 */
public final class DialogoRede extends JDialog {

    private static final Color FUNDO = new Color(32, 32, 36);
    private static final Color TEXTO = new Color(225, 225, 230);
    private static final Color ERRO = new Color(235, 110, 110);
    private static final Color OK = new Color(130, 210, 130);

    private final Rede rede;

    private final JTextField grupo = new JTextField(14);
    private final JTextField portaVisao = new JTextField(6);
    private final JTextField portaControle = new JTextField(6);
    private final JTextField portaAzul = new JTextField(6);
    private final JTextField portaAmarelo = new JTextField(6);
    private final JLabel mensagem = new JLabel(" ");

    private DialogoRede(Window dono, Rede rede) {
        super(dono, "Rede", ModalityType.APPLICATION_MODAL);
        this.rede = rede;

        JPanel campos = new JPanel(new GridLayout(0, 2, 8, 8));
        campos.setBackground(FUNDO);
        campos.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        linha(campos, "Grupo multicast da visao", grupo);
        linha(campos, "Porta da visao", portaVisao);
        linha(campos, "Porta de SimulatorCommand", portaControle);
        linha(campos, "Porta RobotControl azul", portaAzul);
        linha(campos, "Porta RobotControl amarelo", portaAmarelo);

        mensagem.setForeground(TEXTO);
        mensagem.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));

        JButton aplicar = new JButton("Aplicar");
        aplicar.addActionListener(e -> aplicar());
        JButton restaurar = new JButton("Restaurar padrao");
        restaurar.addActionListener(e -> {
            preencher(ConfigRede.padrao());
            dizer("valores padrao carregados -- clique em Aplicar", TEXTO);
        });
        JButton fechar = new JButton("Fechar");
        fechar.addActionListener(e -> dispose());

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        botoes.setBackground(FUNDO);
        botoes.add(restaurar);
        botoes.add(aplicar);
        botoes.add(fechar);

        JPanel corpo = new JPanel();
        corpo.setLayout(new BoxLayout(corpo, BoxLayout.Y_AXIS));
        corpo.setBackground(FUNDO);
        corpo.add(campos);
        corpo.add(mensagem);
        corpo.add(Box.createVerticalStrut(4));

        setLayout(new BorderLayout());
        add(corpo, BorderLayout.CENTER);
        add(botoes, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(aplicar);

        preencher(rede.getConfig());
        dizer("aplicar reabre os sockets; conexoes ativas caem", new Color(150, 150, 160));

        pack();
        setMinimumSize(new Dimension(420, getHeight()));
        setLocationRelativeTo(dono);
    }

    public static void abrir(Component origem, Rede rede) {
        new DialogoRede(SwingUtilities.getWindowAncestor(origem), rede).setVisible(true);
    }

    private void aplicar() {
        ConfigRede nova;
        try {
            nova = new ConfigRede(grupo.getText().trim(),
                    inteiro(portaVisao, "porta da visao"),
                    inteiro(portaControle, "porta de SimulatorCommand"),
                    inteiro(portaAzul, "porta RobotControl azul"),
                    inteiro(portaAmarelo, "porta RobotControl amarelo"));
        } catch (IllegalArgumentException e) {
            dizer(e.getMessage(), ERRO);
            return;
        }

        try {
            rede.reconfigurar(nova);
            dizer("aplicado: " + nova.grupoVisao() + ":" + nova.portaVisao()
                    + ", escutando " + nova.portaControle() + " / "
                    + nova.portaAzul() + " / " + nova.portaAmarelo(), OK);
            System.out.println("rede reconfigurada");
            rede.anunciar();
        } catch (IOException e) {
            dizer(e.getMessage(), ERRO);
            // Mostra de volta o que ficou valendo, que pode ser a config antiga.
            preencher(rede.getConfig());
        }
    }

    private static int inteiro(JTextField campo, String nome) {
        try {
            return Integer.parseInt(campo.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nome + ": \"" + campo.getText().trim()
                    + "\" nao e um numero");
        }
    }

    private void preencher(ConfigRede c) {
        grupo.setText(c.grupoVisao());
        portaVisao.setText(String.valueOf(c.portaVisao()));
        portaControle.setText(String.valueOf(c.portaControle()));
        portaAzul.setText(String.valueOf(c.portaAzul()));
        portaAmarelo.setText(String.valueOf(c.portaAmarelo()));
    }

    private void dizer(String texto, Color cor) {
        mensagem.setForeground(cor);
        mensagem.setText("<html><body style='width:340px'>" + texto + "</body></html>");
        pack();
    }

    private static void linha(JPanel painel, String rotulo, JTextField campo) {
        JLabel l = new JLabel(rotulo);
        l.setForeground(TEXTO);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12f));
        painel.add(l);
        painel.add(campo);
    }
}
