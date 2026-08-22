package demo;

import engine.Evento;
import engine.TipoEvento;
import sim.ControladorExterno;
import sim.Simulacao;

/**
 * Toca um {@link Roteiro} em ciclo, disparando cada passo na hora marcada.
 *
 * <p>Roda ANTES da fisica do quadro, para que um comando marcado em {@code t}
 * ja governe o movimento daquele mesmo quadro.
 *
 * <p>Cada passo vira um evento no log. Sem isso, quem analisa o dataset teria de
 * adivinhar em que fase do cenario cada quadro caiu.
 */
public final class ExecutorDeCenario {

    private final Simulacao sim;
    private final Contexto contexto;

    private Roteiro roteiro;
    private double tCiclo;
    private int proximoPasso;

    public ExecutorDeCenario(Simulacao sim, ControladorExterno comandos) {
        this.sim = sim;
        this.contexto = new Contexto(sim, comandos);
    }

    public Roteiro getRoteiro() { return roteiro; }

    /**
     * Se o cenario deve encostar na linha de fundo os robos que nao participam.
     *
     * <p>Ligado por padrao. A formacao inicial e uma cruz com quatro robos sobre
     * o eixo X, exatamente por onde os cenarios mandam a bola: sem recolher, o
     * chute bate num companheiro e volta em vez de ir ao gol. Desligar serve para
     * ver justamente essa interferencia.
     */
    public void setRecolherRobos(boolean ativo) {
        contexto.setRecolherAtivo(ativo);
        reiniciar();
    }

    public boolean isRecolherRobos() { return contexto.isRecolherAtivo(); }

    /** Troca o cenario em execucao. {@code null} desliga e para todos os robos. */
    public void selecionar(Roteiro novo) {
        this.roteiro = novo;
        reiniciar();
        if (novo == null) contexto.limparComandos();
    }

    private void reiniciar() {
        tCiclo = 0;
        proximoPasso = 0;
    }

    /** Tempo decorrido dentro do ciclo atual, para a interface mostrar. */
    public double getTempoDoCiclo() { return tCiclo; }

    public void tick(double dt) {
        if (roteiro == null) return;

        while (proximoPasso < roteiro.passos().size()
                && roteiro.passos().get(proximoPasso).t() <= tCiclo) {
            Passo p = roteiro.passos().get(proximoPasso++);
            p.acao().accept(contexto);
            sim.getMundo().registrar(TipoEvento.CENARIO, Evento.dados(
                    "cenario", roteiro.nome(),
                    "passo", p.rotulo(),
                    "t_ciclo", p.t()));
        }

        tCiclo += dt;
        if (tCiclo >= roteiro.duracao()) reiniciar();
    }
}
