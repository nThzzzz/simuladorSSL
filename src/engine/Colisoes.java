package engine;

import core.Vec2;
import model.Bola;
import model.ParametrosFisica;
import model.Geometria;
import model.Robot;
import model.RobotCommand;

import java.util.List;

/**
 * Resolucao de contatos e atuadores.
 *
 * <p>Duas mudancas importantes em relacao a versao anterior:
 *
 * <ul>
 *   <li>A colisao bola-robo agora usa velocidade RELATIVA. Antes so a velocidade
 *       da bola entrava na conta, o que fazia um robo em movimento atravessar
 *       uma bola parada em vez de empurra-la.
 *   <li>A face plana do dribbler so e tratada como plano dentro da largura real
 *       da boca; fora dela o contato volta a ser com a capa circular.
 * </ul>
 *
 * <p>Aproximacao conhecida: os cantos da boca do robo sao tratados como quina
 * viva, sem o arredondamento de raio {@link Bola#RAIO} da soma de Minkowski.
 */
public final class Colisoes {

    /** Meia largura da boca do robo, derivada da truncagem da capa circular. */
    public static final double MEIA_BOCA =
            Math.sqrt(Robot.RAIO * Robot.RAIO - Robot.DIST_FACE_FRONTAL * Robot.DIST_FACE_FRONTAL);

    private static final double EPS = 0.01; // mm de folga para a bola nao grudar

    private final ParametrosFisica p;

    public Colisoes(ParametrosFisica p) { this.p = p; }

    public void resolver(Mundo mundo) {
        roboParede(mundo);
        roboRobo(mundo);
        atuadores(mundo);
        bolaRobo(mundo);
        bolaParede(mundo);
    }

    // ------------------------------------------------------------------ robos

    private void roboParede(Mundo mundo) {
        Geometria g = mundo.getGeometria();
        double limX = g.limiteParedeX() - Robot.RAIO;
        double limY = g.limiteParedeY() - Robot.RAIO;

        for (Robot r : mundo.getRobos()) {
            Vec2 pos = r.getPosicao();
            Vec2 vel = r.getVelocidade();
            double x = pos.x(), y = pos.y(), vx = vel.x(), vy = vel.y();

            if (x < -limX)      { x = -limX; vx = Math.max(0, vx); }
            else if (x > limX)  { x =  limX; vx = Math.min(0, vx); }

            if (y < -limY)      { y = -limY; vy = Math.max(0, vy); }
            else if (y > limY)  { y =  limY; vy = Math.min(0, vy); }

            r.setPosicao(new Vec2(x, y));
            r.setVelocidade(new Vec2(vx, vy));
        }
    }

    private void roboRobo(Mundo mundo) {
        List<Robot> robos = mundo.getRobos();
        double minDist = 2 * Robot.RAIO;

        for (int i = 0; i < robos.size(); i++) {
            for (int j = i + 1; j < robos.size(); j++) {
                Robot a = robos.get(i), b = robos.get(j);

                Vec2 delta = b.getPosicao().menos(a.getPosicao());
                double dist = delta.norma();
                if (dist >= minDist) continue;

                // Robos exatamente sobrepostos: separa num eixo arbitrario mas estavel.
                Vec2 n = dist < 1e-6 ? new Vec2(1, 0) : delta.escala(1.0 / dist);
                double sobreposicao = minDist - dist;

                a.setPosicao(a.getPosicao().menos(n.escala(sobreposicao / 2.0)));
                b.setPosicao(b.getPosicao().mais(n.escala(sobreposicao / 2.0)));

                Vec2 velRel = b.getVelocidade().menos(a.getVelocidade());
                double velNormal = velRel.escalar(n);
                if (velNormal >= 0) continue; // ja estao se afastando

                // Massas iguais: o impulso se divide pela metade entre os dois.
                double impulso = -(1 + p.restituicaoRoboRobo()) * velNormal / 2.0;
                a.setVelocidade(a.getVelocidade().menos(n.escala(impulso)));
                b.setVelocidade(b.getVelocidade().mais(n.escala(impulso)));

                mundo.registrar(TipoEvento.COLISAO_ROBO_ROBO, Evento.dados(
                        "robo_a", a.chave(), "robo_b", b.chave(),
                        "x", a.getPosicao().x(), "y", a.getPosicao().y(),
                        "vel_aproximacao", -velNormal));
            }
        }
    }

    // -------------------------------------------------------------- atuadores

