package app.fisica;

import core.Vec2;
import engine.FisicaBola;
import engine.Mundo;
import model.Bola;
import model.Cor;
import model.Geometria;
import model.ParametrosFisica;
import model.Robot;
import model.RobotCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Os experimentos que dao significado a cada parametro de fisica.
 *
 * <p>Rodam com passo menor que o da simulacao (1/240 s) e guardam uma amostra a
 * cada quatro, porque o objetivo aqui e a medida ficar precisa, nao acompanhar
 * o relogio: sao algumas centenas de passos com um ou dois corpos, entao roda em
 * microssegundos e pode ser refeito a cada movimento do slider.
 */
public final class Ensaios {

    private static final double DT = 1.0 / 240.0;
    private static final int SUBAMOSTRA = 4;
    private static final double DT_AMOSTRA = DT * SUBAMOSTRA;

    private Ensaios() {}

    public static List<Ensaio> todos() {
        return List.of(
                atritoRolamento(), atritoDeslizamento(),
                restituicaoParede(), restituicaoRobo(), restituicaoRoboRobo(),
                restituicaoQuique(), atritoQuique());
    }

    // ------------------------------------------------------------- no gramado

    private static Ensaio atritoRolamento() {
        return new Ensaio("Atrito de rolamento",
                "bola solta a 3 m/s: ate onde ela rola",
                "m", Vista.PLANO, new double[]{0, 13000, -700, 700}, Double.NaN, true,
                0.02, 0.20, "%.3f",
                ParametrosFisica::atritoRolamento,
                (base, v) -> new ParametrosFisica(base.gravidade(), base.atritoDeslizamento(),
                        v, base.restituicaoParede(), base.restituicaoRobo(),
                        base.restituicaoRoboRobo(), base.atritoTangencialRobo(),
                        base.velocidadeMinimaBola(), base.alcanceDribbler(),
                        base.forcaDribbler(), base.restituicaoQuique(), base.atritoQuique()),
                p -> {
                    Bola b = new Bola();
                    b.lancar(new Vec2(3000, 0));
                    return rodarSoBola(b, p, 16.0, () -> b.getPosicao().x() / 1000.0);
                });
    }

    private static Ensaio atritoDeslizamento() {
        return new Ensaio("Atrito de deslizamento",
                "chute a 6 m/s: quanto desliza antes de rolar",
                "m", Vista.PLANO, new double[]{0, 4000, -700, 700}, Double.NaN, true,
                0.15, 0.60, "%.2f",
                ParametrosFisica::atritoDeslizamento,
                (base, v) -> new ParametrosFisica(base.gravidade(), v,
                        base.atritoRolamento(), base.restituicaoParede(), base.restituicaoRobo(),
                        base.restituicaoRoboRobo(), base.atritoTangencialRobo(),
                        base.velocidadeMinimaBola(), base.alcanceDribbler(),
                        base.forcaDribbler(), base.restituicaoQuique(), base.atritoQuique()),
                p -> {
                    Bola b = new Bola();
                    b.lancar(new Vec2(6000, 0));
                    FisicaBola f = new FisicaBola(p);
                    List<Amostra> as = new ArrayList<>();
                    double fimDoDeslize = 0;
                    int extras = 0;
                    // Para logo depois da transicao: a bola ainda rolaria por mais
                    // de dez metros, e o trecho que este ensaio mede viraria um
                    // ponto na faixa.
                    for (int i = 0; i < (int) (6.0 / DT); i++) {
                        boolean deslizava = b.estaDeslizando();
                        f.integrar(b, DT);
                        if (deslizava && !b.estaDeslizando()) fimDoDeslize = b.getPosicao().x();
                        if (i % SUBAMOSTRA == 0) as.add(amostra(b));
                        if (fimDoDeslize > 0 && ++extras > (int) (0.5 / DT)) break;
                    }
                    return new Trajetoria(as, DT_AMOSTRA, fimDoDeslize / 1000.0);
                });
    }

    // ---------------------------------------------------------------- quiques

    private static Ensaio restituicaoParede() {
        double xParede = Geometria.divisaoB().limiteParedeX() - Bola.RAIO;
        return new Ensaio("Quique na parede",
                "bola a 4 m/s na parede: quanto ela volta",
                "m", Vista.PLANO, new double[]{2000, xParede + 200, -700, 700}, xParede, true,
                0.0, 1.0, "%.2f",
                ParametrosFisica::restituicaoParede,
                (base, v) -> new ParametrosFisica(base.gravidade(), base.atritoDeslizamento(),
                        base.atritoRolamento(), v, base.restituicaoRobo(),
                        base.restituicaoRoboRobo(), base.atritoTangencialRobo(),
                        base.velocidadeMinimaBola(), base.alcanceDribbler(),
                        base.forcaDribbler(), base.restituicaoQuique(), base.atritoQuique()),
                p -> {
                    Mundo m = mundo(p, 0, 0);
                    m.getBola().reposicionar(new Vec2(2200, 0));
                    m.getBola().lancar(new Vec2(4000, 0));
                    // Medir a velocidade no instante do quique esconderia o efeito
                    // do giro, que so aparece no deslize seguinte. O que se percebe
                    // e o quanto ela volta.
                    return rodarMundo(m, 8.0, distanciaDeVolta(xParede));
                });
    }

