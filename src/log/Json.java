package log;

import java.util.Map;

/**
 * Serializador JSON minimo -- suficiente para o log e sem dependencia externa,
 * o que mantem o projeto compilando com {@code javac} puro.
 *
 * <p>Suporta null, String, Number, Boolean, Enum, Map e Iterable.
 */
public final class Json {

    private Json() {}

    public static String escrever(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(valor, sb);
        return sb.toString();
    }

    private static void escrever(Object valor, StringBuilder sb) {
        switch (valor) {
            case null -> sb.append("null");
            case String s -> escaparPara(s, sb);
            case Boolean b -> sb.append(b);
            case Double d -> sb.append(numero(d));
            case Float f -> sb.append(numero(f));
            case Number n -> sb.append(n);
            case Enum<?> e -> escaparPara(e.name(), sb);
            case Map<?, ?> m -> {
                sb.append('{');
                boolean primeiro = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!primeiro) sb.append(',');
                    primeiro = false;
                    escaparPara(String.valueOf(e.getKey()), sb);
                    sb.append(':');
                    escrever(e.getValue(), sb);
                }
                sb.append('}');
            }
            case Iterable<?> it -> {
                sb.append('[');
                boolean primeiro = true;
                for (Object o : it) {
                    if (!primeiro) sb.append(',');
                    primeiro = false;
                    escrever(o, sb);
                }
                sb.append(']');
            }
            default -> escaparPara(String.valueOf(valor), sb);
        }
    }

    /**
     * Escreve o double sem perda de precisao. Nao arredonda de proposito: o
     * {@code meta.json} guarda o dt que reproduz a corrida, e um dt arredondado
     * a 4 casas gera uma trajetoria diferente da que foi gravada.
     */
    public static String numero(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "null";
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    private static void escaparPara(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
