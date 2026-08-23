package engine;

import core.Caixa;
import core.Vec2;
import model.Bola;
import model.ParametrosFisica;
import model.Geometria;
import model.ParedeDoGol;
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
 *   <li>O gol deixou de ser enfeite. Antes a bola atravessava o gol inteiro e ia
 *       quicar na parede da faixa externa, 300 mm atras da linha de fundo; agora
 *       ha tres paredes de verdade por gol, e a bola que entra fica la dentro.
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
        roboGol(mundo);
        roboRobo(mundo);
        atuadores(mundo);
        bolaRobo(mundo);
        bolaGol(mundo);
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


    /**
     * Robos contra as paredes do gol.
     *
     * <p>Resolucao estatica, sem varredura, e isso e seguro aqui: a 3 m/s um robo
     * anda 50 mm por quadro, e a barreira efetiva do fundo do gol e a espessura
     * da parede mais dois raios de robo, 200 mm. Nao ha passo que a atravesse,
     * entao basta desfazer a sobreposicao. A bola precisa de mais cuidado.
     *
     * <p>Como na parede externa, o contato nao devolve o robo: ele so para. Um
     * robo de SSL encosta na parede e fica encostado, nao ricocheteia.
     */
    private void roboGol(Mundo mundo) {
        for (ParedeDoGol parede : mundo.getParedesDosGols()) {
            for (Robot r : mundo.getRobos()) {
                Contato c = contatoEstatico(r.getPosicao(), Robot.RAIO, parede.caixa());
                if (c == null) continue;

                r.setPosicao(c.posicao());
                double velNormal = r.getVelocidade().escalar(c.normal());
                if (velNormal < 0) {
                    r.setVelocidade(r.getVelocidade().menos(c.normal().escala(velNormal)));
                }
            }
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
     * Quique nas paredes do gol.
     *
     * <p>O gol e o unico obstaculo do campo fino o bastante para a bola pular por
     * cima dele em um passo: a parede tem 20 mm, a bola tem 43 mm de diametro e a
     * 60 Hz ela percorre ate 108 mm por quadro. Resolver por sobreposicao, como
     * se faz com o robo e com a parede externa, deixaria um chute forte sair pelo
     * fundo do gol -- em um quadro a bola estaria dentro, no seguinte ja atras da
     * estrutura, sem nunca ter tocado nela. Por isso o teste e VARRIDO: o que se
     * confronta com a parede e o segmento percorrido no passo, nao o ponto de
     * chegada.
     *
     * <p>So o primeiro contato do passo e resolvido, e o resto do deslocamento e
     * descartado -- mesma escolha de {@link #bolaParede}. Encostar em duas
     * paredes no mesmo quadro so acontece no canto interno do gol, e ai o segundo
     * contato aparece no quadro seguinte.
     *
     * <p>Acima de {@code golAltura} nao ha gol nenhum: e a mesma regra do teto do
     * robo, e e ela que faz um chip por cima da trave continuar valendo. A parede
     * externa e que segue infinitamente alta, entao a bola nao se perde.
     */
    private void bolaGol(Mundo mundo) {
        Bola bola = mundo.getBola();
        Geometria g = mundo.getGeometria();
        if (bola.getZ() >= g.golAltura()) return;

        Contato primeiro = null;
        ParedeDoGol atingida = null;
        for (ParedeDoGol parede : mundo.getParedesDosGols()) {
            Contato c = contatoVarrido(bola.getPosicaoAnterior(), bola.getPosicao(),
                    Bola.RAIO, parede.caixa());
            if (c != null && (primeiro == null || c.t() < primeiro.t())) {
                primeiro = c;
                atingida = parede;
            }
        }
        if (primeiro == null) return;

        Vec2 n = primeiro.normal();
        Vec2 vel = velocidadeNoContato(bola, primeiro.t());
        bola.setPosicao(primeiro.posicao());

        double velNormal = vel.escalar(n);
        if (velNormal >= 0) return; // rocou de saida: separa, mas nao ha quique

        double velAntes = vel.norma();
        boolean rolava = !bola.estaDeslizando();
        double e = p.restituicaoParede();

        // Inverte so a componente normal; a tangencial atravessa intacta, como na
        // parede externa, onde o quique em x nao mexe em vy.
        Vec2 nova = vel.menos(n.escala(velNormal * (1 + e)));
        bola.rebater(nova, bola.getVz(), rolamentoAposQuique(velAntes, rolava, e));

        mundo.registrar(TipoEvento.BOLA_PAREDE, Evento.dados(
                "parede", atingida.nome(),
                "x", bola.getPosicao().x(), "y", bola.getPosicao().y(),
                "vel", bola.getRapidez()));
    }

    /**
     * Velocidade da bola no INSTANTE do toque, e nao no fim do passo.
     *
     * <p>O integrador cobrou atrito do passo inteiro, inclusive do trecho depois
     * do ponto de contato -- trecho que a bola nunca percorreu, porque a parede a
     * interrompeu. Devolver esse pedaco por {@code v^2 = v1^2 + 2*a*s} e o que faz
     * o quique no gol nao depender da taxa: sem a correcao a bola volta 444 mm a
     * 60 Hz contra 385 mm a 2000 Hz, uma diferenca de 15% que vem so do tamanho do
     * passo. E o mesmo motivo que leva {@link FisicaBola} a resolver o toque do
     * chip no chao dentro do passo em vez de arredondar para a borda do quadro.
     *
     * <p>A parede externa ainda arredonda: la o quique e resolvido pela posicao de
     * chegada, com o erro de dt que isso carrega. Sao codigos separados de
     * proposito -- mexer no quique da parede mudaria a fisica ja gravada nos logs
     * antigos.
     */
    private Vec2 velocidadeNoContato(Bola bola, double t) {
        Vec2 vel = bola.getVelocidade();
        // No ar nao ha atrito horizontal para devolver.
        if (bola.estaNoAr() || t >= 1) return vel;

        double sobra = bola.getPosicao().distancia(bola.getPosicaoAnterior()) * (1 - t);
        double desaceleracao = bola.estaDeslizando()
                ? p.desaceleracaoDeslizamento()
                : p.desaceleracaoRolamento();
        return vel.comNorma(Math.sqrt(vel.normaQuad() + 2 * desaceleracao * sobra));
    }

    /**
     * Contato de um circulo com uma caixa parada, sabendo de onde ele veio.
     *
     * @param posicao centro ja corrigido, encostado na face e fora dela
     * @param normal  direcao de saida, unitaria e alinhada a um eixo quando a face
     *                e plana
     * @param t       fracao do passo em que o contato aconteceu; 1 quando o
     *                circulo simplesmente terminou o passo sobreposto
     */
    private record Contato(Vec2 posicao, Vec2 normal, double t) {}

    /**
     * Primeiro contato do segmento {@code origem -> destino} com uma caixa.
     *
     * <p>Metodo dos slabs sobre a caixa dilatada pelo raio: pela soma de
     * Minkowski, um circulo contra um retangulo vira um PONTO contra o retangulo
     * crescido. O eixo que fecha o intervalo por ultimo e a face atingida, e dele
     * sai a normal. O custo dessa dilatacao e a mesma quina viva ja assumida na
     * boca do dribbler -- rocar o canto do poste resolve como se fosse a face.
     *
     * <p>Sem cruzamento, ainda resta o caso do circulo que terminou o passo dentro
     * da parede sem nunca ter atravessado a fronteira: e o que acontece quando
     * alguem o colocou ali, um dribbler empurrando a bola contra a trave. Ai vale
     * a resolucao estatica.
     */
    private static Contato contatoVarrido(Vec2 origem, Vec2 destino, double raio,
                                          Caixa caixa) {
        Caixa dilatada = caixa.dilatada(raio);
        Vec2 d = destino.menos(origem);
        double tEntrada = 0, tSaida = 1;
        Vec2 normal = null;

        if (Math.abs(d.x()) < 1e-12) {
            if (origem.x() < dilatada.xMin() || origem.x() > dilatada.xMax()) return null;
        } else {
            double t1 = (dilatada.xMin() - origem.x()) / d.x();
            double t2 = (dilatada.xMax() - origem.x()) / d.x();
            if (Math.min(t1, t2) > tEntrada) {
                tEntrada = Math.min(t1, t2);
                normal = new Vec2(d.x() > 0 ? -1 : 1, 0);
            }
            tSaida = Math.min(tSaida, Math.max(t1, t2));
        }

        if (Math.abs(d.y()) < 1e-12) {
            if (origem.y() < dilatada.yMin() || origem.y() > dilatada.yMax()) return null;
        } else {
            double t1 = (dilatada.yMin() - origem.y()) / d.y();
            double t2 = (dilatada.yMax() - origem.y()) / d.y();
            if (Math.min(t1, t2) > tEntrada) {
                tEntrada = Math.min(t1, t2);
                normal = new Vec2(0, d.y() > 0 ? -1 : 1);
            }
            tSaida = Math.min(tSaida, Math.max(t1, t2));
        }

        if (normal == null || tEntrada > tSaida) {
            return contatoEstatico(destino, raio, caixa);
        }
        Vec2 toque = origem.mais(d.escala(tEntrada));
        return new Contato(toque.mais(normal.escala(EPS)), normal, tEntrada);
    }

    /**
     * Contato de um circulo parado sobre uma caixa: para onde ele tem de sair.
     *
     * <p>Com o centro FORA da caixa a normal e a direcao do ponto mais proximo ate
     * ele, o que arredonda os cantos corretamente. Com o centro DENTRO nao existe
     * essa direcao, entao a saida e pela face de menor penetracao -- numa parede
     * fina isso e sempre a face por onde ele entrou.
     */
    private static Contato contatoEstatico(Vec2 centro, double raio, Caixa caixa) {
        Vec2 maisProximo = caixa.pontoMaisProximo(centro);
        Vec2 fora = centro.menos(maisProximo);
        double dist = fora.norma();

        if (dist > 1e-9) {
            if (dist >= raio) return null;
            Vec2 n = fora.escala(1.0 / dist);
            return new Contato(maisProximo.mais(n.escala(raio + EPS)), n, 1);
        }

        double ateXMin = centro.x() - caixa.xMin(), ateXMax = caixa.xMax() - centro.x();
        double ateYMin = centro.y() - caixa.yMin(), ateYMax = caixa.yMax() - centro.y();
        double menor = Math.min(Math.min(ateXMin, ateXMax), Math.min(ateYMin, ateYMax));

        if (menor == ateXMin) {
            return new Contato(new Vec2(caixa.xMin() - raio - EPS, centro.y()),
                    new Vec2(-1, 0), 1);
        }
        if (menor == ateXMax) {
            return new Contato(new Vec2(caixa.xMax() + raio + EPS, centro.y()),
                    new Vec2(1, 0), 1);
        }
        if (menor == ateYMin) {
            return new Contato(new Vec2(centro.x(), caixa.yMin() - raio - EPS),
                    new Vec2(0, -1), 1);
        }
        return new Contato(new Vec2(centro.x(), caixa.yMax() + raio + EPS),
                new Vec2(0, 1), 1);
    }

    /**
     * Quique nas paredes.
     *
     * <p>Simplificacao conhecida: a parede e tratada como infinitamente alta,
     * entao um chip nunca sai do campo. Enquanto nao houver arbitro para repor a
     * bola, deixa-la escapar significaria perde-la para sempre. O gol, ao
     * contrario, tem altura de verdade -- por cima dele a bola passa.
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
