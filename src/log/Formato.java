package log;

import java.math.BigDecimal;

/** Formatacao numerica compacta para os streams CSV. */
public final class Formato {

    private Formato() {}

    /**
     * Arredonda para 4 casas decimais (0,1 micrometro em mm) e remove zeros a
     * direita. Usado so no tracking, onde o volume importa -- o {@code meta.json}
     * e o {@code events.jsonl} gravam o valor sem perda, para nao inviabilizar a
     * reproducao da corrida.
     */
    public static String compacto(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "";
        double r = Math.round(d * 1e4) / 1e4;
        if (r == Math.rint(r) && Math.abs(r) < 1e15) return String.valueOf((long) r);
        return BigDecimal.valueOf(r).stripTrailingZeros().toPlainString();
    }
}
