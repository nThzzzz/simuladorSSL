package demo;

import core.Vec2;
import model.Cor;
import model.RobotCommand;

import java.util.List;

/**
 * Os cenarios de teste que acompanham o simulador.
 *
 * <p>Servem para tres coisas: exercitar chutador, chip e dribbler sem precisar
 * de um software de time; dar ao modo headless algo para gravar (sem eles o log
 * sai com 12 robos parados); e servir de referencia visual de que a fisica esta
 * fazendo o que devia.
 *
 * <p>Nao sao jogo. Sao roteiros fixos, sem nenhuma decisao tomada a partir do
 * estado do mundo.
 */
public final class Cenarios {

    private Cenarios() {}

    /** Velocidade de chip que, a 45 graus, alcanca {@code distancia} milimetros. */
    private static double chipParaAlcancar(double distancia, double gravidade) {
        // Alcance de um projetil a 45 graus e v^2 * sen(90) / g, ou seja v^2 / g.
        return Math.sqrt(distancia * gravidade);
    }

    public static Roteiro chuteNoGol() {
        return Roteiro.de("Chute no gol",
                        "Robo conduz a bola e chuta rasteiro no maximo da regra")
                .em(0.0, "posiciona", c -> {
                    c.limparComandos();
                    c.recolherTodos();
                    c.posicionar(Cor.AZUL, 0, new Vec2(-2000, 0), 0);
                    c.bolaNaBoca(Cor.AZUL, 0);
                })
                .em(0.4, "avanca com dribbler", c ->
                        c.comandar(Cor.AZUL, 0,
                                RobotCommand.mover(1200, 0, 0).comDribbler(true)))
                .em(1.4, "chuta 6,5 m/s", c ->
                        c.comandar(Cor.AZUL, 0,
                                RobotCommand.mover(0, 0, 0).comChute(6500)))
                .em(1.5, "solta o chutador", c ->
                        c.comandar(Cor.AZUL, 0, RobotCommand.PARADO))
                .reiniciaEm(5.0);
    }

    public static Roteiro passeComChip(double gravidade) {
        // O adversario fica entre os dois: a graca do chip e passar por cima dele.
        // O alcance e onde a bola TOCA, nao onde ela para: depois dos quiques ela
        // ainda rola bastante, e o receptor precisa ficar nessa janela para
        // receber a bola rolando em vez de saltitando.
        double alcance = 2500;
        double velocidade = chipParaAlcancar(alcance, gravidade);

        return Roteiro.de("Passe com chip",
                        "Chip por cima de um adversario, recebido com o dribbler")
                .em(0.0, "posiciona", c -> {
                    c.limparComandos();
                    c.recolherTodos();
                    c.posicionar(Cor.AZUL, 0, new Vec2(-3000, 0), 0);
                    c.posicionar(Cor.AMARELO, 0, new Vec2(-1000, 0), Math.PI);
                    c.posicionar(Cor.AZUL, 1, new Vec2(1400, 0), Math.PI);
                    c.bolaNaBoca(Cor.AZUL, 0);
                })
                .em(0.3, "receptor liga o dribbler", c ->
                        c.comandar(Cor.AZUL, 1,
                                RobotCommand.mover(0, 0, 0).comDribbler(true)))
                .em(0.6, "chip a 45 graus", c ->
                        c.comandar(Cor.AZUL, 0, RobotCommand.mover(0, 0, 0)
                                .comChip(velocidade, RobotCommand.ANGULO_CHIP_PADRAO)))
                .em(0.7, "solta o chutador", c ->
                        c.comandar(Cor.AZUL, 0, RobotCommand.PARADO))
                .reiniciaEm(6.0);
    }

    public static Roteiro conducaoComRoller() {
        RobotCommand frente   = RobotCommand.mover(1500, 0, 0).comDribbler(true);
        RobotCommand re       = RobotCommand.mover(-1200, 0, 0).comDribbler(true);
        RobotCommand esquerda = RobotCommand.mover(0, 1200, 0).comDribbler(true);
        RobotCommand direita  = RobotCommand.mover(0, -1200, 0).comDribbler(true);
        RobotCommand giro     = RobotCommand.mover(0, 0, 3.0).comDribbler(true);
        RobotCommand parado   = RobotCommand.mover(0, 0, 0).comDribbler(true);

        // A sequencia e escolhida para exercitar o roller, nao para parecer bonita.
        // Andar em circulo e o caso facil: a bola acompanha por inercia mesmo sem
        // dribbler. Quem separa "esta segurando" de "esta empurrando" e a parada
        // seca e a marcha a re, onde a inercia jogaria a bola para longe na hora.
        return Roteiro.de("Conducao com roller",
                        "Frente, freada seca, re, laterais e giro, tudo com a bola presa")
                .em(0.0, "posiciona", c -> {
                    c.limparComandos();
                    c.recolherTodos();
                    c.posicionar(Cor.AZUL, 0, new Vec2(-3000, 0), 0);
                    c.bolaNaBoca(Cor.AZUL, 0);
                })
                .em(0.4,  "acelera para frente", c -> c.comandar(Cor.AZUL, 0, frente))
                .em(1.8,  "freada seca",         c -> c.comandar(Cor.AZUL, 0, parado))
                .em(2.6,  "marcha a re",         c -> c.comandar(Cor.AZUL, 0, re))
                .em(4.0,  "freada seca",         c -> c.comandar(Cor.AZUL, 0, parado))
                .em(4.8,  "desliza para a esquerda", c -> c.comandar(Cor.AZUL, 0, esquerda))
                .em(6.0,  "desliza para a direita",  c -> c.comandar(Cor.AZUL, 0, direita))
                .em(7.2,  "gira no proprio eixo",    c -> c.comandar(Cor.AZUL, 0, giro))
                .em(8.4,  "para",                c -> c.comandar(Cor.AZUL, 0, parado))
                .em(9.0,  "solta a bola",        c -> c.comandar(Cor.AZUL, 0, RobotCommand.PARADO))
                .reiniciaEm(10.5);
    }

    public static List<Roteiro> todos(double gravidade) {
        return List.of(chuteNoGol(), passeComChip(gravidade), conducaoComRoller());
    }
}
