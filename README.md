# ⚽ Simulador SSL 2D (Java Swing)

Um simulador bidimensional leve e customizado para a categoria Small Size League (SSL) de robótica, construído inteiramente do zero usando Java nativo (Swing/Graphics2D). 

Este projeto nasceu da curiosidade de desvendar como funcionam as mecânicas e a matemática por trás de simuladores clássicos como o `grSim`, implementando desde o sistema de coordenadas até um motor de física próprio para a movimentação da bola.

## ✨ Funcionalidades

* **Física Customizada (Game Loop a ~60 FPS):** Cálculo vetorial de atrito da bola com o gramado e sistema de rebotes (colisão) nas linhas do campo.
* **Mecânica "Drag and Shoot":** Interação com o mouse simulando um estilingue para chutar a bola, com vetor de mira dinâmico e trava de velocidade limite realista (6.5 m/s).
* **Mundo Cartesiano (Padrão SSL):** O sistema de coordenadas foi adaptado para espelhar a saída da `SSL-Vision`, onde o ponto `(0,0)` fica no exato centro do campo (4 quadrantes).
* **Escala Real:** O ambiente é renderizado assumindo que 1 pixel = 1 cm. A bola (diâmetro de ~4.3cm) e os robôs (diâmetro de 18cm com a geometria frontal achatada de chute) respeitam as dimensões oficiais do regulamento.
* **Câmera e Zoom:** Sistema de zoom centralizado fluido utilizando o scroll do mouse, permitindo focar em jogadas específicas.
* **Interface Dinâmica:** HUD visualizando a velocidade atual da bola em m/s e um Dashboard integrado para reconfigurar a quantidade de robôs e nomes das equipes instantaneamente.

## 🏗️ Arquitetura (Padrão MVC)

O código foi projetado para agir como uma mini-engine, separando rigorosamente a lógica matemática da renderização gráfica:

* **Modelo (`Mundo.java`, `Bola.java`, `Time.java`, `Robot.java`):** O "cérebro" da simulação. Lida com a cinemática, controle de estado e limites do jogo. Não interage com pixels ou bibliotecas de interface.
* **Visão (`Campo.java`):** O "pintor". Inspeciona o estado atual do `Mundo` e renderiza o gramado, objetos, HUD e vetores de mira, aplicando conversões de escala e inversão de eixo Y (`AffineTransform`).
* **Controlador (`Main.java`):** O "maestro". Intercepta os eventos de input (cliques, arrastes, botões), envia essas ações na forma de forças para o modelo e gerencia o relógio do *Game Loop*L
