# Atribución y Licencias — Suite OfiOfi (`DocxDocx` · `XlsxXlsx` · `PptxPptx`)

**OfiOfi** es una suite ofimática gratuita de código limpio (clean-room), sin servidores propietarios ni `OfiOfi Cloud` (almacenamiento local, iCloud Drive, Google Drive mediante selectores nativos del sistema operativo y WebDAV estándar).

## 1. Motores Base en Rust (`MIT OR Apache-2.0`)
- **`storytold/wordcraft`** (Ola 1 Word / `DocxDocx`: `geom`, `doc`, `fonts`, `metafile`, `craft-proof`, `docbin`, `docx`, `formats`, `layout`, `pdf`, `render`, `engine`) — Licencia dual `MIT OR Apache-2.0`.
- **`storytold/gridcraft`** (Ola 2 Excel / `XlsxXlsx`, shell compartido vía `OfficeShellKit`) — Licencia dual `MIT OR Apache-2.0`.
- **`storytold/deckcraft`** (Ola 3 PowerPoint / `PptxPptx`, shell compartido vía `OfficeShellKit`) — Licencia dual `MIT OR Apache-2.0`.
- **`storytold/craft-libs`** (`krilla`, renderizado PDF/vectorial) — Licencia `MIT OR Apache-2.0`.
- **Puente FFI (`word-mobile-ffi`)** — `UniFFI` (`Mozilla Public License 2.0` / compatible vinculación FFI), `serde_json`, `thiserror`, `kurbo`, `parking_lot` (`MIT OR Apache-2.0`).

## 2. Tipografías Métricas Abiertas (`SIL OFL 1.1`)
- **Carlito** (sustitución métrica libre para Calibri / familia Sans métrica) — `SIL Open Font License 1.1`.
- **Caladea** (sustitución métrica libre para Cambria) — `SIL Open Font License 1.1`.
- **Liberation Sans** (sustitución métrica libre para Arial) — `SIL Open Font License 1.1`.
- **STIX Two Math** (tipografía matemática para ecuaciones OMML) — `SIL Open Font License 1.1`.

## 3. Diccionarios de Ortografía (`MPL-1.1` / `LGPL-3.0` disjuntiva)
- **RLA-ES (`es-CL`, `es-419`, `es-ES`)** — Se distribuye bajo **`Mozilla Public License 1.1` (`MPL-1.1`)**, seleccionada de `(GPL-3.0 OR LGPL-3.0 OR MPL-1.1)` para mantener compatibilidad con distribución en tiendas móviles (Google Play / Apple App Store).

## 4. Activos Visuales e Interfaz Clean-Room
- Todos los **408 íconos SVG**, **Android Vector Drawables (XML)** y **Apple XCAssets** (`office-suite/assets/icons/svg/`) fueron generados algorítmicamente mediante `office-suite/scripts/gen_icons.py` en estilo limpio equivalente a Fluent UI sin utilizar ni extraer binarios, marcas registradas ni activos con derechos de autor de Microsoft Corporation.

## 5. Dependencias Android y Apple
- **Jetpack Compose / AndroidX / Material 3** — `Apache-2.0`.
- **JNA (`net.java.dev.jna:jna:5.15.0`)** — `MIT OR Apache-2.0` (Apache-2.0 seleccionada).
- **Apple SDKs (SwiftUI, PencilKit, UniformTypeIdentifiers)** — Marcos nativos de sistema Apple.
