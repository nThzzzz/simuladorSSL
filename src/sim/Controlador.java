package sim;

import engine.Mundo;

/**
 * Fonte de comandos para os robos de um quadro.
 *
 * <p>Deve escrever o {@link model.RobotCommand} de cada robo sob sua
 * responsabilidade antes de {@link Mundo#passo(double)}. Hoje ha uma unica
 * implementacao, {@link ControladorExterno}, alimentada pelo {@code RobotControl}
 * que chega pela rede: o simulador nao decide nada por conta propria.
 */
public interface Controlador {
    void decidir(Mundo mundo, double dt);
}
