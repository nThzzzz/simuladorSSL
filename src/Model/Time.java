package Model;

import View.Campo;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;

public class Time {
    private String nome;
    private int numRobos;
    private List<Model.Robot> robos;
    private boolean isBlue;

    public Time(String nome, int numRobos, boolean isBlue) {
        this.nome = nome;
        this.numRobos = numRobos;
        this.robos = new ArrayList<>(numRobos);
        this.isBlue = isBlue;
    }

    public List<Model.Robot> istanciarRobos(Campo campo) {
        int FIELD_WIDTH = campo.getPreferredSize().width;
        int FIELD_HEIGHT = campo.getPreferredSize().height;

        Point[] pontos = {
                // === ALAS ===
                // Ala superior (Y positivo)
                new Point(abs(FIELD_WIDTH/2 - 3*(FIELD_WIDTH/10)), abs(FIELD_HEIGHT/2 - FIELD_HEIGHT/4)),
                // Ala inferior (Y negativo)
                new Point(abs(FIELD_WIDTH/2 - 3*(FIELD_WIDTH/10)), -abs(FIELD_HEIGHT/2 - FIELD_HEIGHT/4)),

                // === LINHA NO EIXO X (Y = 0) ===
                // Robô 2: Mais recuado
                new Point(abs(FIELD_WIDTH/2 - 4*(FIELD_WIDTH/10)), 0),
                // Robô 3: Alinhado com os alas
                new Point(abs(FIELD_WIDTH/2 - 3*(FIELD_WIDTH/10)), 0),
                // Robô 4: Mais à frente
                new Point(abs(FIELD_WIDTH/2 - 2*(FIELD_WIDTH/10)), 0),
                // Robô 5: Ponta (mais avançado)
                new Point(abs(FIELD_WIDTH/2 - 1*(FIELD_WIDTH/10)), 0)
        };

        this.robos.clear();

        for (int i = 0; i < pontos.length && i < this.numRobos; i++) {
            int sinalX = this.isBlue ? -1 : 1;
            double theta = this.isBlue ? 0 : Math.PI;

            this.robos.add(new Robot(sinalX * pontos[i].x, pontos[i].y, theta, this.isBlue, i));
        }

        return this.robos;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public int getNumRobos() {
        return numRobos;
    }

    public void setNumRobos(int numRobos) {
        this.numRobos = numRobos;
    }

    public List<Robot> getRobos() {
        return robos;
    }

    public void setRobos(List<Robot> robos) {
        this.robos = robos;
    }

    public boolean isBlue() {
        return isBlue;
    }

    public void setBlue(boolean blue) {
        isBlue = blue;
    }
}