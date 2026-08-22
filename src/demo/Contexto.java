package demo;

import core.Vec2;
import engine.Mundo;
import model.Bola;
import model.Cor;
import model.Robot;
import model.RobotCommand;
import sim.ControladorExterno;
import sim.Simulacao;

/**
 * O que um passo de roteiro pode fazer com o mundo.
 *
 * <p>Os comandos saem pelo {@link ControladorExterno}, o mesmo ponto por onde
 * entram os comandos que chegam pela rede. Continua valendo que existe um unico
 * caminho pelo qual um robo se move: o roteiro e apenas mais um produtor de
 * {@link RobotCommand}, no lugar de um software de time.
 */
public final class Contexto {

    private final Simulacao sim;
    private final ControladorExterno comandos;
    private boolean recolherAtivo = true;

    public Contexto(Simulacao sim, ControladorExterno comandos) {
        this.sim = sim;
        this.comandos = comandos;
    }

    public Mundo mundo() { return sim.getMundo(); }

    /** Liga ou desliga o recolhimento dos robos que nao participam do cenario. */
    public void setRecolherAtivo(boolean ativo) { this.recolherAtivo = ativo; }

    public boolean isRecolherAtivo() { return recolherAtivo; }

    public Robot robo(Cor cor, int id) {
        return sim.getMundo().getEquipe(cor).getRobo(id);
    }

    /** Teleporta um robo e zera a inercia dele. */
    public void posicionar(Cor cor, int id, Vec2 posicao, double theta) {
        Robot r = robo(cor, id);
        if (r == null) return;
        r.setPosicao(posicao);
        r.setTheta(theta);
        r.setVelocidade(Vec2.ZERO);
        r.setOmega(0);
        r.setBolaNoDribbler(false);
    }

    /** Encosta a bola na face do dribbler do robo, parada. */
    public void bolaNaBoca(Cor cor, int id) {
        Robot r = robo(cor, id);
        if (r == null) return;
        sim.getMundo().reposicionarBola(
                r.pontoDribbler().mais(Vec2.dePolar(Bola.RAIO, r.getTheta())));
    }

    public void bola(Vec2 posicao) {
        sim.getMundo().reposicionarBola(posicao);
    }

    /**
     * Encosta todos os robos na propria linha de fundo, fora da area de jogo.
     *
     * <p>Todo cenario comeca por aqui. A formacao inicial e uma cruz com quatro
     * robos sobre o eixo X, que e exatamente por onde os cenarios mandam a bola:
     * sem recolher, o chute bate num companheiro e volta em vez de ir ao gol.
     */
    public void recolherTodos() {
        if (!recolherAtivo) return;
        double meioX = sim.getMundo().getGeometria().meioComprimento();
        for (Cor cor : Cor.values()) {
            int lado = model.Geometria.ladoDefendido(cor);
            int i = 0;
            for (Robot r : sim.getMundo().getEquipe(cor).getRobos()) {
                posicionar(cor, r.getId(),
                        new Vec2(lado * (meioX - 200), -2400 + i * 420),
                        lado > 0 ? Math.PI : 0);
                i++;
            }
        }
    }

    public void comandar(Cor cor, int id, RobotCommand comando) {
        comandos.receber(cor, id, comando);
    }

    /** Tira todos os robos do campo de visao do roteiro anterior. */
    public void limparComandos() {
        comandos.limpar();
        for (Robot r : sim.getMundo().getRobos()) r.setComando(RobotCommand.PARADO);
    }
}
