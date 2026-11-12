# Roadmap Detallado — Aplicación 1: Word (Fases F0–F8)

Diseñado para ejecución incremental por **Gemini 3.8 Flash** (1 tarea = 1 turno).

---

## Fase F0 — Toolchains y Base Rust

### `[W-001]` Instalar Rust y targets cruzados Android / iOS / visionOS
- **Contexto a leer:** `office-suite/AGENTS.md`
- **Acción:** Instalar `rustup`, toolchain estable, `rustfmt`, `clippy`, `cargo-ndk`, `uniffi` CLI y los targets `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`, `aarch64-apple-ios`, `aarch64-apple-ios-sim`, `aarch64-apple-visionos`, `aarch64-apple-visionos-sim`.
- **Verificación (`verify_cmd`):** `rustc --version && cargo ndk --version && rustup target list --installed | grep -E "android|ios|visionos"`

### `[W-002]` Configurar repositorio núcleo de Word (`wordcraft` L0–L5)
- **Contexto a leer:** `office-suite/PLAN-MAESTRO.md`
- **Acción:** Crear o enlazar el fork/módulo núcleo basado en `storytold/wordcraft` (Apache-2.0/MIT) en un workspace hermano o subproyecto limpio y correr tests base L0–L4.
- **Verificación (`verify_cmd`):** `cargo test -p wordcraft-doc -p wordcraft-docx -p wordcraft-layout -p wordcraft-engine`

### `[W-003]` Verificar compilación cruzada sin dependencias desktop (`egui`/`winit`)
- **Contexto a leer:** `Cargo.toml`, `crates/engine/Cargo.toml`, `crates/render/Cargo.toml` (del repo núcleo)
- **Acción:** Auditar features de `wordcraft-engine` y `wordcraft-render` para compilar limpiamente hacia Android ARM64 e iOS ARM64 sin `eframe`, `rfd` ni `arboard`.
- **Verificación (`verify_cmd`):** `cargo check -p wordcraft-engine -p wordcraft-render --target aarch64-linux-android && cargo check -p wordcraft-engine -p wordcraft-render --target aarch64-apple-ios`

---

## Fase F1 — Puente UniFFI (`crates/mobile-ffi`)

### `[W-004]` Scaffold de `crates/mobile-ffi` con `UniFFI`
- **Acción:** Crear `crates/mobile-ffi` con `MobileDocumentSession` (`open_bytes`, `open_path`, `new_blank`, `save_docx_bytes`, `export_pdf_bytes`, `page_count`, `page_size_pt`, `is_dirty`). Cero `unwrap`/`expect`/`panic`.
- **Verificación (`verify_cmd`):** `cargo test -p word-mobile-ffi`

### `[W-005]` Renderizador de tiles RGBA8888 (`render_tile`) e invalidación incremental
- **Acción:** Implementar renderizado por regiones (`tile_x, tile_y, tile_w, tile_h, dpi_scale`) sobre `vello_cpu` reutilizando el `DisplayList` cacheado por página; invalidar solo páginas modificadas.
- **Verificación (`verify_cmd`):** `cargo test -p word-mobile-ffi test_render_tile_non_empty`

### `[W-006]` Puente de comandos y estado vivo de formato (`execute_command`)
- **Acción:** Exponer `execute_command(cmd_id, params_json) -> CommandOutcome` devolviendo `dirty_pages`, `can_undo`, `can_redo`, `caret_rect`, `selection_rects` y `format_state_json` (fuente, tamaño, negrita, cursiva, subrayado, alineación, estilo activo, conteo de palabras/páginas).
- **Verificación (`verify_cmd`):** `cargo test -p word-mobile-ffi test_command_bold_and_state`

### `[W-007]` Hit-testing táctil, handles de selección e IME (`preedit`/`commit`)
- **Acción:** Exponer `tap_at`, `double_tap_at`, `triple_tap_at`, `drag_selection_handle`, `ime_set_composing`, `ime_commit_text`, `ime_delete_surrounding` con undo atómico por palabra/composición.
- **Verificación (`verify_cmd`):** `cargo test -p word-mobile-ffi test_ime_spanish_accents_and_undo`

### `[W-008]` Scripts de empaquetado `jniLibs` (Android) y `XCFramework` (Apple)
- **Acción:** Agregar tareas `xtask mobile-android` y `xtask mobile-apple` generando `.so` + `word_mobile_ffi.kt` y `WordMobileFFI.xcframework` + `word_mobile_ffi.swift`.
- **Verificación (`verify_cmd`):** `cargo xtask mobile-android --check && cargo xtask mobile-apple --check`

---

## Fase F2 — Sistema de Diseño Office-Parity

