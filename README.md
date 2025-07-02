# Simulador de Tráfico Concurrente 🚦

Un simulador de tráfico desarrollado en Java y JavaFX. Este proyecto aplica conceptos de programación concurrente para gestionar el flujo de múltiples vehículos de forma segura y eficiente, previniendo colisiones y manejando prioridades.

## ✨ Características Principales

* **Simulación Gráfica**: Interfaz visual construida con JavaFX que se redimensiona y adapta al tamaño de la ventana.
* **Gestión de Tráfico Concurrente**: Cada vehículo opera en su propio hilo (`Thread`), permitiendo un comportamiento verdaderamente paralelo.
* **Lógica de Prioridad de Carril**: La intersección cede el paso a un carril a la vez, el cual debe vaciarse antes de que el siguiente pueda cruzar.
* **Vehículos de Emergencia**: Soporte para vehículos de emergencia que tienen prioridad absoluta, haciendo que su carril sea atendido de inmediato.
* **Comportamiento de Cola Realista**: Los vehículos hacen fila y avanzan progresivamente (`creeping forward`) a medida que el espacio se libera.
* **Controles Interactivos**:
    * Añade vehículos de forma individual especificando su origen, destino y tipo.
    * Añade un lote de vehículos aleatorios para realizar pruebas de estrés.
    * Retardo en los botones para prevenir la creación masiva y accidental de vehículos.

---

## 🚀 Tecnologías Utilizadas

* **Lenguaje**: Java 17
* **Framework de UI**: JavaFX
* **Gestor de Proyectos**: Apache Maven
* **Servidor Gráfico**: XLaunch

---

## 👨‍💻 Autor

Desarrollado por **Randy Alexander Germosén**.