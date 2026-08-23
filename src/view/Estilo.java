package view;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/**
 * Aparencia da janela: um visual so, igual em qualquer sistema operacional.
 *
 * <p>Antes daqui o simulador nao escolhia look-and-feel nenhum, e caia no Metal
 * -- o visual de fabrica do Swing, de 1998. A estrategia, do outro lado, pedia
 * o visual do SISTEMA. As duas janelas costumam ficar lado a lado na mesma tela
 * e sairam com botao diferente uma da outra na MESMA maquina, alem de mudarem de
 * cara entre macOS e Windows. Ter um L&F escolhido de proposito nos dois e o que
 * faz as duas parecerem o mesmo programa vendo a mesma cena.
 *
 * <p>O FlatLaf desenha tudo em Java, sem delegar nada ao sistema, e por isso sai
 * igual nos tres sistemas. Escolher o Metal tambem daria isso, mas o Metal nao
 * respeita cor por componente o bastante para acompanhar o painel escuro daqui.
 *
 * <p>A fonte e o segundo eixo, e independente do L&F. {@code new Font("SansSerif",
 * ...)} nao nomeia uma fonte: e um pedido que o JDK resolve para uma fonte FISICA
 * diferente em cada sistema -- SF no mac, Segoe UI no Windows. Larguras
 * diferentes movem texto desenhado a mao, e nenhum look-and-feel conserta isso,
 * porque {@link Campo} pinta direto no {@code Graphics}. A JetBrains Mono vai
 * embarcada no jar: mesma fonte em todo lugar, com ou sem ela instalada.
 *
 * <p>Ela e monoespacada de proposito. A tela do simulador e quase toda numero
 * medido -- posicao, velocidade, tempo -- e digito de largura fixa nao faz o
 * valor dancar quando so o algarismo muda.
 *
 * <p>As cores sao as mesmas da {@code view.Paleta} da estrategia, e isso e
 * proposital pelo mesmo motivo que o verde do campo e: as duas janelas ficam
 * lado a lado, e um cinza diferente em cada uma faria parecer dois programas.
 */
public final class Estilo {

    private Estilo() {}

    public static final Color FUNDO      = new Color(18, 18, 20);
    public static final Color PAINEL     = new Color(32, 32, 36);
    public static final Color PAINEL_ALT = new Color(26, 26, 30);
    public static final Color BORDA      = new Color(58, 58, 64);
    public static final Color TEXTO      = new Color(225, 225, 230);
    public static final Color APAGADO    = new Color(150, 150, 160);
    public static final Color REALCE     = new Color(50, 120, 255);

    /** Corpo da interface, no mesmo tamanho que a estrategia usa. */
    public static final float CORPO = 12f;

    /**
     * Instala o visual. Tem de rodar na thread do Swing e antes de qualquer
     * componente nascer: quem ja existe guardou a fonte e a cor do L&F anterior.
     */
    public static void instalar() {
        FlatJetBrainsMonoFont.install();
        FlatDarkLaf.setup();

        UIManager.put("defaultFont", fonte(Font.PLAIN, CORPO));

        UIManager.put("Panel.background", PAINEL);
        UIManager.put("Panel.foreground", TEXTO);
        UIManager.put("Label.foreground", TEXTO);
        UIManager.put("Label.disabledForeground", APAGADO);
        UIManager.put("Separator.foreground", BORDA);

        UIManager.put("Button.background", PAINEL_ALT);
        UIManager.put("Button.foreground", TEXTO);
        UIManager.put("Button.borderColor", BORDA);
        UIManager.put("Button.hoverBorderColor", APAGADO);
        UIManager.put("Button.disabledText", APAGADO);

        UIManager.put("ToggleButton.background", PAINEL_ALT);
        UIManager.put("ToggleButton.foreground", TEXTO);

        UIManager.put("TextField.background", FUNDO);
        UIManager.put("TextField.foreground", TEXTO);
        UIManager.put("TextField.borderColor", BORDA);
        UIManager.put("TextArea.background", PAINEL);
        UIManager.put("TextArea.foreground", TEXTO);
        UIManager.put("ComboBox.background", PAINEL_ALT);
        UIManager.put("ComboBox.foreground", TEXTO);
        UIManager.put("CheckBox.foreground", TEXTO);
        UIManager.put("Spinner.background", FUNDO);
        UIManager.put("Spinner.foreground", TEXTO);
        UIManager.put("Slider.trackColor", BORDA);
        UIManager.put("Slider.thumbColor", REALCE);

        UIManager.put("ScrollPane.background", PAINEL);
        UIManager.put("Viewport.background", PAINEL);
        UIManager.put("SplitPane.background", FUNDO);
        UIManager.put("SplitPaneDivider.draggingColor", REALCE);

        UIManager.put("TitlePane.background", FUNDO);
        UIManager.put("TitlePane.foreground", TEXTO);
        UIManager.put("OptionPane.background", PAINEL);
        UIManager.put("ToolTip.background", PAINEL_ALT);
        UIManager.put("ToolTip.foreground", TEXTO);

        UIManager.put("Component.focusColor", REALCE);
        UIManager.put("Component.focusedBorderColor", REALCE);
        UIManager.put("Component.borderColor", BORDA);

        // Canto levemente arredondado em botao e campo. O quadrado do Metal
        // parece Java de 2005; o pilula do Aqua nao cabe numa coluna estreita.
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("Component.focusWidth", 1);
    }

    /**
     * A fonte da interface, no estilo e tamanho pedidos.
     *
     * <p>Existe para que nenhum {@code new Font("...")} solto sobre no codigo:
     * um so seria suficiente para trazer de volta a fonte-do-sistema e a
     * diferenca entre maquinas que esta classe foi escrita para acabar.
     */
    public static Font fonte(int estilo, float tamanho) {
        return new Font(FlatJetBrainsMonoFont.FAMILY, estilo, Math.round(tamanho));
    }
}