    /** Dribbler e chutador: a unica parte da fisica que responde a intencao. */
    private void atuadores(Mundo mundo) {
        Bola bola = mundo.getBola();

        for (Robot r : mundo.getRobos()) {
            RobotCommand cmd = r.getComando();
            boolean naZona = bolaNaZonaDribbler(r, bola);
            boolean tinha = r.temBolaNoDribbler();

            // --- Chute: consome a posse e lanca a bola na direcao frontal ---
            if (cmd.temChute() && (tinha || naZona)) {
                double velocidade = cmd.velChute();
                double elevacao = cmd.anguloChute();
                boolean chip = cmd.ehChip();

                // A elevacao reparte a velocidade do chutador entre o plano e a
                // vertical; a componente do robo so entra na horizontal, porque
                // o robo nao se move para cima.
                Vec2 saida = Vec2.dePolar(velocidade * Math.cos(elevacao), r.getTheta())
                        .mais(r.getVelocidade());
                double vz = velocidade * Math.sin(elevacao);

                bola.setPosicao(r.pontoDribbler().mais(
                        Vec2.dePolar(Bola.RAIO + EPS, r.getTheta())));
                bola.setZ(0);
                bola.lancar(saida, vz);

                if (tinha) {
                    r.setBolaNoDribbler(false);
                    mundo.registrar(TipoEvento.POSSE_PERDIDA, Evento.dados(
                            "robo", r.chave(), "motivo", "chute"));
                }
                mundo.registrar(chip ? TipoEvento.CHIP : TipoEvento.CHUTE, Evento.dados(
                        "robo", r.chave(),
                        "velocidade_comandada", velocidade,
                        "velocidade_bola", bola.getRapidez(),
                        "angulo_elevacao", elevacao,
                        "vz", bola.getVz(),
                        // Altura maxima prevista, util para conferir o chip no log.
                        "apice", bola.getVz() * bola.getVz() / (2 * p.gravidade()),
                        "theta", r.getTheta(),
                        "bola_x", bola.getPosicao().x(),
                        "bola_y", bola.getPosicao().y()));
                continue;
            }

            // --- Dribbler: segura a bola contra a face ---
            boolean segurando = cmd.dribbler() && naZona;
            if (segurando) {
                Vec2 pontoDeApoio = r.pontoDribbler().mais(
                        Vec2.dePolar(Bola.RAIO, r.getTheta()));
                Vec2 correcao = pontoDeApoio.menos(bola.getPosicao()).escala(p.forcaDribbler());
                bola.setPosicao(pontoDeApoio);
                bola.setVelocidade(r.getVelocidade().mais(correcao.limitado(Bola.VEL_MAX)));
                bola.marcarRolando();
            }

            if (segurando != tinha) {
                r.setBolaNoDribbler(segurando);
                mundo.registrar(segurando ? TipoEvento.POSSE_GANHA : TipoEvento.POSSE_PERDIDA,
                        Evento.dados("robo", r.chave(),
                                "motivo", segurando ? "dribbler" : "bola_saiu_da_zona",
                                "x", r.getPosicao().x(), "y", r.getPosicao().y()));
            }
        }
    }

    /**
     * True se a bola esta a frente da boca, dentro do alcance do rolo.
     *
     * <p>A tolerancia vertical e o proprio raio da bola, e nao zero. O rolo tem
     * altura: exigir {@code z == 0} faria uma bola que ainda vem saltitando baixo
     * ser recusada e rebatida pela casca, quando na pratica o dribbler a domina.
     */
    public boolean bolaNaZonaDribbler(Robot r, Bola bola) {
        if (bola.getZ() > Bola.RAIO) return false;
        Vec2 local = bola.getPosicao().menos(r.getPosicao()).paraLocal(r.getTheta());
        double alcance = Robot.DIST_FACE_FRONTAL + Bola.RAIO + p.alcanceDribbler();
        return local.x() > 0 && local.x() <= alcance && Math.abs(local.y()) <= MEIA_BOCA;
    }

    // ------------------------------------------------------------------- bola

