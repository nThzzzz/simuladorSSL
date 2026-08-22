package engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Evento discreto carimbado com o quadro em que ocorreu.
 *
 * <p>Junto com o stream de tracking por quadro, e isso que forma o log: o
 * tracking diz onde tudo estava, o evento diz o que aconteceu e por que.
 */
public record Evento(double t, long frame, TipoEvento tipo, Map<String, Object> dados) {

    public static Map<String, Object> dados(Object... paresChaveValor) {
        if (paresChaveValor.length % 2 != 0) {
            throw new IllegalArgumentException("esperado numero par de argumentos (chave, valor)");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            m.put(String.valueOf(paresChaveValor[i]), paresChaveValor[i + 1]);
        }
        return m;
    }
}
