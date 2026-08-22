package log;

import engine.Evento;
import engine.Mundo;
import model.Bola;
import model.ParametrosFisica;
import model.Equipe;
import model.Geometria;
import model.Robot;
import model.RobotCommand;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Grava a corrida em um diretorio com quatro arquivos:
 *
 * <ul>
 *   <li>{@code meta.json}    -- geometria, fisica, equipes e dt (reproduz a corrida)
 *   <li>{@code ball.csv}     -- uma linha por quadro
 *   <li>{@code robots.csv}   -- uma linha por robo por quadro (formato longo)
 *   <li>{@code events.jsonl} -- um evento por linha
 * </ul>
 *
 * <p>O CSV de robos e longo, e nao largo com uma coluna por robo, porque a
 * quantidade de robos muda em tempo de execucao pela interface -- um cabecalho
 * largo ficaria invalido no meio do arquivo. Em formato longo basta filtrar:
 * {@code df[(df.cor == "blue") & (df.id == 3)]}.
 *
 * <p>Quais streams sao gravados vem da {@link ConfigLog}. Um stream desligado
 * nao cria arquivo nem consome fila -- {@code meta.json} e sempre escrito, e
 * registra a configuracao usada para que quem le o log saiba se o tracking foi
 * decimado ou simplesmente nao existe.
 *
 * <p>A escrita acontece numa thread separada alimentada por fila limitada. Se o
 * disco nao acompanhar, a fisica bloqueia em vez de descartar linhas: um log com
 * buracos silenciosos e pior do que uma simulacao mais lenta.
 */
public final class LoggerArquivo implements Logger {

    public static final int VERSAO_LOG = 1;

    private static final int DESTINO_BOLA = 0;
    private static final int DESTINO_ROBOS = 1;
    private static final int DESTINO_EVENTOS = 2;

    private record Linha(int destino, String texto) {}

    private static final Linha FIM = new Linha(-1, null);

    private final Path diretorio;
    private final ConfigLog config;

    private final BlockingQueue<Linha> fila = new ArrayBlockingQueue<>(8192);
    private final Writer[] writers = new Writer[3];
    private final Thread escritor;

    private volatile IOException falha;
    private volatile boolean fechado;
    private long quadrosGravados;
    private long eventosGravados;

    public LoggerArquivo(Path diretorio) { this(diretorio, ConfigLog.COMPLETO); }

