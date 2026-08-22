package app.fisica;

import model.ParametrosFisica;

import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Um experimento que isola UM parametro de fisica e mede o efeito dele.
 *
 * <p>Existe porque um slider de "restituicao 0,50" nao diz nada a ninguem. O
 * ensaio traduz o numero em algo observavel: quanto a bola rola, com que
 * velocidade ela volta da parede, ate onde vai o chip.
 *
 * <p>Cada ensaio sabe ler e escrever o proprio campo em {@link ParametrosFisica},
 * entao o dialogo nao precisa de um caso especial por parametro.
 *
 * @param nome      rotulo do parametro
 * @param pergunta  o que o ensaio responde, em uma linha
 * @param unidade   unidade da medida, para exibir junto do numero
 * @param limites   janela de desenho {@code {xMin, xMax, vMin, vMax}} em mm
 * @param parede      x de uma parede a desenhar, ou {@code NaN} se o ensaio nao tem
 * @param mostraBola  false quando quem se move sao os robos e a bola so atrapalharia
 * @param leitor    extrai o valor atual do parametro
 * @param escritor  devolve uma copia dos parametros com o valor trocado
 * @param roda      executa o experimento
 */
public record Ensaio(
        String nome,
        String pergunta,
        String unidade,
        Vista vista,
        double[] limites,
        double parede,
        boolean mostraBola,
        double min,
        double max,
        String formato,
        ToDoubleFunction<ParametrosFisica> leitor,
        EscritorDeParametro escritor,
        Function<ParametrosFisica, Trajetoria> roda
) {
    /** Troca um unico campo do record de parametros. */
    @FunctionalInterface
    public interface EscritorDeParametro {
        ParametrosFisica com(ParametrosFisica base, double valor);
    }

    public double valorDe(ParametrosFisica p) { return leitor.applyAsDouble(p); }

    public ParametrosFisica aplicar(ParametrosFisica base, double valor) {
        return escritor.com(base, valor);
    }
}