    private void bolaRobo(Mundo mundo) {
        Bola bola = mundo.getBola();
        double somaRaios = Robot.RAIO + Bola.RAIO;
        double planoFace = Robot.DIST_FACE_FRONTAL + Bola.RAIO;

        // Bola acima do teto do robo passa por cima: e para isso que serve o chip.
        // O teste usa a base da bola, entao encostar de raspao ainda colide.
        if (bola.getZ() >= Robot.ALTURA) return;

        for (Robot r : mundo.getRobos()) {
            if (r.temBolaNoDribbler()) continue; // o dribbler ja governa a bola

            Vec2 local = bola.getPosicao().menos(r.getPosicao()).paraLocal(r.getTheta());
            double dist = local.norma();
            if (dist >= somaRaios || dist < 1e-9) continue;

            boolean naBoca = Math.abs(local.y()) <= MEIA_BOCA
                    && local.x() > Robot.DIST_FACE_FRONTAL;
            if (naBoca && local.x() >= planoFace) continue; // ainda nao encostou na face

            // Velocidade RELATIVA: e ela que decide se ha aproximacao.
            double velAntes = bola.getRapidez();
            boolean rolava = !bola.estaDeslizando();

            Vec2 velRelLocal = bola.getVelocidade().menos(r.getVelocidade())
                    .paraLocal(r.getTheta());

            Vec2 novaPosLocal;
            Vec2 novaVelRelLocal = velRelLocal;

            if (naBoca) {
                if (velRelLocal.x() < 0) {
                    novaVelRelLocal = new Vec2(
                            -velRelLocal.x() * p.restituicaoRobo(),
                            velRelLocal.y() * p.atritoTangencialRobo());
                }
                novaPosLocal = new Vec2(planoFace + EPS, local.y());
            } else {
                Vec2 n = local.escala(1.0 / dist);
                double velNormal = velRelLocal.escalar(n);
                if (velNormal < 0) {
                    Vec2 compNormal = n.escala(velNormal);
                    Vec2 compTangencial = velRelLocal.menos(compNormal);
                    novaVelRelLocal = compTangencial.escala(p.atritoTangencialRobo())
                            .menos(compNormal.escala(p.restituicaoRobo()));
                }
                novaPosLocal = n.escala(somaRaios + EPS);
            }

            bola.setPosicao(r.getPosicao().mais(novaPosLocal.paraGlobal(r.getTheta())));
            Vec2 velGlobal = novaVelRelLocal.paraGlobal(r.getTheta()).mais(r.getVelocidade());
            // Mesmo efeito do quique na parede: a bola inverte a translacao mas
            // leva o giro consigo, e o atrito precisa reverte-lo antes de ela
            // voltar a rolar. Sem isto a bola escapa do robo viva demais.
            bola.rebater(velGlobal, bola.getVz(),
                    rolamentoAposQuique(velAntes, rolava, p.restituicaoRobo()));

            mundo.registrar(TipoEvento.COLISAO_BOLA_ROBO, Evento.dados(
                    "robo", r.chave(),
                    "regiao", naBoca ? "face_dribbler" : "capa",
                    "bola_x", bola.getPosicao().x(),
                    "bola_y", bola.getPosicao().y(),
                    "vel_bola", bola.getRapidez()));
        }
    }

    /**
     * Em que rapidez a bola volta a rolar depois de bater numa parede.
     *
     * <p>Um quique inverte a translacao mas nao o giro: a bola sai do contato
     * girando ao contrario do proprio movimento, e o atrito tem de parar e
     * reverter esse giro antes de ela voltar a rolar. Para uma esfera homogenea
     * que chegava rolando, o deslize termina em {@code v0 * (5e - 2) / 7}.
     *
     * <p>Com a restituicao padrao de 0,5 isso deixa apenas 7% da velocidade de
     * chegada, contra os 50% que sobrariam ignorando o giro -- era exatamente o
     * que fazia a bola parecer quicar demais.
     *
     * <p>Duas simplificacoes: abaixo de e = 0,4 a conta da negativo, ou seja o
     * giro venceria e a bola voltaria em direcao a parede; aqui ela apenas para.
     * E num quique de raspao, onde so uma componente inverte, a formula e
     * aproximacao, porque a componente tangencial segue rolando.
     */
    private static double rolamentoAposQuique(double velAntes, boolean rolava,
                                              double restituicao) {
        if (!rolava) return velAntes * restituicao * (5.0 / 7.0);
        return Math.max(0, velAntes * (5 * restituicao - 2) / 7.0);
    }

    /**
     * Quique nas paredes.
     *
     * <p>Simplificacao conhecida: a parede e tratada como infinitamente alta,
     * entao um chip nunca sai do campo. Enquanto nao houver arbitro para repor a
     * bola, deixa-la escapar significaria perde-la para sempre.
     */
    private void bolaParede(Mundo mundo) {
        Bola bola = mundo.getBola();
        Geometria g = mundo.getGeometria();
        double limX = g.limiteParedeX() - Bola.RAIO;
        double limY = g.limiteParedeY() - Bola.RAIO;

        Vec2 pos = bola.getPosicao();
        Vec2 vel = bola.getVelocidade();
        double velAntes = vel.norma();
        boolean rolava = !bola.estaDeslizando();
        double x = pos.x(), y = pos.y(), vx = vel.x(), vy = vel.y();
        String parede = null;

        if (x < -limX)     { x = -limX; vx =  Math.abs(vx) * p.restituicaoParede(); parede = "x_min"; }
        else if (x > limX) { x =  limX; vx = -Math.abs(vx) * p.restituicaoParede(); parede = "x_max"; }

        if (y < -limY)     { y = -limY; vy =  Math.abs(vy) * p.restituicaoParede();
                             parede = parede == null ? "y_min" : parede + "+y_min"; }
        else if (y > limY) { y =  limY; vy = -Math.abs(vy) * p.restituicaoParede();
                             parede = parede == null ? "y_max" : parede + "+y_max"; }

        if (parede == null) return;

        bola.setPosicao(new Vec2(x, y));
        bola.rebater(new Vec2(vx, vy), bola.getVz(),
                rolamentoAposQuique(velAntes, rolava, p.restituicaoParede()));
        mundo.registrar(TipoEvento.BOLA_PAREDE, Evento.dados(
                "parede", parede, "x", x, "y", y, "vel", bola.getRapidez()));
    }
}