    public LoggerArquivo(Path diretorio, ConfigLog config) {
        this.diretorio = diretorio;
        this.config = config;

        try {
            Files.createDirectories(diretorio);
            if (config.tracking()) {
                writers[DESTINO_BOLA]  = abrir("ball.csv");
                writers[DESTINO_ROBOS] = abrir("robots.csv");
            }
            if (config.eventos()) {
                writers[DESTINO_EVENTOS] = abrir("events.jsonl");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("nao foi possivel abrir o log em " + diretorio, e);
        }

        this.escritor = new Thread(this::consumir, "ssl-logger");
        this.escritor.setDaemon(true);
        this.escritor.start();

        if (config.tracking()) {
            enfileirar(DESTINO_BOLA, "t,frame,x,y,z,vx,vy,vz,rapidez,deslizando,no_ar");
            enfileirar(DESTINO_ROBOS, "t,frame,cor,id,x,y,theta,vx,vy,rapidez,omega,"
                    + "cmd_vt,cmd_vn,cmd_w,cmd_kick,cmd_kick_ang,cmd_drib,posse");
        }
    }

    private Writer abrir(String nome) throws IOException {
        return new BufferedWriter(
                Files.newBufferedWriter(diretorio.resolve(nome), StandardCharsets.UTF_8), 1 << 16);
    }

    // ------------------------------------------------------------------ Logger

    @Override
    public void inicio(Mundo mundo, double dt) {
        Geometria g = mundo.getGeometria();
        ParametrosFisica p = mundo.getParametros();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("versao_log", VERSAO_LOG);
        meta.put("criado_em", Instant.now().toString());
        meta.put("dt", dt);
        meta.put("hz", Math.round(1.0 / dt * 1e6) / 1e6);
        meta.put("streams", Map.of(
                "tracking", config.tracking(),
                "eventos", config.eventos()));
        meta.put("intervalo_quadros", config.intervaloQuadros());
        meta.put("hz_tracking", config.tracking()
                ? Math.round(config.hzEfetivo(dt) * 1e6) / 1e6 : 0.0);
        meta.put("unidades", Map.of(
                "posicao", "mm", "velocidade", "mm/s",
                "angulo", "rad", "tempo", "s"));

        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("comprimento", g.comprimento());
        geo.put("largura", g.largura());
        geo.put("faixa_externa", g.faixaExterna());
        geo.put("gol_largura", g.golLargura());
        geo.put("gol_profundidade", g.golProfundidade());
        geo.put("area_defesa_profundidade", g.areaDefesaProfundidade());
        geo.put("area_defesa_largura", g.areaDefesaLargura());
        geo.put("raio_circulo_central", g.raioCirculoCentral());
        geo.put("espessura_linha", g.espessuraLinha());
        meta.put("geometria", geo);

        Map<String, Object> fisica = new LinkedHashMap<>();
        fisica.put("gravidade", p.gravidade());
        fisica.put("atrito_deslizamento", p.atritoDeslizamento());
        fisica.put("atrito_rolamento", p.atritoRolamento());
        fisica.put("restituicao_parede", p.restituicaoParede());
        fisica.put("restituicao_robo", p.restituicaoRobo());
        fisica.put("restituicao_robo_robo", p.restituicaoRoboRobo());
        fisica.put("atrito_tangencial_robo", p.atritoTangencialRobo());
        fisica.put("velocidade_minima_bola", p.velocidadeMinimaBola());
        fisica.put("alcance_dribbler", p.alcanceDribbler());
        fisica.put("forca_dribbler", p.forcaDribbler());
        meta.put("fisica", fisica);

        meta.put("dimensoes", Map.of(
                "raio_bola", Bola.RAIO,
                "raio_robo", Robot.RAIO,
                "dist_face_frontal", Robot.DIST_FACE_FRONTAL));

        meta.put("equipes", Map.of(
                mundo.getAzul().getCor().tag(), descreverEquipe(mundo.getAzul()),
                mundo.getAmarelo().getCor().tag(), descreverEquipe(mundo.getAmarelo())));

        try {
            Files.writeString(diretorio.resolve("meta.json"), Json.escrever(meta),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            falha = e;
        }
    }

    private static Map<String, Object> descreverEquipe(Equipe e) {
        List<Integer> ids = new ArrayList<>();
        for (Robot r : e.getRobos()) ids.add(r.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", e.getNome());
        m.put("ids", ids);
        return m;
    }

    @Override
    public void quadro(Mundo mundo) {
        if (fechado || !config.tracking()) return;
        long frame = mundo.getFrame();
        if (frame % config.intervaloQuadros() != 0) return;

        String t = Formato.compacto(mundo.getTempo());
        Bola bola = mundo.getBola();

        StringBuilder sb = new StringBuilder(96);
        sb.append(t).append(',').append(frame).append(',')
          .append(Formato.compacto(bola.getPosicao().x())).append(',')
          .append(Formato.compacto(bola.getPosicao().y())).append(',')
          .append(Formato.compacto(bola.getZ())).append(',')
          .append(Formato.compacto(bola.getVelocidade().x())).append(',')
          .append(Formato.compacto(bola.getVelocidade().y())).append(',')
          .append(Formato.compacto(bola.getVz())).append(',')
          .append(Formato.compacto(bola.getRapidez())).append(',')
          .append(bola.estaDeslizando() ? 1 : 0).append(',')
          .append(bola.estaNoAr() ? 1 : 0);
        enfileirar(DESTINO_BOLA, sb.toString());

        for (Robot r : mundo.getRobos()) {
            RobotCommand c = r.getComando();
            StringBuilder rb = new StringBuilder(160);
            rb.append(t).append(',').append(frame).append(',')
              .append(r.getCor().tag()).append(',').append(r.getId()).append(',')
              .append(Formato.compacto(r.getPosicao().x())).append(',')
              .append(Formato.compacto(r.getPosicao().y())).append(',')
              .append(Formato.compacto(r.getTheta())).append(',')
              .append(Formato.compacto(r.getVelocidade().x())).append(',')
              .append(Formato.compacto(r.getVelocidade().y())).append(',')
              .append(Formato.compacto(r.getRapidez())).append(',')
              .append(Formato.compacto(r.getOmega())).append(',')
              .append(Formato.compacto(c.velTangencial())).append(',')
              .append(Formato.compacto(c.velNormal())).append(',')
              .append(Formato.compacto(c.velAngular())).append(',')
              .append(Formato.compacto(c.velChute())).append(',')
              .append(Formato.compacto(c.anguloChute())).append(',')
              .append(c.dribbler() ? 1 : 0).append(',')
              .append(r.temBolaNoDribbler() ? 1 : 0);
            enfileirar(DESTINO_ROBOS, rb.toString());
        }
        quadrosGravados++;
    }

    @Override
    public void eventos(List<Evento> eventos) {
        if (fechado || !config.eventos() || eventos.isEmpty()) return;
        for (Evento e : eventos) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", e.t());
            m.put("frame", e.frame());
            m.put("tipo", e.tipo().name());
            m.putAll(e.dados());
            enfileirar(DESTINO_EVENTOS, Json.escrever(m));
            eventosGravados++;
        }
    }

    @Override
    public void close() {
        if (fechado) return;
        fechado = true;
        try {
            fila.put(FIM);
            escritor.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (falha != null) {
            throw new UncheckedIOException("falha ao gravar o log em " + diretorio, falha);
        }
    }

    // ------------------------------------------------------------------ interno

    private void enfileirar(int destino, String texto) {
        if (falha != null || writers[destino] == null) return;
        try {
            fila.put(new Linha(destino, texto));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void consumir() {
        try {
            while (true) {
                Linha l = fila.take();
                if (l == FIM) break;
                Writer w = writers[l.destino()];
                if (w == null) continue;
                w.write(l.texto());
                w.write('\n');
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            falha = e;
        } finally {
            for (Writer w : writers) {
                if (w == null) continue;
                try { w.close(); } catch (IOException e) { if (falha == null) falha = e; }
            }
        }
    }

    public Path getDiretorio()  { return diretorio; }
    public ConfigLog getConfig() { return config; }

    @Override public long getQuadrosGravados() { return quadrosGravados; }
    @Override public long getEventosGravados() { return eventosGravados; }
}
