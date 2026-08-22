package model;

/**
 * Constantes ajustaveis do motor de fisica, em mm / s / rad.
 *
 * <p>E um record proposital: os parametros sao gravados no cabecalho do log,
 * entao uma corrida sempre pode ser reproduzida com a mesma fisica que a gerou.
 * Trocar de parametros durante uma gravacao e permitido -- a troca entra no log
 * como um evento {@code PARAMETROS_ALTERADOS}.
 *
 * <p>Sobre os padroes de restituicao: um robo de SSL e construido para MATAR a
 * bola no contato, nao para devolve-la, dai {@code restituicaoRobo} baixo.
 *
 * <p>A {@code restituicaoParede} tem uma sensibilidade que nao se ve no numero:
 * ela entra duas vezes no resultado, na velocidade de saida e no termo
 * {@code (5e-2)/7} que decide quanta velocidade sobrevive ao deslize seguinte.
 * Perto de 0,4 esse termo tende a zero, entao pequenos ajustes mudam muito o
 * quanto a bola volta -- de 0,50 para 0,59 a saida sobe 18% e a distancia
 * percorrida na volta sobe 58%.
 *
 * @param gravidade            mm/s^2
 * @param atritoDeslizamento   coeficiente cinetico bola-carpete (fase de deslizamento)
 * @param atritoRolamento      coeficiente de rolamento bola-carpete
 * @param restituicaoParede    fracao de velocidade preservada ao quicar na parede
 * @param restituicaoRobo      fracao preservada na colisao bola-robo
 * @param restituicaoRoboRobo  fracao preservada na colisao robo-robo
 * @param atritoTangencialRobo fracao da componente tangencial preservada na colisao bola-robo
 * @param velocidadeMinimaBola mm/s abaixo dos quais a bola e considerada parada
 * @param alcanceDribbler      mm de folga a frente da face do dribbler onde a bola e capturada
 * @param forcaDribbler        1/s -- taxa com que o dribbler puxa a bola para a face
 * @param restituicaoQuique    fracao da velocidade vertical preservada ao bater no chao
 * @param atritoQuique         fracao da velocidade horizontal preservada em cada quique
 */
public record ParametrosFisica(
        double gravidade,
        double atritoDeslizamento,
        double atritoRolamento,
        double restituicaoParede,
        double restituicaoRobo,
        double restituicaoRoboRobo,
        double atritoTangencialRobo,
        double velocidadeMinimaBola,
        double alcanceDribbler,
        double forcaDribbler,
        double restituicaoQuique,
        double atritoQuique
) {
    public static ParametrosFisica padrao() {
        return new ParametrosFisica(
                9810.0,   // gravidade
                0.30,     // atrito de deslizamento
                0.05,     // atrito de rolamento
                0.59,     // restituicao parede
                0.35,     // restituicao bola-robo
                0.30,     // restituicao robo-robo
                0.80,     // atrito tangencial bola-robo
                1.0,      // velocidade minima da bola
                12.0,     // alcance do dribbler
                20.0,     // forca do dribbler
                0.55,     // restituicao do quique
                0.75      // atrito horizontal no quique
        );
    }

    /** Desaceleracao da bola enquanto desliza, mm/s^2. */
    public double desaceleracaoDeslizamento() { return atritoDeslizamento * gravidade; }

    /** Desaceleracao da bola em rolamento puro, mm/s^2. */
    public double desaceleracaoRolamento() { return atritoRolamento * gravidade; }
}
