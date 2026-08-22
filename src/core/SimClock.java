package core;

/**
 * Relogio de simulacao com passo fixo.
 *
 * <p>O passo fixo e o que torna o log reproduzivel: o mesmo estado inicial e a
 * mesma sequencia de comandos produzem exatamente os mesmos quadros,
 * independente da carga da maquina ou do FPS de renderizacao.
 *
 * <p>O acumulador contra o relogio de parede so e usado no modo tempo real;
 * no modo headless a simulacao roda o mais rapido possivel.
 */
public final class SimClock {

    public static final double DT_PADRAO = 1.0 / 60.0;

    private final double dt;
    private long frame;
    private double tempo;

    private long ultimoNano = -1;
    private double acumulador;

    public SimClock(double dt) {
        if (dt <= 0) throw new IllegalArgumentException("dt deve ser > 0, recebido: " + dt);
        this.dt = dt;
    }

    public double getDt()    { return dt; }
    public long getFrame()   { return frame; }
    public double getTempo() { return tempo; }

    /** Avanca um tick de simulacao. */
    public void avancar() {
        frame++;
        tempo += dt;
    }

    /**
     * Quantos ticks devem rodar agora para acompanhar o relogio de parede.
     * O teto {@code maxTicks} evita a espiral da morte quando um quadro atrasa:
     * a simulacao prefere perder tempo real a travar tentando recuperar.
     */
    public int ticksPendentes(int maxTicks) {
        long agora = System.nanoTime();
        if (ultimoNano < 0) {
            ultimoNano = agora;
            return 1;
        }
        acumulador += (agora - ultimoNano) / 1_000_000_000.0;
        ultimoNano = agora;

        int n = 0;
        while (acumulador >= dt && n < maxTicks) {
            acumulador -= dt;
            n++;
        }
        if (n == maxTicks) acumulador = 0;
        return n;
    }

    public void reiniciar() {
        frame = 0;
        tempo = 0;
        acumulador = 0;
        ultimoNano = -1;
    }
}
