package teste;

import core.Angulo;
import core.Vec2;
import engine.FisicaBola;
import engine.Mundo;
import model.Bola;
import model.ParametrosFisica;
import model.Cor;
import model.Geometria;
import model.Robot;
import model.RobotCommand;
import model.RobotCommand;
import sim.Simulacao;

/**
 * Verificacao das invariantes do motor, sem framework de teste -- roda com
 * {@code java -cp out/production/SSL teste.Autoteste} e sai com codigo != 0 se
 * algo quebrar.
 *
 * <p>O caso que mais importa aqui e o de independencia de dt: era exatamente o
 * que a fisica antiga falhava, e e a condicao para o log ter algum valor.
 *
 * <p>Os robos sao acionados por {@link RobotCommand} direto, que e o unico jeito
 * de mover um robo agora que o simulador nao tem logica de jogo propria.
 */
public final class Autoteste {

    private static int falhas = 0;
    private static int total = 0;

    public static void main(String[] args) {
        bolaAtritoDuasFases();
        bolaIndependenteDeDt();
        bolaRespeitaTetoDeVelocidade();
        bolaQuicaDentroDosLimites();
        roboRespeitaAceleracaoMaxima();
        roboSaturaNaVelocidadeMaxima();
        comandoLocalViraMovimentoGlobal();
        roboRespeitaOmegaMaximo();
        colisaoRoboRoboConservaMomento();
        formacaoCabeNoCampo();
        formacaoEhCruzApontandoParaOCentro();

        System.out.printf("%n%d/%d verificacoes passaram%n", total - falhas, total);
        if (falhas > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ casos

    /** A distancia percorrida deve bater com a integral analitica das duas fases. */
    private static void bolaAtritoDuasFases() {
        ParametrosFisica p = ParametrosFisica.padrao();
        double v0 = 6000;
        double vRolagem = v0 * 5.0 / 7.0;
        double a1 = p.desaceleracaoDeslizamento();
        double a2 = p.desaceleracaoRolamento();

        double t1 = (v0 - vRolagem) / a1;
        double d1 = (v0 + vRolagem) / 2.0 * t1;
        double t2 = vRolagem / a2;
        double d2 = vRolagem / 2.0 * t2;
        double esperado = d1 + d2;

        double obtido = simularBolaAte0(v0, 1.0 / 240.0, p);
        aproximado("bola: distancia de parada bate com a analitica",
                obtido, esperado, esperado * 0.002);
    }

    /** Mesma trajetoria a 60 Hz e a 600 Hz -- o pecado original da versao antiga. */
    private static void bolaIndependenteDeDt() {
        ParametrosFisica p = ParametrosFisica.padrao();
        double a = simularBolaAte0(4000, 1.0 / 60.0, p);
        double b = simularBolaAte0(4000, 1.0 / 600.0, p);
        aproximado("bola: distancia igual a 60 Hz e a 600 Hz", a, b, b * 0.005);
    }

    private static void bolaRespeitaTetoDeVelocidade() {
        Bola bola = new Bola();
        bola.lancar(new Vec2(20000, 20000));
        verdadeiro("bola: velocidade saturada em " + Bola.VEL_MAX + " mm/s",
                bola.getRapidez() <= Bola.VEL_MAX + 1e-6);
    }

    private static void bolaQuicaDentroDosLimites() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 0, "B", 0); // sem robos: so bola e paredes
        Geometria g = sim.getMundo().getGeometria();
        sim.getMundo().getBola().lancar(new Vec2(6000, 3000));

        boolean dentro = true;
        for (int i = 0; i < 3000; i++) {
            sim.tick();
            Vec2 pos = sim.getMundo().getBola().getPosicao();
            if (Math.abs(pos.x()) > g.limiteParedeX() - Bola.RAIO + 0.1
                    || Math.abs(pos.y()) > g.limiteParedeY() - Bola.RAIO + 0.1) {
                dentro = false;
                break;
            }
        }
        verdadeiro("bola: nunca escapa das paredes ao longo de 50 s", dentro);
    }

