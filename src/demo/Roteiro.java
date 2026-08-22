package demo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cenario de teste como linha do tempo de acoes.
 *
 * <p>Malha aberta de proposito: nada aqui olha para o estado do mundo para
 * decidir o proximo passo. Isso mantem o cenario deterministico (a mesma
 * corrida sai igual toda vez, o que importa para gerar dataset) e evita
 * devolver logica de navegacao ao simulador, que e do software que joga.
 */
public record Roteiro(String nome, String descricao, double duracao, List<Passo> passos) {

    public Roteiro {
        List<Passo> ordenados = new ArrayList<>(passos);
        ordenados.sort(Comparator.comparingDouble(Passo::t));
        passos = List.copyOf(ordenados);
    }

    public static Construtor de(String nome, String descricao) {
        return new Construtor(nome, descricao);
    }

    /** Montagem legivel de cima para baixo, na ordem em que as coisas acontecem. */
    public static final class Construtor {
        private final String nome;
        private final String descricao;
        private final List<Passo> passos = new ArrayList<>();
        private double duracao;

        private Construtor(String nome, String descricao) {
            this.nome = nome;
            this.descricao = descricao;
        }

        public Construtor em(double t, String rotulo, java.util.function.Consumer<Contexto> acao) {
            passos.add(new Passo(t, rotulo, acao));
            return this;
        }

        /** Fecha o ciclo: ao chegar aqui o roteiro recomeca do zero. */
        public Roteiro reiniciaEm(double duracao) {
            this.duracao = duracao;
            return new Roteiro(nome, descricao, duracao, passos);
        }
    }
}
