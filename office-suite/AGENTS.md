# AGENTS.md — Guía de Ejecución para Agentes de Desarrollo (Gemini 3.8 Flash / Multi-Agente)

Este directorio (`office-suite/`) contiene el **Plan Maestro, Especificaciones Visuales y Tracker de Tareas** para construir una suite ofimática gratuita, de código abierto y completa (`.docx`, `.xlsx`, `.pptx`) para **Android (Smartphone + Tablet), iOS (iPhone), iPadOS (iPad) y visionOS (Apple Vision Pro)**, partiendo por **Word**.

## Reglas obligatorias antes de escribir código

1. **Una tarea atómica por turno:**
   - Lee `office-suite/TRACKER.json`.
   - Toma únicamente la primera tarea con `"status": "todo"` cuyas `"depends_on"` estén todas en `"done"`.
   - Lee su descripción en `office-suite/ROADMAP-WORD.md` y **solo** los archivos listados en `"context_files"`.
2. **Clean-room estricto:**
   - Nunca copies código, binarios, XML internos, fuentes propietarias ni íconos extraídos de Microsoft Office.
   - Aprende solo de la especificación observada (`office-suite/specs/word-ui-es-16.115.md`), estándares públicos (ECMA-376 / ODF 1.3) y código Apache-2.0/MIT del ecosistema ArtCraft (`storytold/wordcraft`, `gridcraft`, `deckcraft`, `craft-libs`, `craft-fonts`).
3. **Never Crash (en todo código Rust):**
   - Prohibido `unwrap()`, `expect()`, `panic!()`, `todo!()`, `unimplemented!()`, `unreachable!()` e indexación directa sobre datos de entrada en código de producción.
   - Todo crate Rust nuevo debe declarar en `src/lib.rs`:
     ```rust
     #![deny(clippy::unwrap_used, clippy::expect_used, clippy::panic, clippy::todo, clippy::unimplemented, clippy::unreachable)]
     ```
4. **Cero versiones recortadas en móvil:**
   - Smartphones (`< 600dp` / Compact) muestran el 100 % de las herramientas mediante un **Bottom Sheet Ribbon** navegable por las 9 pestañas y búsqueda de comandos.
   - Tablets / iPad / Vision Pro muestran el **Ribbon completo** visualmente equivalente a Microsoft Word.
5. **Verificación real obligatoria:**
   - Ninguna tarea puede marcarse como `"done"` en `TRACKER.json` sin ejecutar su `"verify_cmd"` y observar código de salida `0`.
   - No inventes salidas de tests ni rutas no verificadas.
6. **Privacidad y rutas:**
   - Nunca escribas rutas absolutas de usuario local (`$HOME` literal), secretos, tokens ni datos personales en archivos del repositorio.

## Flujo de cierre de cada turno

1. Ejecutar `"verify_cmd"` de la tarea actual.
2. Actualizar `office-suite/TRACKER.json` (`"status": "done"`, `"completed_at": "YYYY-MM-DD"`, `"verification_note": "..."`).
3. Hacer commit con formato `[W-XXX] <resumen corto>` sin mezclar cambios de otras tareas.