    /** Um comando de 3 m/s nao pode virar 3 m/s no quadro seguinte. */
    private static void roboRespeitaAceleracaoMaxima() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 1, "B", 0);
        Robot r = sim.getMundo().getAzul().getRobo(0);
        r.setPosicao(Vec2.ZERO);
        r.setTheta(0);
        r.setComando(RobotCommand.mover(3000, 0, 0));

        sim.getMundo().passo(1.0 / 60.0);
        aproximado("robo: aceleracao saturada em acelMax*dt",
                r.getRapidez(), Robot.ACEL_MAX_PADRAO / 60.0, 1);

        // Meio segundo depois deve estar na metade do caminho, nao no teto.
        for (int i = 0; i < 29; i++) sim.getMundo().passo(1.0 / 60.0);
        aproximado("robo: rampa de aceleracao coerente apos 0,5 s",
                r.getRapidez(), Robot.ACEL_MAX_PADRAO * 0.5, 60);
    }

    private static void roboSaturaNaVelocidadeMaxima() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 1, "B", 0);
        Robot r = sim.getMundo().getAzul().getRobo(0);
        // Comeca no fundo do campo: 2 s a 3 m/s percorrem 4,5 m e a medicao
        // precisa acontecer antes de a parede zerar a velocidade.
        r.setPosicao(new Vec2(-4000, 0));
        r.setTheta(0);
        // Comando muito acima do teto: a saturacao tem de acontecer na fisica.
        r.setComando(RobotCommand.mover(99_000, 0, 0));

        for (int i = 0; i < 120; i++) sim.getMundo().passo(1.0 / 60.0);
        aproximado("robo: velocidade saturada em velMax",
                r.getRapidez(), Robot.VEL_MAX_PADRAO, 1);
    }

    /** O comando vem no referencial do robo; girar o robo tem de girar o movimento. */
    private static void comandoLocalViraMovimentoGlobal() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 1, "B", 0);
        Robot r = sim.getMundo().getAzul().getRobo(0);
        r.setPosicao(Vec2.ZERO);
        r.setTheta(Math.PI / 2);              // apontando para +y
        r.setComando(RobotCommand.mover(1000, 0, 0)); // "para frente"

        for (int i = 0; i < 60; i++) sim.getMundo().passo(1.0 / 60.0);

        verdadeiro("robo: comando frontal com theta=90 move em +y",
                r.getPosicao().y() > 500 && Math.abs(r.getPosicao().x()) < 20);
    }

    private static void roboRespeitaOmegaMaximo() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 1, "B", 0);
        Robot r = sim.getMundo().getAzul().getRobo(0);
        r.setComando(RobotCommand.mover(0, 0, 999));  // muito acima do teto

        for (int i = 0; i < 120; i++) sim.getMundo().passo(1.0 / 60.0);
        aproximado("robo: velocidade angular saturada em omegaMax",
                r.getOmega(), Robot.OMEGA_MAX_PADRAO, 0.01);
    }

    private static void colisaoRoboRoboConservaMomento() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 2, "B", 0);
        Robot a = sim.getMundo().getAzul().getRobo(0);
        Robot b = sim.getMundo().getAzul().getRobo(1);

        a.setPosicao(new Vec2(-100, 0));
        b.setPosicao(new Vec2(100, 0));
        a.setVelocidade(new Vec2(1000, 0));
        b.setVelocidade(new Vec2(-1000, 0));

        Vec2 antes = a.getVelocidade().mais(b.getVelocidade());
        sim.getMundo().passo(1.0 / 60.0);
        Vec2 depois = a.getVelocidade().mais(b.getVelocidade());

        aproximado("colisao robo-robo: momento conservado (massas iguais)",
                depois.distancia(antes), 0, 1e-6);
    }

    private static void formacaoCabeNoCampo() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 6, "B", 6);
        Geometria g = sim.getMundo().getGeometria();

        boolean ok = true;
        for (Robot r : sim.getMundo().getRobos()) {
            if (Math.abs(r.getPosicao().x()) > g.meioComprimento() - Robot.RAIO
                    || Math.abs(r.getPosicao().y()) > g.meiaLargura() - Robot.RAIO) {
                ok = false;
                System.out.println("    fora do campo: " + r);
            }
            // Cada equipe deve comecar no proprio lado.
            int lado = Geometria.ladoDefendido(r.getCor());
            if (Math.signum(r.getPosicao().x()) != lado && r.getPosicao().x() != 0) ok = false;
        }
        verdadeiro("formacao: 6x6 cabe no campo e cada equipe no seu lado", ok);
    }

    /** Cada robo encara (0,0) e cada um cai sobre um dos dois eixos da cruz. */
    private static void formacaoEhCruzApontandoParaOCentro() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 6, "B", 6);

        boolean encarando = true, naCruz = true;
        double centroX = Geometria.divisaoB().meioComprimento() * 0.49;

        for (Robot r : sim.getMundo().getRobos()) {
            Vec2 p = r.getPosicao();
            // O angulo tem de ser exatamente o da direcao ate a origem.
            if (Math.abs(Angulo.diferenca(p.negado().angulo(), r.getTheta())) > 1e-9) {
                encarando = false;
            }
            // Ou esta no braco horizontal (y = 0) ou no vertical (|x| = centro).
            boolean bracoHorizontal = Math.abs(p.y()) < 1e-6;
            boolean bracoVertical = Math.abs(Math.abs(p.x()) - centroX) < 1e-6;
            if (!bracoHorizontal && !bracoVertical) naCruz = false;
        }

        verdadeiro("formacao: todo robo encara o centro do campo", encarando);
        verdadeiro("formacao: todo robo cai sobre um braco da cruz", naCruz);
    }

    // ------------------------------------------------------------------ apoio

    private static double simularBolaAte0(double v0, double dt, ParametrosFisica p) {
        Bola bola = new Bola();
        bola.lancar(new Vec2(v0, 0));
        FisicaBola fisica = new FisicaBola(p);
        int limite = (int) (60 / dt);
        for (int i = 0; i < limite && bola.getRapidez() > 0; i++) fisica.integrar(bola, dt);
        return bola.getPosicao().x();
    }

    private static void verdadeiro(String nome, boolean condicao) {
        total++;
        if (condicao) {
            System.out.println("  ok    " + nome);
        } else {
            falhas++;
            System.out.println("  FALHA " + nome);
        }
    }

    private static void aproximado(String nome, double obtido, double esperado, double tol) {
        total++;
        if (Math.abs(obtido - esperado) <= tol) {
            System.out.printf("  ok    %s%n", nome);
        } else {
            falhas++;
            System.out.printf("  FALHA %s  (obtido %.4f, esperado %.4f +- %.4f)%n",
                    nome, obtido, esperado, tol);
        }
    }
}
