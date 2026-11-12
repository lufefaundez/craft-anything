# Especificación Visual Clean-Room: Word 16.115 (`es-CL`)

Basada en la observación directa de Microsoft Word 16.115 (`es-CL`) y diseñada para replicarse fielmente en **Android (Tablet & Phone), iOS/iPadOS y visionOS** sin recortes funcionales.

## 1. Jerarquía del Chrome en Tablet / iPad / visionOS

1. **QAT + Barra de Título (`38 dp`):**
   - Izquierda: `Autoguardado` (toggle), `Inicio` (casita → Backstage), `Guardar`, `Deshacer ▾`, `Repetir`, `Imprimir`, `Personalizar QAT (···)`.
   - Centro: Título del documento (`Documento1`).
   - Derecha: `Comentarios`, selector `Edición ▾`, botón pill azul (`#185ABD`) `Compartir ▾`, `Buscar`.
2. **Tira de Pestañas (`32 dp`):**
   - `Archivo` | `Inicio` (subrayado azul `#185ABD` de `2.5 dp`) | `Insertar` | `Dibujar` | `Diseño` | `Disposición` | `Referencias` | `Correspondencia` | `Revisar` | `Vista`
   - Pestañas contextuales dinámicas: `Diseño de tabla`, `Disposición de tabla`, `Formato de imagen`, `Formato de forma`, `Ecuación`, `Diseño de gráfico`, `SmartArt`.
3. **Tarjeta del Ribbon (`92 dp`, radio `8 dp`, borde `#E1E1E1`, sombra suave):**
   - **Inicio:**
     - *Portapapeles:* `Pegar ▾` (grande), `Cortar`, `Copiar`, `Copiar formato`
     - *Fuente:* Selector familia (default lógico `Aptos (Cuerpo)`), Tamaño (`12`), `A^`, `Aˇ`, `Aa ▾`, `Borrar formato`; Fila 2: `N`, `K`, `S ▾`, `ab` (tachado), `x₂`, `x²`, `Efectos de texto ▾`, `Resaltado ▾`, `Color de fuente ▾`
     - *Párrafo:* `Viñetas ▾`, `Numeración ▾`, `Lista multinivel ▾`, `Disminuir sangría`, `Aumentar sangría`, `Ordenar A↓Z`, `Mostrar todo ¶`; Fila 2: `Izquierda`, `Centrar`, `Derecha`, `Justificar`, `Interlineado ▾`, `Sombreado ▾`, `Bordes ▾`
     - *Estilos:* Mini tarjetas con vista previa (`Normal`, `Sin espaciado`, `Título 1`, `Título 2`...) + botón `Panel de estilos`
     - *Edición / Voz:* `Buscar y reemplazar`, `Dictar`, `Editor` (ortografía/gramática)
   - **Insertar:** Páginas (Portada, Página en blanco, Salto de página), Tablas (Cuadrícula NxM + Dibujar tabla), Ilustraciones (Imágenes, Formas, Iconos, SmartArt, Gráfico), Vínculos (Vínculo, Marcador, Referencia cruzada), Encabezado y pie (Encabezado, Pie, Número de página), Texto (Cuadro de texto, Letra capital), Símbolos (Ecuación, Símbolo).
   - **Dibujar:** Herramientas de dibujo (Seleccionar, Borrador, Pluma negra/roja, Lápiz, Resaltador), Agregar lápiz, Convertir entrada de lápiz a forma, Reproducción de entrada manuscrita.
   - **Diseño:** Formato del documento (Temas, Conjuntos de estilos, Colores, Fuentes, Espaciado entre párrafos), Fondo de página (Marca de agua, Color de página, Bordes de página).
   - **Disposición:** Configurar página (Márgenes, Orientación, Tamaño [Carta/Oficio/A4], Columnas, Saltos, Números de línea, Guiones), Párrafo (Sangría izq/der, Espaciado antes/después), Organizar (Posición, Ajustar texto, Traer adelante, Enviar atrás, Alinear, Agrupar, Girar).
   - **Referencias:** Tabla de contenido, Notas al pie / Notas al final, Citas y bibliografía (APA/IEEE/Chicago, Administrar fuentes), Títulos (Insertar título, Tabla de ilustraciones), Índice.
   - **Correspondencia:** Sobres y etiquetas, Combinar correspondencia (Seleccionar destinatarios CSV/XLSX/ODS, Insertar campo combinado, Vista previa de resultados, Finalizar y combinar).
   - **Revisar:** Revisión (Ortografía y gramática, Sinónimos, Contar palabras), Voz (Leer en voz alta), Idioma (Establecer idioma de corrección), Comentarios (Nuevo, Anterior, Siguiente, Eliminar, Resolver), Seguimiento (Control de cambios, Revisiones), Cambios (Aceptar, Rechazar), Proteger (Restringir edición).
   - **Vista:** Vistas (Modo lectura, Diseño de impresión, Diseño web, Esquema, Borrador), Inmersivo (Concentración), Mostrar (Regla, Líneas de cuadrícula, Panel de navegación), Zoom (Zoom, 100%, Una página, Varias páginas, Ancho de página), Ventana (Nueva ventana, Organizar todo, Ver en paralelo).
4. **Barra de Estado (`26 dp`):**
   - Izquierda: `Página X de Y`, `N palabras`, estado revisión, Idioma (`Español (Chile)` / `Español (España)`).
   - Derecha: `Concentración`, selector de 4 vistas, `-` Slider `+`, `% Zoom`.

## 2. Reglas de Adaptación en Smartphones (`< 600dp`)

- **Nunca ocultar herramientas avanzadas.**
- En teléfono:
  - El canvas ocupa ~85 % de la altura.
  - Barra contextual sobre teclado con 6 accesos directos + botón `▲ Ribbon`.
  - Al tocar `▲ Ribbon`, se abre un **Bottom Sheet** con buscador superior (`Buscar comando...`), selector horizontal de las **9 pestañas** (+ contextuales activas) y tarjetas táctiles (`>= 44 dp` por botón) agrupando exactamente las mismas funciones de cada pestaña.
  - Toggle rápido en barra superior: **Vista Móvil (Reflow)** ↔ **Vista Página Real**.
