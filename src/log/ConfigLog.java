package log;

/**
 * O que gravar numa corrida.
 *
 * <p>Os dois streams tem custo muito diferente: o tracking e denso (uma linha
 * por robo por quadro, ~95% do volume) e os eventos sao esparsos. Separar os
 * dois permite gravar so a semantica da partida quando a trajetoria completa
 * nao interessa.
 *
 * @param tracking         grava {@code ball.csv} e {@code robots.csv}
 * @param eventos          grava {@code events.jsonl}
 * @param intervaloQuadros grava 1 a cada N quadros no tracking (1 = todos).
 *                         Eventos nunca sao decimados: perder um chute ou um
 *                         gol para economizar disco nao faz sentido.
 */
public record ConfigLog(boolean tracking, boolean eventos, int intervaloQuadros) {

    public static final ConfigLog COMPLETO = new ConfigLog(true, true, 1);
    public static final ConfigLog SO_EVENTOS = new ConfigLog(false, true, 1);
    public static final ConfigLog DESLIGADO = new ConfigLog(false, false, 1);

    public ConfigLog {
        if (intervaloQuadros < 1) {
            throw new IllegalArgumentException("intervaloQuadros deve ser >= 1, recebido: "
                    + intervaloQuadros);
        }
    }

    /** True se ha algo a gravar -- caso contrario nem vale criar o diretorio. */
    public boolean gravaAlgo() { return tracking || eventos; }

    /** Taxa efetiva de amostragem do tracking, em Hz. */
    public double hzEfetivo(double dt) { return 1.0 / (dt * intervaloQuadros); }
}
