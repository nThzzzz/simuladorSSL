package visao;

import core.Vec2;
import model.Cor;
import model.Geometria;

import java.util.List;

/**
 * Retrato completo e imutavel de um quadro da simulacao.
 *
 * <p>Serve a dois consumidores: a janela desenha a partir dele e o
 * {@link rede.PublicadorVisao} o converte em {@code SSL_WrapperPacket}. Uma
 * estrutura so para os dois evita manter dois caminhos de leitura do mundo em
 * dia, e ser imutavel evita ler estado meio atualizado no meio de um repaint.
 */
public record EstadoMundo(
        long frame,
        double tempo,
        Geometria geometria,
        String nomeAzul,
        String nomeAmarelo,
        EstadoBola bola,
        List<EstadoRobo> robos
) {
    public EstadoMundo {
        robos = List.copyOf(robos);
    }

    /** Quadro sem nada em campo, para antes do primeiro pacote chegar. */
    public static EstadoMundo vazio(Geometria geometria) {
        return new EstadoMundo(0, 0, geometria, "Azul", "Amarelo",
                EstadoBola.PARADA, List.of());
    }

    public EstadoRobo robo(Cor cor, int id) {
        for (EstadoRobo r : robos) if (r.cor() == cor && r.id() == id) return r;
        return null;
    }

    /** Robo mais proximo de um ponto; {@code cor} nula aceita as duas equipes. */
    public EstadoRobo maisProximo(Vec2 ponto, Cor cor) {
        EstadoRobo melhor = null;
        double menor = Double.MAX_VALUE;
        for (EstadoRobo r : robos) {
            if (cor != null && r.cor() != cor) continue;
            double d = r.posicao().distancia(ponto);
            if (d < menor) { menor = d; melhor = r; }
        }
        return melhor;
    }

    public int quantidade(Cor cor) {
        int n = 0;
        for (EstadoRobo r : robos) if (r.cor() == cor) n++;
        return n;
    }
}
