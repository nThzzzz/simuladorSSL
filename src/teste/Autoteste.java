package teste;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import app.componentes.Campo;
import visao.EstadoMundo;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

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
        bolaNaoSaiPeloFundoDoGol();
        bolaBateNoPosteEmVezDeEntrar();
        quiqueNoGolIndependeDeDt();
        chipPassaPorCimaDoGol();
        roboParaNoFundoDoGol();
        cenarioChuteChegaAoGol();
        cenarioChipPassaPorCimaEEhRecebido();
        cenarioConducaoMantemAPosse();
        semRollerABolaFicaParaTras();
        colisaoRoboRoboConservaMomento();
        formacaoCabeNoCampo();
        formacaoEhCruzApontandoParaOCentro();
        arquiteturaRespeitaODeclarado();
        zoomAncoraNoCursor();
        zoomNoBatenteNaoDesliza();
        zoomIgnoraPicoDoTrackpad();

        System.out.printf("%n%d/%d verificacoes passaram%n", total - falhas, total);
        if (falhas > 0) System.exit(1);
    }

    // -------------------------------------------------------- arquitetura

    /**
     * As zonas e as dependencias declaradas nos {@code package-info.java} valem.
     *
     * <p>O README sempre descreveu a arquitetura em prosa, e prosa nao segura
     * nada: o grafo REAL tinha dois ciclos que ninguem via -- {@code estrategia}
     * com {@code estrategia.esqueleto}, e depois {@code app.telas} com
     * {@code app.componentes} -- e o {@code view} dependia de tres pacotes que a
     * prosa nao citava. Cada pacote agora DECLARA sua zona e de quem depende, e
     * este caso le a declaracao e o codigo e falha quando os dois divergem.
     *
     * <p>A regra que mais importa e a de zona: {@code ESTAVEL} nao pode depender
     * de {@code TRABALHO} nem de {@code EXTENSAO}. E o que garante que mexer numa
     * play nunca obrigue a mexer na rede -- e o que deixa rodar mil partidas de
     * treino sem abrir janela nem tocar em socket.
     */
    private static void arquiteturaRespeitaODeclarado() {
        Path src = Path.of("src");
        if (!Files.isDirectory(src)) {
            verdadeiro("arquitetura: src/ encontrado (rode a partir da raiz do repo)", false);
            return;
        }

        Map<String, String> zona = new TreeMap<>();
        Map<String, Set<String>> declarado = new TreeMap<>();
        Map<String, Set<String>> real = new TreeMap<>();
        List<String> semInfo = new ArrayList<>();

        try (var caminhos = Files.walk(src)) {
            for (Path p : caminhos.filter(x -> x.toString().endsWith(".java")).toList()) {
                if (p.toString().contains("/proto/")) continue;
                String txt = Files.readString(p);
                Matcher mp = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE).matcher(txt);
                if (!mp.find()) continue;              // Main.java, no pacote padrao
                String pkg = mp.group(1);

                if (p.getFileName().toString().equals("package-info.java")) {
                    // A lista termina no ultimo ponto da LINHA: nome de pacote tem
                    // ponto no meio, e cortar no primeiro dava "estrategia" onde
                    // estava escrito "estrategia.coaches".
                    Matcher md = Pattern.compile(
                            "Zona:\\s*(\\w+)\\.\\s*Depende de:\\s*(.*)\\.\\s*$", Pattern.MULTILINE)
                            .matcher(txt);
                    if (!md.find()) { semInfo.add(pkg + " (formato)"); continue; }
                    zona.put(pkg, md.group(1));
                    Set<String> deps = new TreeSet<>();
                    for (String d : md.group(2).split(",")) {
                        d = d.trim();
                        if (!d.isEmpty() && !d.equals("(nada)")) deps.add(d);
                    }
                    declarado.put(pkg, deps);
                    continue;
                }

                real.putIfAbsent(pkg, new TreeSet<>());
                Matcher mi = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+);", Pattern.MULTILINE)
                        .matcher(txt);
                while (mi.find()) {
                    String imp = mi.group(1);
                    if (imp.startsWith("java.") || imp.startsWith("javax.")
                            || imp.startsWith("com.formdev.")) continue;
                    String alvo = imp.substring(0, imp.lastIndexOf('.'));
                    if (alvo.startsWith("proto")) alvo = "proto";
                    if (!alvo.equals(pkg)) real.get(pkg).add(alvo);
                }
            }
        } catch (Exception e) {
            verdadeiro("arquitetura: leitura de src/ (" + e.getMessage() + ")", false);
            return;
        }

        for (String pkg : real.keySet()) if (!zona.containsKey(pkg)) semInfo.add(pkg);
        verdadeiro("arquitetura: todo pacote declara zona e dependencias"
                + (semInfo.isEmpty() ? "" : " -- faltam: " + semInfo), semInfo.isEmpty());

        List<String> naoDeclaradas = new ArrayList<>();
        for (var e : real.entrySet()) {
            Set<String> ok = declarado.getOrDefault(e.getKey(), Set.of());
            if (ok.contains("*")) continue;                       // teste, por natureza
            for (String alvo : e.getValue()) {
                if (alvo.equals("proto") || ok.contains(alvo)) continue;
                naoDeclaradas.add(e.getKey() + " -> " + alvo);
            }
        }
        verdadeiro("arquitetura: nenhum import fora do que o pacote declara"
                + (naoDeclaradas.isEmpty() ? "" : " -- " + naoDeclaradas), naoDeclaradas.isEmpty());

        List<String> furos = new ArrayList<>();
        for (var e : declarado.entrySet()) {
            if (!"ESTAVEL".equals(zona.get(e.getKey()))) continue;
            for (String alvo : e.getValue()) {
                String za = zona.get(alvo);
                if (za != null && !za.equals("ESTAVEL")) furos.add(e.getKey() + " -> " + alvo + " (" + za + ")");
            }
        }
        verdadeiro("arquitetura: ESTAVEL nao depende de TRABALHO nem de EXTENSAO"
                + (furos.isEmpty() ? "" : " -- " + furos), furos.isEmpty());

        String ciclo = acharCiclo(declarado);
        verdadeiro("arquitetura: nenhuma dependencia circular"
                + (ciclo == null ? "" : " -- " + ciclo), ciclo == null);
    }

    /** Busca em profundidade; devolve o caminho do primeiro ciclo, ou null. */
    private static String acharCiclo(Map<String, Set<String>> grafo) {
        Set<String> pronto = new HashSet<>();
        for (String inicio : grafo.keySet()) {
            Deque<String> pilha = new ArrayDeque<>();
            String r = visitar(inicio, grafo, new LinkedHashSet<>(), pronto, pilha);
            if (r != null) return r;
        }
        return null;
    }

    private static String visitar(String no, Map<String, Set<String>> grafo,
                                  LinkedHashSet<String> caminho, Set<String> pronto,
                                  Deque<String> pilha) {
        if (pronto.contains(no)) return null;
        if (!caminho.add(no)) return String.join(" -> ", caminho) + " -> " + no;
        for (String p : grafo.getOrDefault(no, Set.of())) {
            if (p.equals("*")) continue;
            if (caminho.contains(p)) return String.join(" -> ", caminho) + " -> " + p;
            String r = visitar(p, grafo, caminho, pronto, pilha);
            if (r != null) return r;
        }
        caminho.remove(no);
        pronto.add(no);
        return null;
    }

    // ------------------------------------------------------- zoom do campo

    /**
     * O ponto do mundo sob o cursor tem de ficar parado durante o zoom.
     *
     * <p>Antes o zoom ancorava no centro do painel, e quem olhava um canto via
     * o canto fugir da tela a cada passo. E um caso de tela, mas e aritmetica
     * pura -- da para verificar sem abrir janela, e sem isso a regressao volta
     * sem ninguem notar, porque o sintoma parece "mao pesada no trackpad".
     */
    private static void zoomAncoraNoCursor() {
        Campo campo = new Campo(() -> EstadoMundo.vazio(Geometria.divisaoB()));
        campo.setSize(1000, 700);

        int sx = 820, sy = 160; // longe do centro: era exatamente ali que fugia
        Vec2 alvo = campo.telaParaMundo(sx, sy);

        for (int i = 0; i < 15; i++) campo.aplicarZoom(1.1, sx, sy);
        aproximado("zoom: o ponto sob o cursor fica parado ao aproximar",
                campo.telaParaMundo(sx, sy).distancia(alvo), 0, 0.5);

        for (int i = 0; i < 30; i++) campo.aplicarZoom(1 / 1.1, sx, sy);
        aproximado("zoom: e continua parado ao afastar",
                campo.telaParaMundo(sx, sy).distancia(alvo), 0, 0.5);
    }

    /**
     * No batente o zoom nao acontece, e o pan nao pode andar mesmo assim.
     *
     * <p>E o que quebraria se a correcao do pan usasse o fator PEDIDO em vez do
     * aplicado: a imagem deslizaria sob o cursor com o zoom ja parado, bem na
     * hora em que se insiste no gesto.
     */
    private static void zoomNoBatenteNaoDesliza() {
        Campo campo = new Campo(() -> EstadoMundo.vazio(Geometria.divisaoB()));
        campo.setSize(1000, 700);

        int sx = 300, sy = 520;
        for (int i = 0; i < 60; i++) campo.aplicarZoom(1.1, sx, sy); // crava no teto
        Vec2 noTeto = campo.telaParaMundo(sx, sy);

        for (int i = 0; i < 20; i++) campo.aplicarZoom(1.1, sx, sy); // insiste
        aproximado("zoom: insistir no batente nao desliza a imagem",
                campo.telaParaMundo(sx, sy).distancia(noTeto), 0, 1e-6);
    }

    /**
     * Um pico do trackpad nao pode valer mais que um entalhe de roda.
     *
     * <p>O 4,7 nao e inventado: foi medido no trackpad, no meio de uma rajada de
     * 327 eventos cuja soma inteira era 2,1. Sem teto ele sozinho dava 58% de
     * escala num quadro.
     */
    private static void zoomIgnoraPicoDoTrackpad() {
        Campo campo = new Campo(() -> EstadoMundo.vazio(Geometria.divisaoB()));
        aproximado("zoom: pico de 4,7 do trackpad vale um entalhe, nao 4,7",
                Campo.fatorDeZoom(roda(campo, 4.7)), Campo.fatorDeZoom(roda(campo, 1.0)), 1e-9);
        verdadeiro("zoom: entalhe normal de roda passa intacto",
                Math.abs(Campo.fatorDeZoom(roda(campo, 1.0)) - 1 / 1.1) < 1e-9);
    }

    private static MouseWheelEvent roda(Campo campo, double precise) {
        return new MouseWheelEvent(campo, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                0, 0, 0, 0, 0, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL,
                1, (int) precise, precise);
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


    // -------------------------------------------------------------------- gol

    /**
     * O caso que justifica a varredura: um chute maximo entra e FICA no gol.
     *
     * <p>A 60 Hz a bola percorre 108 mm por quadro e a parede do fundo tem 20 mm.
     * Resolvendo o contato pela posicao de chegada, como se faz com o robo, a
     * bola apareceria dentro do gol num quadro e atras dele no seguinte, sem
     * nunca ter tocado na parede.
     */
    private static void bolaNaoSaiPeloFundoDoGol() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 0, "B", 0);
        Geometria g = sim.getMundo().getGeometria();
        Bola bola = sim.getMundo().getBola();
        bola.reposicionar(Vec2.ZERO, 0, new Vec2(Bola.VEL_MAX, 0), 0);

        double limite = g.meioComprimento() + g.golProfundidade() - Bola.RAIO;
        double xMaximo = 0;
        for (int i = 0; i < 60 * 10; i++) {
            sim.getMundo().passo(1.0 / 60.0);
            xMaximo = Math.max(xMaximo, bola.getPosicao().x());
        }

        verdadeiro("gol: o chute maximo entra pela boca",
                xMaximo > g.meioComprimento());
        verdadeiro("gol: e nao atravessa o fundo em nenhum quadro",
                xMaximo <= limite + 0.1);
    }

    /** Bola alinhada com o poste bate na frente dele e volta, em vez de entrar. */
    private static void bolaBateNoPosteEmVezDeEntrar() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 0, "B", 0);
        Geometria g = sim.getMundo().getGeometria();
        Bola bola = sim.getMundo().getBola();

        // 10 mm para fora da boca: a bola nao cabe entre o poste e essa linha.
        bola.reposicionar(new Vec2(0, g.golLargura() / 2 + 10), 0, new Vec2(6000, 0), 0);

        double xMaximo = 0;
        for (int i = 0; i < 60 * 6; i++) {
            sim.getMundo().passo(1.0 / 60.0);
            xMaximo = Math.max(xMaximo, bola.getPosicao().x());
        }
        verdadeiro("gol: bola alinhada ao poste nao passa da linha de fundo",
                xMaximo <= g.meioComprimento() - Bola.RAIO + 0.1);
    }

    /**
     * A mesma condicao de dt que vale para o resto da fisica, agora no gol.
     *
     * <p>Sem corrigir a velocidade para o instante do toque a bola volta 444 mm a
     * 60 Hz e 385 mm a 2000 Hz -- 15% de diferenca vinda so do tamanho do passo.
     */
    private static void quiqueNoGolIndependeDeDt() {
        double a = quiqueNoFundoDoGol(1.0 / 60.0);
        double b = quiqueNoFundoDoGol(1.0 / 600.0);
        aproximado("gol: quique no fundo igual a 60 Hz e a 600 Hz", a, b, Math.abs(b) * 0.01);
    }

    /** Onde a bola para depois de entrar no gol e voltar do fundo. */
    private static double quiqueNoFundoDoGol(double dt) {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 0, "B", 0);
        Bola bola = sim.getMundo().getBola();
        bola.reposicionar(new Vec2(2000, 0), 0, new Vec2(6000, 0), 0);
        for (int i = 0; i < (int) (8.0 / dt); i++) sim.getMundo().passo(dt);
        return bola.getPosicao().x();
    }

    /** A parede do gol tem 155 mm; acima disso a bola passa, como passa sobre o robo. */
    private static void chipPassaPorCimaDoGol() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 0, "B", 0);
        Geometria g = sim.getMundo().getGeometria();
        Bola bola = sim.getMundo().getBola();
        bola.reposicionar(new Vec2(2000, 0), 0,
                new Vec2(velocidadeHorizontal(6500, 40), 0), velocidadeVertical(6500, 40));

        double xMaximo = 0;
        for (int i = 0; i < 60 * 10; i++) {
            sim.getMundo().passo(1.0 / 60.0);
            xMaximo = Math.max(xMaximo, bola.getPosicao().x());
        }
        // Passar das costas do gol so e possivel por cima da estrutura inteira.
        double costas = g.meioComprimento() + g.golProfundidade() + g.golEspessuraParede();
        verdadeiro("gol: o chip sobrevoa a estrutura em vez de bater no fundo",
                xMaximo > costas + Bola.RAIO);
    }

    /** O robo entra no gol, mas para na face interna do fundo. */
    private static void roboParaNoFundoDoGol() {
        Simulacao sim = Simulacao.padrao();
        sim.inicializarPartida("A", 1, "B", 0);
        Geometria g = sim.getMundo().getGeometria();
        Robot r = sim.getMundo().getAzul().getRobo(0);
        r.setPosicao(new Vec2(4200, 0));
        r.setTheta(0);
        r.setComando(new RobotCommand(Robot.VEL_MAX_PADRAO, 0, 0, 0, 0, false));

        for (int i = 0; i < 60 * 5; i++) sim.getMundo().passo(1.0 / 60.0);

        aproximado("gol: o robo para na face interna do fundo",
                r.getPosicao().x(),
                g.meioComprimento() + g.golProfundidade() - Robot.RAIO, 0.1);
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