    private static Ensaio restituicaoRobo() {
        return new Ensaio("Quique no robo",
                "bola a 4 m/s na casca: velocidade de volta",
                "m/s", Vista.PLANO, new double[]{-1600, 400, -700, 700}, Double.NaN, true,
                0.0, 1.0, "%.2f",
                ParametrosFisica::restituicaoRobo,
                (base, v) -> new ParametrosFisica(base.gravidade(), base.atritoDeslizamento(),
                        base.atritoRolamento(), base.restituicaoParede(), v,
                        base.restituicaoRoboRobo(), base.atritoTangencialRobo(),
                        base.velocidadeMinimaBola(), base.alcanceDribbler(),
                        base.forcaDribbler(), base.restituicaoQuique(), base.atritoQuique()),
                p -> {
                    Mundo m = mundo(p, 1, 0);
                    Robot r = m.getAzul().getRobo(0);
                    r.setPosicao(Vec2.ZERO);
                    r.setTheta(0);   // casca voltada para a bola que vem de -x
                    m.getBola().reposicionar(new Vec2(-1400, 0));
                    m.getBola().lancar(new Vec2(4000, 0));
                    return rodarMundo(m, 2.0, velocidadeDeVolta());
                });
    }

    private static Ensaio restituicaoRoboRobo() {
        return new Ensaio("Quique entre robos",
                "choque frontal: velocidade de separacao",
                "m/s", Vista.PLANO, new double[]{-900, 900, -400, 400}, Double.NaN, false,
                0.0, 1.0, "%.2f",
                ParametrosFisica::restituicaoRoboRobo,
                (base, v) -> new ParametrosFisica(base.gravidade(), base.atritoDeslizamento(),
                        base.atritoRolamento(), base.restituicaoParede(), base.restituicaoRobo(),
                        v, base.atritoTangencialRobo(), base.velocidadeMinimaBola(),
                        base.alcanceDribbler(), base.forcaDribbler(),
                        base.restituicaoQuique(), base.atritoQuique()),
                p -> {
                    Mundo m = mundo(p, 1, 1);
                    Robot a = m.getAzul().getRobo(0);
                    Robot b = m.getAmarelo().getRobo(0);
                    // Perto o bastante para o choque acontecer: com comando parado
                    // eles freiam a 3 m/s^2, e mais afastados parariam antes de
                    // encostar -- foi o que fez a primeira versao deste ensaio medir
                    // o mesmo numero para qualquer restituicao.
                    a.setPosicao(new Vec2(-300, 0));
                    a.setTheta(0);
                    a.setVelocidade(new Vec2(1500, 0));
                    b.setPosicao(new Vec2(300, 0));
                    b.setTheta(Math.PI);
                    b.setVelocidade(new Vec2(-1500, 0));
                    a.setComando(RobotCommand.PARADO);
                    b.setComando(RobotCommand.PARADO);
                    m.getBola().reposicionar(new Vec2(0, 2000)); // fora do caminho

                    return rodarMundo(m, 1.5, velocidadeDeSeparacao(a, b));
                });
    }

    // -------------------------------------------------------------- pelo alto

    private static Ensaio restituicaoQuique() {
        return new Ensaio("Restituicao vertical",
                "bola largada de 1 m: altura do primeiro quique",
                "mm", Vista.PERFIL, new double[]{0, 3200, 0, 1100}, Double.NaN, true,
                0.0, 0.95, "%.2f",
                ParametrosFisica::restituicaoQuique,
                (base, v) -> new ParametrosFisica(base.gravidade(), base.atritoDeslizamento(),
                        base.atritoRolamento(), base.restituicaoParede(), base.restituicaoRobo(),
                        base.restituicaoRoboRobo(), base.atritoTangencialRobo(),
                        base.velocidadeMinimaBola(), base.alcanceDribbler(),
                        base.forcaDribbler(), v, base.atritoQuique()),
                p -> {
                    Bola b = new Bola();
                    // Um empurraozinho horizontal so para os quiques nao se
                    // empilharem no mesmo ponto e darem para ver no desenho.
                    b.reposicionar(new Vec2(0, 0), 1000, new Vec2(700, 0), 0);
                    FisicaBola f = new FisicaBola(p);
                    List<Amostra> as = new ArrayList<>();
                    boolean tocou = false;
                    double primeiroQuique = 0;
                    for (int i = 0; i < (int) (4.0 / DT); i++) {
                        double antes = b.getZ();
                        f.integrar(b, DT);
                        if (!tocou && b.getZ() < antes && b.getVz() > 0) tocou = true;
                        if (tocou) primeiroQuique = Math.max(primeiroQuique, b.getZ());
                        if (i % SUBAMOSTRA == 0) as.add(amostra(b));
                    }
                    return new Trajetoria(as, DT_AMOSTRA, primeiroQuique);
                });
    }

