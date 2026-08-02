import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SSL Simulator 2D");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Usamos BorderLayout para dividir a tela entre o Campo (Centro) e os Controles (Baixo)
            frame.setLayout(new BorderLayout());

            // 1. Cria o campo com valores iniciais padrão (já carrega com 6 de cada lado)
            Campo campo = new Campo("RoboFEI", 6, "Adversário", 6);
            frame.add(campo, BorderLayout.CENTER);

            // 2. === PAINEL DE CONTROLE (INTERFACE NA TELA) ===
            JPanel painelControle = new JPanel();
            painelControle.setBackground(new Color(40, 40, 40)); // Fundo escuro combinando

            // Campos de entrada Time Azul
            JLabel lblAzul = new JLabel("Time Azul:");
            lblAzul.setForeground(new Color(60, 130, 255));
            JTextField txtNomeAzul = new JTextField("RoboFEI", 8);
            JTextField txtQtdAzul = new JTextField("6", 2);

            // Campos de entrada Time Amarelo
            JLabel lblAmarelo = new JLabel("  Time Amarelo:");
            lblAmarelo.setForeground(new Color(255, 210, 0));
            JTextField txtNomeAmarelo = new JTextField("Adversário", 8);
            JTextField txtQtdAmarelo = new JTextField("6", 2);

            // Botão de Confirmação
            JButton btnAtualizar = new JButton("Confirmar / Atualizar");

            // 3. Adicionando os elementos no painel inferior (um do lado do outro)
            painelControle.add(lblAzul);
            painelControle.add(txtNomeAzul);
            painelControle.add(criaLabelBranca("Qtd:"));
            painelControle.add(txtQtdAzul);

            painelControle.add(lblAmarelo);
            painelControle.add(txtNomeAmarelo);
            painelControle.add(criaLabelBranca("Qtd:"));
            painelControle.add(txtQtdAmarelo);

            painelControle.add(new JLabel("   ")); // Espaçador
            painelControle.add(btnAtualizar);

            // 4. === AÇÃO DO BOTÃO ===
            btnAtualizar.addActionListener(e -> {
                try {
                    // Pega o texto que você digitou na tela
                    String nomeAzul = txtNomeAzul.getText();
                    int qtdAzul = Integer.parseInt(txtQtdAzul.getText());

                    String nomeAmarelo = txtNomeAmarelo.getText();
                    int qtdAmarelo = Integer.parseInt(txtQtdAmarelo.getText());

                    // Chama a nova função do campo para atualizar os robôs em tempo real!
                    campo.atualizarPartida(nomeAzul, qtdAzul, nomeAmarelo, qtdAmarelo);

                } catch (NumberFormatException ex) {
                    // Proteção básica caso alguém digite letras onde devia ser número
                    JOptionPane.showMessageDialog(frame, "A quantidade de robôs deve ser um número inteiro!", "Erro", JOptionPane.ERROR_MESSAGE);
                }
            });

            // Adiciona a barra de controles na parte de baixo (SOUTH) da janela principal
            frame.add(painelControle, BorderLayout.SOUTH);

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }

    // Função auxiliar só para criar textos brancos e economizar código
    private static JLabel criaLabelBranca(String texto) {
        JLabel label = new JLabel(texto);
        label.setForeground(Color.WHITE);
        return label;
    }
}