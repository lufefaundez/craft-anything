# Plan Maestro — Suite OfiOfi (`DocxDocx` · `XlsxXlsx` · `PptxPptx`)

## 1. Objetivo

Construir la suite ofimática **`OfiOfi`**, gratuita en su núcleo completo de edición (sin recortes funcionales), comercialmente sostenible y visualmente familiar a **Microsoft Office 16.115**, optimizada para:
- **Android Smartphones** (`Compact < 600dp`)
- **Android Tablets & Foldables** (`Medium / Expanded >= 600dp`)
- **iPhone** (`iOS`)
- **iPad** (`iPadOS`, con Apple Pencil y Stage Manager)
- **Apple Vision Pro** (`visionOS`, ventanas espaciales + `Ornaments`)

Orden de ejecución:
1. **Ola 1 (Prioridad actual):** **`DocxDocx`** — Word (`.docx`, `.doc`, `.odt`, `.rtf`, `.txt`, exportación `.pdf`)
2. **Ola 2:** **`XlsxXlsx`** — Excel (`.xlsx`, `.xls`, `.ods`, `.csv`)
3. **Ola 3:** **`PptxPptx`** — PowerPoint (`.pptx`, `.odp`, exportación `.pdf`)

## 2. Rol de `craft-anything` vs `wordcraft` / `gridcraft` / `deckcraft`

- **`craft-anything`** (este repositorio): motor de ingeniería limpia, análisis binario/UI y comparación visual automatizada. Se utiliza para:
  1. Alojar el plan maestro, las especificaciones de UI observadas, los tokens de diseño y el tracker (`office-suite/`).
  2. Ejecutar QA visual y de paridad comparando los builds móviles/Apple contra capturas de Microsoft Word/Excel/PowerPoint en macOS.
- **`wordcraft` / `gridcraft` / `deckcraft`** (repositorios Apache-2.0/MIT de `storytold`): aportan el **núcleo en Rust puro (capas L0–L5)** ya probado:
  - En Word (`wordcraft`): `geom`, `doc`, `fonts`, `metafile`, `craft-proof`, `docbin`, `docx`, `formats`, `layout`, `pdf` (`krilla`), `render` (`vello_cpu`), `engine` (69 módulos de comandos y reglas de typing parity).
  - La capa L6 (`ui-egui`) **NO** se usa en móvil/spatial: se reemplaza por un puente **`UniFFI` (`mobile-ffi`)** + interfaces **100 % nativas** en Jetpack Compose (Android) y SwiftUI (iOS/iPadOS/visionOS).

## 3. Arquitectura técnica multiplataforma

```text
+-------------------------------------------------------------------------+
|                    UI NATIVA ADAPTATIVA (ZERO RECORTES)                 |
|  Android (Kotlin + Jetpack Compose) | Apple (SwiftUI Multiplatform)     |
|  - Phone: BottomSheet Ribbon 9 tabs | - iPhone: BottomSheet + UITextInput|
|  - Tablet: Full Ribbon + S-Pen      | - iPad: Full Ribbon + PencilKit   |
|  - PrintDocumentAdapter / SAF       | - visionOS: Top/Bottom Ornaments  |
+-------------------------------------+-----------------------------------+
                                      | UniFFI (Kotlin + Swift bindings)
                                      v
+-------------------------------------------------------------------------+
|                  crates/mobile-ffi (Rust, never-crash)                  |
|  - MobileDocumentSession (open/save/new/export_pdf)                     |
|  - Tile Rasterizer (render_tile RGBA8888, caché de páginas sucias)      |
|  - Command Bridge (execute_command -> dirty_pages + format_state JSON)  |
|  - Touch Hit-Testing, Selection Handles & IME Preedit Transactions      |
+-------------------------------------+-----------------------------------+
                                      |
                                      v
+-------------------------------------------------------------------------+
|              NÚCLEO OFIMÁTICO RUST (L0-L5, sin egui/winit)              |
|  wordcraft-doc / docx / docbin / formats / layout / render / engine     |
+-------------------------------------------------------------------------+
```

## 4. Fuentes y Activos Legales (Clean-Room)

- **Licencias y monetización:** Los repositorios núcleo (`storytold/wordcraft`, `gridcraft`, `deckcraft`, `craft-libs`) usan licencia permisiva **`MIT OR Apache-2.0`**, lo que habilita distribución comercial en tiendas y monetización ética (p. ej. suscripción **OfiOfi Pro** para IA integrada, nube, OCR y plantillas premium, manteniendo 100 % gratis el editor local), conservando los avisos `NOTICE`/`ATTRIBUTION.md` y excluyendo diccionarios GPL de los binarios embebidos.
- **Íconos:** Catálogo SVG propio exportado a Android Vector Drawable y Apple Asset Catalog. Siluetas y colores semánticos equivalentes a Office sin copiar binarios de Microsoft ni activos de marca de ArtCraft.
- **Tipografías de documentos:** Sustituciones métricas libres desde `storytold/craft-fonts` (`Carlito` ↔ Calibri, `Caladea` ↔ Cambria, `Liberation` ↔ Arial/Times/Courier, `STIX Two Math` ↔ Cambria Math, mapeo métrico libre para `Aptos`) + uso prioritario de fuentes embebidas en `.docx` o instaladas en el OS.
- **Diccionarios:** `craft-proof` con soporte incorporado para español (`es-419`, `es-ES`, `es-MX`, `es-UY`) e inglés.