### `[W-009]` Schema declarativo `specs/ribbon-schema.json` (100 % comandos)
- **Contexto a leer:** `office-suite/specs/word-ui-es-16.115.md`
- **Acción:** Generar JSON validado con las 9 pestañas base + 7 contextuales + QAT + Barra de estado, mapeando cada control a su `command_id` real en `wordcraft_engine::Registry`, con etiquetas `es-CL` y `en-US`.
- **Verificación (`verify_cmd`):** `python3 office-suite/scripts/validate_ribbon_schema.py`

### `[W-010]` Generador de tokens Fluent Light/Dark para Compose y SwiftUI
- **Contexto a leer:** `office-suite/specs/fluent-tokens.json`
- **Acción:** Script generador que produce `OfficeTokens.kt` y `OfficeTokens.swift` garantizando identidad cromática exacta entre plataformas.
- **Verificación (`verify_cmd`):** `python3 office-suite/scripts/gen_tokens.py --check`

### `[W-011]` Catálogo de íconos vectoriales originales (Clean-Room)
- **Acción:** Generar biblioteca SVG original (estilo trazo 1.5px + acentos de color Office) y compilar a Android Vector Drawables y Apple Asset Catalog.
- **Verificación (`verify_cmd`):** `python3 office-suite/scripts/check_icons_coverage.py`

### `[W-012]` Empaquetado de fuentes métricas libres y diccionarios `es-CL`/`es-419`
- **Acción:** Empaquetar `Carlito`, `Caladea`, `Liberation*`, `STIX Two Math` y diccionario `craft-proof` español/inglés en los assets móviles.
- **Verificación (`verify_cmd`):** `cargo test -p word-mobile-ffi test_metric_font_substitution_calibri_aptos`

---

## Fase F3 — Word Android MVP+ (Smartphone + Tablet)

### `[W-013]` Scaffold Android Compose (`apps/android/`)
- **Acción:** Crear proyecto Gradle Kotlin + Jetpack Compose + Material 3 cargando `libword_mobile_ffi.so` y `OfficeTokens.kt`.
- **Verificación (`verify_cmd`):** `./gradlew :app:assembleDebug`

### `[W-014]` Canvas virtualizado por tiles con pinch-to-zoom (`DocumentCanvas.kt`)
- **Acción:** Renderizador de páginas con caché LRU de `Bitmap`, zoom 50 %–400 %, scroll inercial, sombra de hoja y toggle *Vista Móvil (Reflow)* / *Diseño de Impresión*.
- **Verificación (`verify_cmd`):** `./gradlew :app:testDebugUnitTest`

