package visao;

import core.Vec2;
import model.Cor;
import model.Robot;

/**
 * Estado de um robo num quadro. Imutavel.
 *
 * <p>Nao carrega o comando aplicado: o protocolo oficial nao tem campo para ele,
 * entao guardar aqui so criaria um dado que nunca sai do processo. O comando de
 * cada quadro vai para o log, que e onde ele serve para alguma coisa.
 */
public record EstadoRobo(
        int id,
        Cor cor,
        Vec2 posicao,
        double theta,
        Vec2 velocidade,
        double omega,
        boolean posse
) {
    public double rapidez() { return velocidade.norma(); }

    public String chave() { return cor.tag() + "_" + id; }

    /** Ponto no centro da face plana do dribbler. */
    public Vec2 pontoDribbler() {
        return posicao.mais(Vec2.dePolar(Robot.DIST_FACE_FRONTAL, theta));
    }
}
