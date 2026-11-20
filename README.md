# Fuga Orbital

Tetris invertido espacial para Android (Kotlin, render 2.5D propio). Huyes de un planeta: las naves enemigas
te persiguen desde el centro; empújalas hacia el borde. Completa filas
para limpiarlas y suelta hierro para llenar la barra. Pierdes si te alcanzan en el centro.

## Instalar desde el celular

1. Abre la sección Releases de este repositorio en el navegador del celular.
2. Descarga `fuga-orbital-vN.apk` de la versión "latest".
3. Ábrelo y permite instalar apps de este origen cuando Android lo pida.

El APK se compila solo en GitHub Actions con cada cambio en `main`.

## Controles

- Arrastrar: mueve la nave libremente (nunca hacia el centro).
- Tocar: gira la pieza.
- Empujón rápido hacia afuera: la lanza de golpe.

## Créditos de audio

La música usa grabaciones de la librería orquestal [VSCO 2 Community Edition](https://github.com/sgossner/VSCO-2-CE) de Versilian Studios (licencia CC0). Las mezclas se generan con `tools/orquesta.py`; la guitarra eléctrica es un modelo físico de cuerda definido en `tools/make_music.py`.


Batería, guitarras y contrabajo de los temas Épica, Neutron y Duro: bancos CC0 de Karoryfer Lecolds (Big Rusty Drums, Emilyguitar, Meatbass), https://github.com/sfzinstruments .
