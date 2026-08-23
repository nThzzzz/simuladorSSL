package engine;

import core.Vec2;
import model.Bola;
import model.ParametrosFisica;
import model.Cor;
import model.Equipe;
import model.Geometria;
import model.ParedeDoGol;
import model.Robot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Estado completo da simulacao e avanco de um passo de fisica.
 *
 * <p>Nao sabe desenhar, nao sabe decidir e nao sabe gravar arquivo. Recebe os
 * comandos ja prontos nos robos, integra a fisica e deixa disponivel a lista de
 * eventos para quem quiser consumi-la.
 */
public final class Mundo {

    private final Geometria geometria;
    private final List<ParedeDoGol> paredesDosGols;
    private ParametrosFisica parametros;

    private final Bola bola = new Bola();
    private final Equipe azul;
    private final Equipe amarelo;
    private List<Robot> robosCache = List.of();

    private FisicaBola fisicaBola;
    private final FisicaRobo fisicaRobo;
    private Colisoes colisoes;

    private final List<Evento> eventos = new ArrayList<>();
    private long frame;
    private double tempo;

    public Mundo(Geometria geometria, ParametrosFisica parametros) {
        this.geometria = geometria;
        // Geometria e imutavel, entao as seis paredes sao montadas uma vez so
        // e nao a cada passo: em headless a fisica roda centenas de milhares
        // de passos por corrida.
        this.paredesDosGols = geometria.paredesDosGols();
        this.parametros = parametros;
        this.azul = new Equipe("Azul", Cor.AZUL);
        this.amarelo = new Equipe("Amarelo", Cor.AMARELO);
        this.fisicaBola = new FisicaBola(parametros);
        this.fisicaRobo = new FisicaRobo();
        this.colisoes = new Colisoes(parametros);
    }

    // ------------------------------------------------------------- montagem

    public void inicializarPartida(String nomeAzul, int qtdAzul,
                                   String nomeAmarelo, int qtdAmarelo) {
        azul.setNome(nomeAzul);
        amarelo.setNome(nomeAmarelo);
        azul.posicionarFormacao(geometria, qtdAzul);
        amarelo.posicionarFormacao(geometria, qtdAmarelo);
        reconstruirCache();

        bola.reposicionar(Vec2.ZERO);
        registrar(TipoEvento.PARTIDA_INICIADA, Evento.dados(
                "azul", nomeAzul, "robos_azul", azul.getNumRobos(),
                "amarelo", nomeAmarelo, "robos_amarelo", amarelo.getNumRobos()));
    }

    private void reconstruirCache() {
        List<Robot> todos = new ArrayList<>(azul.getRobos());
        todos.addAll(amarelo.getRobos());
        robosCache = Collections.unmodifiableList(todos);
    }

    // ------------------------------------------------------------------ passo

    /** Marca o inicio de um quadro. Os eventos passam a ser carimbados com ele. */
    public void iniciarQuadro(long frame, double tempo) {
        this.frame = frame;
        this.tempo = tempo;
    }

    /** Avanca a fisica em {@code dt} segundos. Os comandos ja devem estar nos robos. */
    public void passo(double dt) {
        for (Robot r : robosCache) fisicaRobo.integrar(r, dt);
        fisicaBola.integrar(bola, dt);
        colisoes.resolver(this);
    }

    public void registrar(TipoEvento tipo, Map<String, Object> dados) {
        eventos.add(new Evento(tempo, frame, tipo, dados));
    }

    /**
     * Retira e devolve os eventos acumulados desde a ultima chamada.
     *
     * <p>Acumular em vez de limpar por quadro e proposital: acoes disparadas
     * pela interface entre dois ticks (reposicionar bola, trocar formacao) nao
     * podem sumir do log so por terem acontecido fora do passo de fisica.
     */
    public List<Evento> drenarEventos() {
        List<Evento> copia = List.copyOf(eventos);
        eventos.clear();
        return copia;
    }

    // ---------------------------------------------------------------- acessos

    public Geometria getGeometria()          { return geometria; }

    /** As tres paredes de cada gol, ja prontas: os unicos obstaculos nao circulares. */
    public List<ParedeDoGol> getParedesDosGols() { return paredesDosGols; }

    public ParametrosFisica getParametros()  { return parametros; }

    /**
     * Troca os parametros de fisica em tempo de execucao.
     *
     * <p>{@link FisicaBola} e {@link Colisoes} guardam os parametros como campo
     * final, entao sao reconstruidos -- sao objetos sem estado e baratos. A troca
     * vira evento para que um log gravado atravessando o ajuste continue
     * interpretavel: o {@code meta.json} descreve a fisica do inicio da corrida.
     */
    public void setParametros(ParametrosFisica novos) {
        this.parametros = novos;
        this.fisicaBola = new FisicaBola(novos);
        this.colisoes = new Colisoes(novos);
        registrar(TipoEvento.PARAMETROS_ALTERADOS, Evento.dados(
                "restituicao_parede", novos.restituicaoParede(),
                "restituicao_robo", novos.restituicaoRobo(),
                "restituicao_robo_robo", novos.restituicaoRoboRobo(),
                "atrito_rolamento", novos.atritoRolamento(),
                "atrito_deslizamento", novos.atritoDeslizamento()));
    }
    public Bola getBola()                    { return bola; }
    public Equipe getAzul()                  { return azul; }
    public Equipe getAmarelo()               { return amarelo; }
    public List<Robot> getRobos()            { return robosCache; }
    public long getFrame()                   { return frame; }
    public double getTempo()                 { return tempo; }
    public Colisoes getColisoes()            { return colisoes; }

    public Equipe getEquipe(Cor cor) { return cor == Cor.AZUL ? azul : amarelo; }

    /** Reposiciona a bola no gramado e registra o evento. */
    public void reposicionarBola(Vec2 p) {
        reposicionarBola(p, 0, Vec2.ZERO, 0);
    }

    /** Reposiciona a bola, possivelmente no ar e em movimento. */
    public void reposicionarBola(Vec2 p, double z, Vec2 velocidade, double vz) {
        bola.reposicionar(p, z, velocidade, vz);
        registrar(TipoEvento.BOLA_REPOSICIONADA, Evento.dados(
                "x", p.x(), "y", p.y(), "z", z,
                "vx", velocidade.x(), "vy", velocidade.y(), "vz", vz));
    }

    /** Robo mais proximo de um ponto, opcionalmente restrito a uma cor. */
    public Robot roboMaisProximo(Vec2 ponto, Cor cor) {
        Robot melhor = null;
        double melhorDist = Double.MAX_VALUE;
        for (Robot r : robosCache) {
            if (cor != null && r.getCor() != cor) continue;
            double d = r.getPosicao().distancia(ponto);
            if (d < melhorDist) { melhorDist = d; melhor = r; }
        }
        return melhor;
    }
}
