package teste;

import core.Angulo;
import demo.Cenarios;
import demo.ExecutorDeCenario;
import demo.Roteiro;
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
        chipAtingeOApiceAnalitico();
        chipCaiNaDistanciaPrevista();
        chipIndependenteDeDt();
        chipPassaPorCimaDoRobo();
        chipBaixoAindaColide();
        chipAssentaDepoisDeQuicar();
        dribblerNaoPegaBolaNoAr();
        cenarioChuteChegaAoGol();
        cenarioChipPassaPorCimaEEhRecebido();
        cenarioConducaoMantemAPosse();
        semRollerABolaFicaParaTras();
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

    // --------------------------------------------------------------- cenarios

    /** Roda um cenario por {@code segundos} e devolve a simulacao para inspecao. */
    private static Simulacao rodarCenario(Roteiro roteiro, double segundos) {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 6, "B", 6);
        ExecutorDeCenario executor =
                new ExecutorDeCenario(sim, sim.getControladorExterno());
        executor.selecionar(roteiro);
        sim.setAntesTick(() -> executor.tick(sim.getClock().getDt()));

        long passos = Math.round(segundos / sim.getClock().getDt());
        for (long i = 0; i < passos; i++) sim.tick();
        return sim;
    }

    private static void cenarioChuteChegaAoGol() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 6, "B", 6);
        ExecutorDeCenario executor =
                new ExecutorDeCenario(sim, sim.getControladorExterno());
        executor.selecionar(Cenarios.chuteNoGol());
        sim.setAntesTick(() -> executor.tick(sim.getClock().getDt()));

        double xMaximo = -9999;
        for (int i = 0; i < 60 * 4; i++) {
            sim.tick();
            xMaximo = Math.max(xMaximo, sim.getMundo().getBola().getPosicao().x());
        }
        verdadeiro("cenario chute no gol: a bola cruza a linha de fundo adversaria",
                xMaximo > sim.getMundo().getGeometria().meioComprimento());
    }

    /** O chip tem de subir acima do robo E terminar dominado pelo receptor. */
    private static void cenarioChipPassaPorCimaEEhRecebido() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 6, "B", 6);
        ExecutorDeCenario executor =
                new ExecutorDeCenario(sim, sim.getControladorExterno());
        executor.selecionar(Cenarios.passeComChip(
                sim.getMundo().getParametros().gravidade()));
        sim.setAntesTick(() -> executor.tick(sim.getClock().getDt()));

        double alturaMaxima = 0;
        boolean recebeu = false;
        for (int i = 0; i < 60 * 5; i++) {
            sim.tick();
            alturaMaxima = Math.max(alturaMaxima, sim.getMundo().getBola().getZ());
            if (sim.getMundo().getAzul().getRobo(1).temBolaNoDribbler()) recebeu = true;
        }
        verdadeiro("cenario chip: sobe bem acima do teto do robo",
                alturaMaxima > Robot.ALTURA * 2);
        verdadeiro("cenario chip: o receptor domina a bola", recebeu);
    }

    /**
     * A conducao tem de sobreviver a freada seca e a marcha a re.
     *
     * <p>Andar para frente ou em circulo nao prova nada: a bola acompanha por
     * inercia mesmo sem dribbler. O que separa "segurando" de "empurrando" e
     * parar de repente e depois inverter o sentido.
     */
    private static void cenarioConducaoMantemAPosse() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 6, "B", 6);
        ExecutorDeCenario executor =
                new ExecutorDeCenario(sim, sim.getControladorExterno());
        executor.selecionar(Cenarios.conducaoComRoller());
        sim.setAntesTick(() -> executor.tick(sim.getClock().getDt()));

        Robot condutor = sim.getMundo().getAzul().getRobo(0);
        boolean perdeu = false;
        double maiorAfastamento = 0;

        // Ate 8,4 s: toda a sequencia menos a soltura deliberada aos 9,0 s.
        for (int i = 0; i < (int) (8.4 * 60); i++) {
            sim.tick();
            if (sim.getClock().getTempo() < 0.6) continue; // deixa engatar
            if (!condutor.temBolaNoDribbler()) perdeu = true;
            maiorAfastamento = Math.max(maiorAfastamento,
                    sim.getMundo().getBola().getPosicao().distancia(condutor.pontoDribbler()));
        }

        verdadeiro("cenario conducao: nao perde a bola em nenhuma manobra", !perdeu);
        aproximado("cenario conducao: a bola nunca se solta da boca",
                maiorAfastamento, Bola.RAIO, 5);
    }

    /**
     * O contraste que da sentido ao cenario: os mesmos movimentos sem o roller.
     *
     * <p>Se a bola acompanhasse do mesmo jeito com o dribbler desligado, o
     * cenario nao estaria demonstrando coisa alguma.
     */
    private static void semRollerABolaFicaParaTras() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 1, "B", 0);
        Robot r = sim.getMundo().getAzul().getRobo(0);
        r.setPosicao(new Vec2(-3000, 0));
        r.setTheta(0);
        sim.getMundo().reposicionarBola(
                r.pontoDribbler().mais(new Vec2(Bola.RAIO, 0)));

        // Mesma abertura do cenario: acelera 1,4 s e depois da re, sem dribbler.
        r.setComando(RobotCommand.mover(1500, 0, 0));
        for (int i = 0; i < (int) (1.4 * 60); i++) sim.getMundo().passo(1.0 / 60.0);
        r.setComando(RobotCommand.mover(-1200, 0, 0));
        for (int i = 0; i < (int) (1.4 * 60); i++) sim.getMundo().passo(1.0 / 60.0);

        // Com o roller a bola fica a 22 mm da boca; sem ele, perto de 1 m. O
        // limiar e folgado de proposito: o que importa e a ordem de grandeza,
        // nao o valor exato, que depende de quanto atrito a bola pega no caminho.
        double distancia = sim.getMundo().getBola().getPosicao().distancia(r.pontoDribbler());
        verdadeiro("sem roller: a mesma manobra deixa a bola para tras",
                distancia > 500);
    }

    // ------------------------------------------------------------------- chip

    /** Altura maxima tem de bater com vz0^2 / 2g. */
    private static void chipAtingeOApiceAnalitico() {
        double vz0 = velocidadeVertical(6500, 45);
        double esperado = vz0 * vz0 / (2 * ParametrosFisica.padrao().gravidade());
        aproximado("chip: apice bate com a analitica",
                voo(6500, 45, 1.0 / 600.0).apice(), esperado, esperado * 0.01);
    }

    /** Alcance ate o primeiro toque: sem arrasto, e simplesmente vh * 2*vz0/g. */
    private static void chipCaiNaDistanciaPrevista() {
        double g = ParametrosFisica.padrao().gravidade();
        double vz0 = velocidadeVertical(6500, 45);
        double vh = velocidadeHorizontal(6500, 45);
        double esperado = vh * (2 * vz0 / g);
        aproximado("chip: alcance ate o primeiro toque bate com a analitica",
                voo(6500, 45, 1.0 / 600.0).primeiroToque(), esperado, esperado * 0.01);
    }

    /**
     * O quique tem de cair no mesmo lugar a 60 Hz e a 600 Hz.
     *
     * <p>E o que justifica resolver o instante do toque dentro do passo: grudar
     * o quique na borda do quadro faria o alcance depender da taxa.
     */
    private static void chipIndependenteDeDt() {
        double a = voo(6500, 45, 1.0 / 60.0).primeiroToque();
        double b = voo(6500, 45, 1.0 / 600.0).primeiroToque();
        aproximado("chip: mesmo alcance a 60 Hz e a 600 Hz", a, b, b * 0.01);
    }

    private static void chipPassaPorCimaDoRobo() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 1, "B", 0);
        Robot r = sim.getMundo().getAzul().getRobo(0);
        r.setPosicao(new Vec2(1500, 0));
        r.setTheta(Math.PI);

        Bola bola = sim.getMundo().getBola();
        bola.reposicionar(Vec2.ZERO, 0, new Vec2(velocidadeHorizontal(6500, 45), 0),
                velocidadeVertical(6500, 45));

        for (int i = 0; i < 40; i++) sim.getMundo().passo(1.0 / 60.0);

        verdadeiro("chip: passa por cima do robo em vez de bater nele",
                bola.getPosicao().x() > 2500);
    }

    private static void chipBaixoAindaColide() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 1, "B", 0);
        Robot r = sim.getMundo().getAzul().getRobo(0);
        r.setPosicao(new Vec2(600, 0));
        r.setTheta(Math.PI);

        Bola bola = sim.getMundo().getBola();
        // Elevacao pequena: a bola nem chega aos 150 mm do teto do robo.
        bola.reposicionar(Vec2.ZERO, 0, new Vec2(3000, 0), 300);

        for (int i = 0; i < 40; i++) sim.getMundo().passo(1.0 / 60.0);

        verdadeiro("chip raso: ainda bate no robo",
                bola.getPosicao().x() < 600 || bola.getVelocidade().x() < 0);
    }

    private static void chipAssentaDepoisDeQuicar() {
        Bola bola = new Bola();
        bola.reposicionar(Vec2.ZERO, 0, new Vec2(2000, 0), 3000);
        FisicaBola fisica = new FisicaBola(ParametrosFisica.padrao());

        for (int i = 0; i < 60 * 30; i++) fisica.integrar(bola, 1.0 / 60.0);

        verdadeiro("chip: assenta no chao depois dos quiques", !bola.estaNoAr());
        aproximado("chip: para de vez", bola.getRapidez(), 0, 1);
    }

    private static void dribblerNaoPegaBolaNoAr() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 1, "B", 0);
        Robot r = sim.getMundo().getAzul().getRobo(0);
        r.setPosicao(Vec2.ZERO);
        r.setTheta(0);
        r.setComando(new RobotCommand(0, 0, 0, 0, 0, true)); // dribbler ligado

        Bola bola = sim.getMundo().getBola();
        // Exatamente na boca do dribbler, mas a 300 mm de altura.
        bola.reposicionar(r.pontoDribbler().mais(new Vec2(Bola.RAIO, 0)), 300,
                Vec2.ZERO, 0);

        sim.getMundo().passo(1.0 / 60.0);
        verdadeiro("dribbler: nao captura bola que passa por cima",
                !r.temBolaNoDribbler());
    }

    private record Voo(double apice, double primeiroToque) {}

    /** Solta um chip e mede altura maxima e onde ele bate no chao pela primeira vez. */
    private static Voo voo(double velocidade, double graus, double dt) {
        Bola bola = new Bola();
        bola.lancar(new Vec2(velocidadeHorizontal(velocidade, graus), 0),
                velocidadeVertical(velocidade, graus));
        FisicaBola fisica = new FisicaBola(ParametrosFisica.padrao());

        double apice = 0;
        double primeiroToque = -1;
        double vzAnterior = bola.getVz();

        for (int i = 0; i < (int) (10 / dt) && primeiroToque < 0; i++) {
            fisica.integrar(bola, dt);
            apice = Math.max(apice, bola.getZ());
            // O quique inverte o sinal da velocidade vertical.
            if (vzAnterior < 0 && bola.getVz() > 0) primeiroToque = bola.getPosicao().x();
            vzAnterior = bola.getVz();
        }
        return new Voo(apice, primeiroToque);
    }

    private static double velocidadeHorizontal(double v, double graus) {
        return v * Math.cos(Math.toRadians(graus));
    }

    private static double velocidadeVertical(double v, double graus) {
        return v * Math.sin(Math.toRadians(graus));
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
