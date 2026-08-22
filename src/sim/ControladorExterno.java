package sim;

import engine.Mundo;
import model.Cor;
import model.Robot;
import model.RobotCommand;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aplica comandos vindos de fora do processo.
 *
 * <p>E o ponto de entrada de um software de time: ele manda {@code RobotControl}
 * pela rede e os comandos aterrissam aqui. Como o simulador nao tem nenhuma
 * logica de jogo propria, este e o unico caminho pelo qual um robo se move.
 *
 * <p>O mapa e concorrente de proposito: quem escreve e a thread de rede, quem le
 * e a thread da simulacao.
 */
public final class ControladorExterno implements Controlador {

    private final Map<String, RobotCommand> comandos = new ConcurrentHashMap<>();

    /** Registra o comando mais recente de um robo. */
    public void receber(Cor cor, int id, RobotCommand comando) {
        comandos.put(cor.tag() + "_" + id, comando);
    }

    public void limpar() { comandos.clear(); }

    public int comandosAtivos() { return comandos.size(); }

    @Override
    public void decidir(Mundo mundo, double dt) {
        if (comandos.isEmpty()) return;
        for (Robot r : mundo.getRobos()) {
            RobotCommand c = comandos.get(r.chave());
            if (c != null) r.setComando(c);
        }
    }
}