### `[W-015]` Cursor, handles táctiles y puente `InputConnection` (`WordInputView.kt`)
- **Acción:** Integración nativa con teclados Android (composición, tildes, ñ, dictado), lupa de selección, doble tap palabra, triple tap párrafo y menú contextual flotante.
- **Verificación (`verify_cmd`):** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.suite.InputTests`

### `[W-016]` Shell Adaptativo: Tablet Full Ribbon vs Smartphone Bottom Sheet
- **Acción:** `WindowWidthSizeClass.Expanded` renderiza la tarjeta Ribbon flotante blanca + QAT + barra de estado idéntica a Word 16.115; `Compact` renderiza barra rápida sobre teclado + Bottom Sheet completo con las 9 pestañas y búsqueda de comandos.
- **Verificación (`verify_cmd`):** `./gradlew :app:testDebugUnitTest --tests "*AdaptiveShellTest*"`

### `[W-017]` Backstage Archivo + Storage Access Framework (SAF)
- **Acción:** Pantalla Inicio, plantillas (Blanco, Carta, Oficio Chile, Informe), abrir/guardar `.docx`, `.doc`, `.odt`, `.rtf`, exportar `.pdf` vía Android SAF.
- **Verificación (`verify_cmd`):** `./gradlew :app:testDebugUnitTest --tests "*DocumentStorageTest*"`

---

## Fase F4 — Word Android Paridad Total (100 % Herramientas)

### `[W-018]` Pestañas `Inicio`, `Insertar`, `Diseño`, `Disposición` al 100 %
- **Acción:** Galerías en vivo de Estilos, Temas, Cuadrícula táctil de Tablas, Cámara/Galería, Formas, Márgenes, Orientación, Tamaño Carta/Oficio/A4, Columnas.
- **Verificación (`verify_cmd`):** `./gradlew :app:testDebugUnitTest --tests "*RibbonCoreTabsTest*"`

### `[W-019]` Pestañas `Referencias`, `Correspondencia`, `Revisar`, `Vista` y Contextuales
- **Acción:** Notas al pie, TOC, Citas, Control de cambios, Comentarios, Revisión ortográfica, Buscar/Reemplazar, Vistas múltiples, Reglas H/V interactivas en Tablet.
- **Verificación (`verify_cmd`):** `./gradlew :app:testDebugUnitTest --tests "*RibbonAdvancedTabsTest*"`

### `[W-020]` Pestaña `Dibujar` (S-Pen/Stylus) y Diálogos Modales Completos
- **Acción:** Plumas, borrador, lazo, Ink to Shape y diálogos completos (Fuente, Párrafo, Tabulaciones, Propiedades de tabla, Configurar página).
- **Verificación (`verify_cmd`):** `./gradlew :app:testDebugUnitTest --tests "*DialogsAndInkTest*"`

### `[W-021]` Impresión Nativa (`PrintDocumentAdapter`) y Atajos de Teclado
- **Acción:** Integración con `PrintManager` usando PDF de `krilla` + tabla de atajos `Ctrl+*` en hardware keyboards.
- **Verificación (`verify_cmd`):** `./gradlew :app:testDebugUnitTest --tests "*PrintAdapterTest*"`

---

## Fase F5 — Word Apple: iOS (iPhone) + iPadOS (iPad)

### `[W-022]` Proyecto Xcode Multiplataforma (`apps/apple/`) + `WordMobileFFI.xcframework`
- **Acción:** Configurar workspace SwiftUI con targets iOS, iPadOS y visionOS compartiendo `OfficeCoreKit` y `OfficeDesignSystem`.
- **Verificación (`verify_cmd`):** `xcodebuild -scheme OfficeWord-iOS -destination 'generic/platform=iOS Simulator' build`

### `[W-023]` Canvas Tile + `UITextInput` Nativo (iOS / iPadOS)
- **Acción:** `UIScrollView` a 120 Hz con tiles Rust y protocolo `UITextInput` completo (autocorrección iOS, dictado, teclados externos).
- **Verificación (`verify_cmd`):** `xcodebuild test -scheme OfficeWord-iOS -destination 'platform=iOS Simulator,name=iPhone 16 Pro'`

### `[W-024]` Shell iPad (Full Ribbon + Apple Pencil) e iPhone (Bottom Sheet Completo) + Files/AirPrint
- **Acción:** Ribbon idéntico en iPad + `PencilKit` para pestaña Dibujar; Bottom Sheet de 9 pestañas en iPhone; `UIDocument` para iCloud Drive/Files y AirPrint.
- **Verificación (`verify_cmd`):** `xcodebuild test -scheme OfficeWord-iOS -destination 'platform=iPad Pro 13-inch (M4)'`

---

## Fase F6 — Word visionOS (Apple Vision Pro)

### `[W-025]` Interfaz Espacial visionOS (`Ornaments`, Multi-ventana y Modo Concentración)
- **Acción:** Implementar `OfficeWord-visionOS` con ventana `.plain`, **Top Ornament** (Ribbon completo flotante), **Bottom Ornament** (Estado/Zoom/Vistas), multi-ventana para comparar documentos y soporte Look+Pinch+Teclado.
- **Verificación (`verify_cmd`):** `xcodebuild -scheme OfficeWord-visionOS -destination 'platform=visionOS Simulator,name=Apple Vision Pro' build`

---

## Fase F7 — QA Visual Automatizado con `craft-anything`

### `[W-026]` Corpus de Paridad Chileno/Global y Diff Visual contra Word 16.115
- **Acción:** Construir suite automatizada usando `craft-anything` que renderiza corpus de `.docx` complejos (oficios, tablas anidadas, ecuaciones, encabezados) y verifica layout, colores, fidelidad visual y resistencia a entradas hostiles.
- **Verificación (`verify_cmd`):** `cargo test --workspace && python3 office-suite/scripts/parity_report.py --min-score 0.95`

---

## Fase F8 — Release y Expansión a Excel & PowerPoint

### `[W-027]` Empaquetado Release (APK reproducible, IPA/TestFlight, Licencias)
- **Acción:** Configurar builds release, `ATTRIBUTION.md`, verificación de licencias Apache-2.0/MIT y notas de publicación.
- **Verificación (`verify_cmd`):** `./gradlew :app:assembleRelease`

### `[W-028]` Extracción de `OfficeShellKit` para Ola 2 (Excel) y Ola 3 (PowerPoint)
- **Acción:** Generalizar componentes de Ribbon/BottomSheet/Backstage/TileCanvas para conectar `storytold/gridcraft` (`.xlsx`) y `storytold/deckcraft` (`.pptx`).
- **Verificación (`verify_cmd`):** `python3 office-suite/scripts/verify_shell_abstraction.py`
