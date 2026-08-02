import Engine.Mundo;
import View.Campo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SSL Simulator Engine");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            Mundo mundo = new Mundo(Campo.FIELD_WIDTH, Campo.FIELD_HEIGHT);

            Campo campo = new Campo(mundo);
            mundo.inicializarPartida("RoboFEI", 6, "Adversário", 6, campo);
            frame.add(campo, BorderLayout.CENTER);

            setupController(campo, mundo);

            JPanel painelControle = criarPainelControle(mundo, campo);
            frame.add(painelControle, BorderLayout.SOUTH);

            Timer gameLoop = new Timer(16, e -> {
                mundo.updatePhysics(0.016);
                campo.repaint();
            });
            gameLoop.start();

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static JPanel criarPainelControle(Mundo mundo, Campo campo) {
        JPanel painel = new JPanel();
        painel.setBackground(new Color(40, 40, 40));

        JLabel lblAzul = new JLabel("Model.Time Azul:");
        lblAzul.setForeground(new Color(60, 130, 255));
        JTextField txtNomeAzul = new JTextField("RoboFEI", 8);
        JTextField txtQtdAzul = new JTextField("6", 2);

        JLabel lblAmarelo = new JLabel("  Model.Time Amarelo:");
        lblAmarelo.setForeground(new Color(255, 210, 0));
        JTextField txtNomeAmarelo = new JTextField("Adversário", 8);
        JTextField txtQtdAmarelo = new JTextField("6", 2);

        JButton btnAtualizar = new JButton("Confirmar Alteração");

        painel.add(lblAzul); painel.add(txtNomeAzul);
        painel.add(criaLabelBranca("Qtd:")); painel.add(txtQtdAzul);
        painel.add(lblAmarelo); painel.add(txtNomeAmarelo);
        painel.add(criaLabelBranca("Qtd:")); painel.add(txtQtdAmarelo);
        painel.add(new JLabel("   "));
        painel.add(btnAtualizar);

        btnAtualizar.addActionListener(e -> {
            try {
                String nomeAzul = txtNomeAzul.getText();
                int qtdAzul = Integer.parseInt(txtQtdAzul.getText());
                String nomeAmarelo = txtNomeAmarelo.getText();
                int qtdAmarelo = Integer.parseInt(txtQtdAmarelo.getText());

                mundo.inicializarPartida(nomeAzul, qtdAzul, nomeAmarelo, qtdAmarelo, campo);

                campo.repaint();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(null, "A quantidade de robôs deve ser um número inteiro!", "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });

        return painel;
    }

    private static JLabel criaLabelBranca(String texto) {
        JLabel label = new JLabel(texto);
        label.setForeground(Color.WHITE);
        return label;
    }

    private static void setupController(Campo campo, Mundo mundo) {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                double cartX = getCartX(e.getX(), campo);
                double cartY = getCartY(e.getY(), campo);
                if (Math.hypot(cartX - mundo.getBola().getX(), cartY - mundo.getBola().getY()) < 15) {
                    campo.showAim = true;
                    mundo.getBola().aplicarForca(0, 0);
                }
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                campo.mouseX = e.getX(); campo.mouseY = e.getY();
                if (campo.showAim) {
                    campo.dragX = getCartX(e.getX(), campo);
                    campo.dragY = getCartY(e.getY(), campo);
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (campo.showAim) {
                    campo.showAim = false;
                    double forcaX = (campo.dragX - mundo.getBola().getX()) * 3.0;
                    double forcaY = (campo.dragY - mundo.getBola().getY()) * 3.0;
                    mundo.getBola().aplicarForca(forcaX, forcaY);
                }
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                campo.mouseX = e.getX(); campo.mouseY = e.getY();
            }
            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                double rot = e.getPreciseWheelRotation();
                if (rot < 0) campo.zoomFactor *= (1.0 - (rot * 0.05));
                else if (rot > 0) campo.zoomFactor /= (1.0 + (rot * 0.05));
                campo.zoomFactor = Math.max(0.2, Math.min(campo.zoomFactor, 5.0));
            }
        };
        campo.addMouseListener(mouse);
        campo.addMouseMotionListener(mouse);
        campo.addMouseWheelListener(mouse);
    }

    private static double getCartX(int screenX, Campo campo) {
        return (screenX - (Campo.MARGIN + Campo.FIELD_WIDTH / 2.0)) / campo.zoomFactor;
    }
    private static double getCartY(int screenY, Campo campo) {
        return ((Campo.MARGIN + Campo.FIELD_HEIGHT / 2.0) - screenY) / campo.zoomFactor;
    }
}