    private static Ensaio atritoQuique() {
        return new Ensaio("Atrito do quique",
                "chip a 5 m/s e 45 graus: alcance total",
                "m", Vista.PERFIL, new double[]{0, 6000, 0, 800}, Double.NaN, true,
                0.30, 1.0, "%.2f",
                ParametrosFisica::atritoQuique,
                (base, v) -> new ParametrosFisica(base.gravidade(), base.atritoDeslizamento(),
                        base.atritoRolamento(), base.restituicaoParede(), base.restituicaoRobo(),
                        base.restituicaoRoboRobo(), base.atritoTangencialRobo(),
                        base.velocidadeMinimaBola(), base.alcanceDribbler(),
                        base.forcaDribbler(), base.restituicaoQuique(), v),
                p -> {
                    double c = Math.cos(Math.PI / 4);
                    Bola b = new Bola();
                    b.lancar(new Vec2(5000 * c, 0), 5000 * c);
                    return rodarSoBola(b, p, 8.0, () -> b.getPosicao().x() / 1000.0);
                });
    }

    // ------------------------------------------------------------------ apoio

    private static Mundo mundo(ParametrosFisica p, int azuis, int amarelos) {
        Mundo m = new Mundo(Geometria.divisaoB(), p);
        m.inicializarPartida("a", azuis, "b", amarelos);
        for (Robot r : m.getRobos()) r.setComando(RobotCommand.PARADO);
        m.drenarEventos();
        return m;
    }

    private static Amostra amostra(Bola b) {
        return new Amostra(b.getPosicao(), b.getZ(), Amostra.SEM_ROBOS);
    }

    private static Trajetoria rodarSoBola(Bola b, ParametrosFisica p, double duracao,
                                          java.util.function.DoubleSupplier medida) {
        FisicaBola f = new FisicaBola(p);
        List<Amostra> as = new ArrayList<>();
        for (int i = 0; i < (int) (duracao / DT); i++) {
            f.integrar(b, DT);
            if (i % SUBAMOSTRA == 0) as.add(amostra(b));
            if (b.getRapidez() == 0 && !b.estaNoAr()) break;
        }
        return new Trajetoria(as, DT_AMOSTRA, medida.getAsDouble());
    }

    private static Trajetoria rodarMundo(Mundo m, double duracao, Medidor medidor) {
        List<Amostra> as = new ArrayList<>();
        for (int i = 0; i < (int) (duracao / DT); i++) {
            m.passo(DT);
            m.drenarEventos();
            medidor.observar(m);
            if (i % SUBAMOSTRA == 0) {
                Vec2[] robos = new Vec2[m.getRobos().size()];
                for (int k = 0; k < robos.length; k++) robos[k] = m.getRobos().get(k).getPosicao();
                as.add(new Amostra(m.getBola().getPosicao(), m.getBola().getZ(), robos));
            }
        }
        return new Trajetoria(as, DT_AMOSTRA, medidor.valor());
    }

    /** Acompanha o ensaio quadro a quadro e resume num numero no fim. */
    private interface Medidor {
        void observar(Mundo m);
        double valor();
    }

    /**
     * Velocidade da bola logo depois de ela inverter o sentido, em m/s.
     *
     * <p>Tem de ser amostrada NO instante do quique. Medir no fim do ensaio
     * pegaria a velocidade ja comida pelo atrito do caminho de volta, e o numero
     * responderia a outra pergunta.
     */
    private static Medidor velocidadeDeVolta() {
        return new Medidor() {
            private double capturado;
            private double vxAnterior;
            private boolean jaCapturou;

            @Override
            public void observar(Mundo m) {
                double vx = m.getBola().getVelocidade().x();
                if (!jaCapturou && vxAnterior > 0 && vx < 0) {
                    capturado = Math.abs(vx) / 1000.0;
                    jaCapturou = true;
                }
                vxAnterior = vx;
            }

            @Override
            public double valor() { return capturado; }
        };
    }

    /** Quanto a bola recua da parede antes de parar, em metros. */
    private static Medidor distanciaDeVolta(double xParede) {
        return new Medidor() {
            private double maisLonge;

            @Override
            public void observar(Mundo m) {
                if (m.getBola().getVelocidade().x() < 0 || maisLonge > 0) {
                    maisLonge = Math.max(maisLonge, xParede - m.getBola().getPosicao().x());
                }
            }

            @Override
            public double valor() { return maisLonge / 1000.0; }
        };
    }

    /** Velocidade com que dois robos se afastam logo apos o choque, em m/s. */
    private static Medidor velocidadeDeSeparacao(Robot a, Robot b) {
        return new Medidor() {
            private double capturado;
            private boolean jaCapturou;

            @Override
            public void observar(Mundo m) {
                // Componente radial: negativa enquanto se aproximam.
                double relativa = b.getVelocidade().x() - a.getVelocidade().x();
                if (!jaCapturou && relativa > 0) {
                    capturado = relativa / 1000.0;
                    jaCapturou = true;
                }
            }

            @Override
            public double valor() { return capturado; }
        };
    }
}
