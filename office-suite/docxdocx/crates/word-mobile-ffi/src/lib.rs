//! UniFFI mobile and spatial bridge for OfiOfi DocxDocx.
//!
//! Exposes a thread-safe `MobileDocumentSession` backed by `wordcraft-engine`,
//! tile-based RGBA8888 page rendering via `vello_cpu`, touch hit-testing,
//! IME text input, command execution, and PDF export.

#![deny(
    clippy::unwrap_used,
    clippy::expect_used,
    clippy::panic,
    clippy::todo,
    clippy::unimplemented,
    clippy::unreachable
)]

use std::path::PathBuf;

use kurbo::Affine;
use parking_lot::Mutex;
use serde_json::{Value, json};
use wordcraft_doc::StoryRef;
use wordcraft_engine::{Selection, Session, blank_document, io, proof};
use wordcraft_fonts::FontDb;
use wordcraft_geom::Region;
use wordcraft_render::{RenderOptions, render_region};

uniffi::setup_scaffolding!();

/// Registers mobile-bundled open metric fonts (`fonts_dir`) and optional language packs
/// (`packs_dir`, e.g., `es` for Chilean/Latin American Spanish) with the core engine.
/// Returns the number of loaded user font faces plus installed language packs.
#[uniffi::export]
pub fn init_mobile_assets(fonts_dir: String, packs_dir: Option<String>) -> Result<u32, MobileFfiError> {
    let mut loaded_count = 0u32;
    let trimmed_fonts = fonts_dir.trim();
    if !trimmed_fonts.is_empty() {
        let fpath = PathBuf::from(trimmed_fonts);
        if !fpath.is_dir() {
            return Err(MobileFfiError::IoFailed {
                detail: format!("fonts_dir is not a directory: {trimmed_fonts}"),
            });
        }
        let (fonts, _errs) = FontDb::global().load_user_fonts(&fpath);
        loaded_count = loaded_count.saturating_add(u32::try_from(fonts.len()).unwrap_or(u32::MAX));
    }
    if let Some(pdir) = packs_dir.filter(|s| !s.trim().is_empty()) {
        let ppath = PathBuf::from(pdir.trim());
        if !ppath.is_dir() {
            return Err(MobileFfiError::IoFailed {
                detail: format!("packs_dir is not a directory: {}", ppath.display()),
            });
        }
        proof::packs::set_packs_dir(Some(ppath));
        loaded_count = loaded_count.saturating_add(u32::try_from(proof::packs::installed().len()).unwrap_or(u32::MAX));
    }
    Ok(loaded_count)
}

/// Maximum tile dimension accepted across FFI to prevent OOM on hostile inputs.
const MAX_TILE_SIDE: u32 = 4096;
/// Maximum IME delete-surrounding steps per call.
const MAX_DELETE_SURROUNDING: u32 = 128;

/// Loads all OS-installed system font families into the engine database and returns
/// the sorted list of available family names (bundled + system + user-added).
#[uniffi::export]
pub fn load_and_list_system_fonts() -> Vec<String> {
    let db = FontDb::global();
    let _ = db.load_system_fonts();
    db.families()
}

/// Page dimensions, section margins, and paragraph indents in points (`1 cm = 28.3465 pt`).
#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct RulerMetrics {
    pub width_pt: f32,
    pub height_pt: f32,
    pub margin_top_pt: f32,
    pub margin_bottom_pt: f32,
    pub margin_left_pt: f32,
    pub margin_right_pt: f32,
    pub para_left_pt: f32,
    pub para_right_pt: f32,
    pub para_first_line_pt: f32,
}

/// Error returned across the FFI boundary without panicking.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum MobileFfiError {
    #[error("invalid JSON parameters: {detail}")]
    InvalidJson { detail: String },
    #[error("document I/O failed: {detail}")]
    IoFailed { detail: String },
    #[error("command failed: {detail}")]
    CommandFailed { detail: String },
    #[error("invalid page index {index} (page count: {count})")]
    InvalidPage { index: u32, count: u32 },
    #[error("invalid tile dimensions {width}x{height} at scale {scale}")]
    InvalidTile { width: u32, height: u32, scale: f32 },
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct PageSizePt {
    pub width_pt: f32,
    pub height_pt: f32,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct TileRequest {
    pub page_index: u32,
    pub dpi_scale: f32,
    pub tile_x_px: u32,
    pub tile_y_px: u32,
    pub tile_w_px: u32,
    pub tile_h_px: u32,
    pub dark_page: bool,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct CaretRect {
    pub page_index: u32,
    pub x_pt: f32,
    pub top_pt: f32,
    pub width_pt: f32,
    pub height_pt: f32,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct PageSelectionRect {
    pub page_index: u32,
    pub x_pt: f32,
    pub y_pt: f32,
    pub width_pt: f32,
    pub height_pt: f32,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct SelectionGeometry {
    pub caret: Option<CaretRect>,
    pub selection_rects: Vec<PageSelectionRect>,
    pub is_collapsed: bool,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct CommandOutcome {
    pub result_json: String,
    pub format_state_json: String,
    pub dirty: bool,
    pub can_undo: bool,
    pub can_redo: bool,
    pub page_count: u32,
    pub word_count: u64,
    pub status: String,
    pub selection: SelectionGeometry,
}

/// Thread-safe mobile/spatial session wrapping `wordcraft_engine::Session`.
#[derive(uniffi::Object)]
pub struct MobileDocumentSession {
    inner: Mutex<Session>,
}

#[uniffi::export]
impl MobileDocumentSession {
    /// Creates a blank document configured for a BCP-47 region tag (e.g., `"es-CL"`, `"en-US"`).
    #[uniffi::constructor]
    pub fn new_blank(region_tag: String) -> Self {
        let region = parse_region(&region_tag);
        let session = Session::new(blank_document(&region));
        Self { inner: Mutex::new(session) }
    }

    /// Opens a document from raw bytes and a hint filename (e.g., `"oficio.docx"`).
    #[uniffi::constructor]
    pub fn open_bytes(file_name: String, bytes: Vec<u8>, password: Option<String>) -> Result<Self, MobileFfiError> {
        let (doc, _encrypted) =
            io::open_bytes_with(&file_name, &bytes, password.as_deref()).map_err(|detail| MobileFfiError::IoFailed { detail })?;
        let mut session = Session::new(doc);
        session.file_name = Some(file_name);
        Ok(Self { inner: Mutex::new(session) })
    }

    /// Opens a document from a filesystem path.
    #[uniffi::constructor]
    pub fn open_path(path: String, password: Option<String>) -> Result<Self, MobileFfiError> {
        let fs_path = std::path::Path::new(&path);
        let (doc, _encrypted) =
            io::open_path_with(fs_path, password.as_deref()).map_err(|detail| MobileFfiError::IoFailed { detail })?;
        let mut session = Session::new(doc);
        session.path = Some(fs_path.to_path_buf());
        if let Some(name) = fs_path.file_name().and_then(|n| n.to_str()) {
            session.file_name = Some(name.to_string());
        }
        Ok(Self { inner: Mutex::new(session) })
    }

    /// Saves the active document as `.docx` bytes.
    pub fn save_docx_bytes(&self) -> Result<Vec<u8>, MobileFfiError> {
        let s = self.inner.lock();
        io::save_bytes("document.docx", &s.doc).map_err(|detail| MobileFfiError::IoFailed { detail })
    }

    /// Exports the active document to PDF bytes via `wordcraft_pdf`.
    pub fn export_pdf_bytes(&self) -> Result<Vec<u8>, MobileFfiError> {
        let s = self.inner.lock();
        wordcraft_pdf::export(&s.doc, &Default::default()).map_err(|e| MobileFfiError::IoFailed { detail: e.to_string() })
    }

    pub fn is_dirty(&self) -> bool {
        self.inner.lock().dirty
    }

    pub fn word_count(&self) -> u64 {
        self.inner.lock().word_count() as u64
    }

    pub fn page_count(&self) -> u32 {
        let mut s = self.inner.lock();
        let lay = s.layout();
        u32::try_from(lay.pages.len()).unwrap_or(u32::MAX)
    }

    pub fn page_size_pt(&self, page_index: u32) -> Result<PageSizePt, MobileFfiError> {
        let mut s = self.inner.lock();
        let lay = s.layout();
        let count = u32::try_from(lay.pages.len()).unwrap_or(u32::MAX);
        let page = lay.pages.get(page_index as usize).ok_or(MobileFfiError::InvalidPage { index: page_index, count })?;
        Ok(PageSizePt { width_pt: page.w, height_pt: page.h })
    }

    /// Renders a tile specified by `req` into straight RGBA8888 bytes.
    pub fn render_tile(&self, req: TileRequest) -> Result<Vec<u8>, MobileFfiError> {
        if !req.dpi_scale.is_finite()
            || req.dpi_scale <= 0.0
            || req.tile_w_px == 0
            || req.tile_h_px == 0
            || req.tile_w_px > MAX_TILE_SIDE
            || req.tile_h_px > MAX_TILE_SIDE
        {
            return Err(MobileFfiError::InvalidTile {
                width: req.tile_w_px,
                height: req.tile_h_px,
                scale: req.dpi_scale,
            });
        }
        let mut s = self.inner.lock();
        let lay = s.layout();
        let count = u32::try_from(lay.pages.len()).unwrap_or(u32::MAX);
        let page = lay
            .pages
            .get(req.page_index as usize)
            .ok_or(MobileFfiError::InvalidPage { index: req.page_index, count })?;

        let view = Affine::translate((-f64::from(req.tile_x_px), -f64::from(req.tile_y_px)))
            * Affine::scale(f64::from(req.dpi_scale));
        let opts = RenderOptions { dark: req.dark_page, ..RenderOptions::default() };
        let rendered = render_region(&s.doc, page, req.tile_w_px, req.tile_h_px, view, &opts);
        Ok(rendered.to_straight())
    }

    /// Executes any `wordcraft-engine` command by ID and JSON parameters, returning
    /// the updated document metrics, selection geometry, and `format.state` snapshot.
    pub fn execute_command(&self, cmd_id: String, params_json: String) -> Result<CommandOutcome, MobileFfiError> {
        let params: Value = if params_json.trim().is_empty() {
            Value::Object(Default::default())
        } else {
            serde_json::from_str(&params_json).map_err(|e| MobileFfiError::InvalidJson { detail: e.to_string() })?
        };
        let mut s = self.inner.lock();
        let result = s.run(&cmd_id, &params).map_err(|e| MobileFfiError::CommandFailed { detail: e.to_string() })?;
        Ok(build_outcome(&mut s, &result))
    }

    /// Places the caret at `(x_pt, y_pt)` on `page_index`.
    pub fn tap_at(&self, page_index: u32, x_pt: f32, y_pt: f32) -> Result<SelectionGeometry, MobileFfiError> {
        let mut s = self.inner.lock();
        if let Some(pos) = hit_page(&mut s, page_index, x_pt, y_pt)? {
            s.sel = Selection::caret(pos);
            s.page_hint = page_index as usize;
        }
        Ok(compute_selection_geometry(&mut s))
    }

    /// Selects the word at `(x_pt, y_pt)` on `page_index`.
    pub fn double_tap_at(&self, page_index: u32, x_pt: f32, y_pt: f32) -> Result<SelectionGeometry, MobileFfiError> {
        let mut s = self.inner.lock();
        if let Some(pos) = hit_page(&mut s, page_index, x_pt, y_pt)? {
            s.sel = Selection::caret(pos);
            s.page_hint = page_index as usize;
            let _ = s.run("select.word", &json!({}));
        }
        Ok(compute_selection_geometry(&mut s))
    }

    /// Selects the paragraph at `(x_pt, y_pt)` on `page_index`.
    pub fn triple_tap_at(&self, page_index: u32, x_pt: f32, y_pt: f32) -> Result<SelectionGeometry, MobileFfiError> {
        let mut s = self.inner.lock();
        if let Some(pos) = hit_page(&mut s, page_index, x_pt, y_pt)? {
            s.sel = Selection::caret(pos);
            s.page_hint = page_index as usize;
            let _ = s.run("select.paragraph", &json!({}));
        }
        Ok(compute_selection_geometry(&mut s))
    }

    /// Moves either the start (`is_anchor == true`) or end (`is_anchor == false`) selection handle.
    pub fn drag_selection_handle(
        &self,
        is_anchor: bool,
        page_index: u32,
        x_pt: f32,
        y_pt: f32,
    ) -> Result<SelectionGeometry, MobileFfiError> {
        let mut s = self.inner.lock();
        if let Some(pos) = hit_page(&mut s, page_index, x_pt, y_pt)?
            && pos.story == s.sel.focus.story
        {
            if is_anchor {
                s.sel.anchor = pos;
            } else {
                s.sel.focus = pos;
                s.page_hint = page_index as usize;
            }
        }
        Ok(compute_selection_geometry(&mut s))
    }

    /// Commits text from the platform IME (`InputConnection` / `UITextInput`) as an IME edit.
    pub fn ime_commit_text(&self, text: String, join_with_previous: bool) -> Result<CommandOutcome, MobileFfiError> {
        let mut s = self.inner.lock();
        if join_with_previous {
            s.join_next_ime_text_replacement();
        }
        let result = s
            .run_ime("text.insert", &json!({ "text": text }))
            .map_err(|e| MobileFfiError::CommandFailed { detail: e.to_string() })?;
        Ok(build_outcome(&mut s, &result))
    }

    /// Deletes `before` characters backward and `after` characters forward around the caret for IME sync.
    pub fn ime_delete_surrounding(&self, before: u32, after: u32) -> Result<CommandOutcome, MobileFfiError> {
        let b = before.min(MAX_DELETE_SURROUNDING);
        let a = after.min(MAX_DELETE_SURROUNDING);
        let mut s = self.inner.lock();
        let mut last = Value::Null;
        for _ in 0..b {
            last = s
                .run_ime("text.backspace", &json!({}))
                .map_err(|e| MobileFfiError::CommandFailed { detail: e.to_string() })?;
        }
        for _ in 0..a {
            last = s
                .run_ime("text.delete", &json!({}))
                .map_err(|e| MobileFfiError::CommandFailed { detail: e.to_string() })?;
        }
        Ok(build_outcome(&mut s, &last))
    }

    /// Returns the current caret and selection highlight rectangles without mutating state.
    pub fn get_selection_geometry(&self) -> SelectionGeometry {
        let mut s = self.inner.lock();
        compute_selection_geometry(&mut s)
    }

    /// Returns exact page dimensions, section margins, and active paragraph indents in points.
    pub fn get_ruler_metrics(&self, page_index: u32) -> Result<RulerMetrics, MobileFfiError> {
        let mut s = self.inner.lock();
        let (width_pt, height_pt) = {
            let lay = s.layout();
            let count = u32::try_from(lay.pages.len()).unwrap_or(u32::MAX);
            let page = lay.pages.get(page_index as usize).ok_or(MobileFfiError::InvalidPage { index: page_index, count })?;
            (page.w, page.h)
        };
        let sec_idx = match s.sel.focus.story {
            StoryRef::Body => s.doc.section_index_of(s.sel.focus.path.0.first().copied().unwrap_or(0) as usize),
            _ => 0,
        };
        let sect = s.doc.sections().get(sec_idx).map(|(_, x)| (*x).clone()).unwrap_or_default();
        let p_props = s.doc.para_at(&s.sel.focus).map(|p| p.props.clone()).unwrap_or_default();
        let rp = s.doc.styles.resolve_para(&p_props);
        Ok(RulerMetrics {
            width_pt,
            height_pt,
            margin_top_pt: sect.margin_top,
            margin_bottom_pt: sect.margin_bottom,
            margin_left_pt: sect.margin_left,
            margin_right_pt: sect.margin_right,
            para_left_pt: rp.indent_left,
            para_right_pt: rp.indent_right,
            para_first_line_pt: rp.indent_first,
        })
    }
}

fn hit_page(s: &mut Session, page_index: u32, x_pt: f32, y_pt: f32) -> Result<Option<wordcraft_doc::Pos>, MobileFfiError> {
    if !x_pt.is_finite() || !y_pt.is_finite() {
        return Ok(None);
    }
    let lay = s.layout();
    let count = u32::try_from(lay.pages.len()).unwrap_or(u32::MAX);
    if (page_index as usize) >= lay.pages.len() {
        return Err(MobileFfiError::InvalidPage { index: page_index, count });
    }
    let story = lay.story_at(page_index as usize, x_pt, y_pt).unwrap_or(StoryRef::Body);
    Ok(lay.hit(page_index as usize, x_pt, y_pt, story))
}

fn parse_region(tag: &str) -> Region {
    let trimmed = tag.trim();
    if trimmed.is_empty() {
        return Region::default();
    }
    let mut parts = trimmed.split(['-', '_']);
    let language = parts.next().unwrap_or_default().to_ascii_lowercase();
    let territory = parts.next().unwrap_or_default().to_ascii_uppercase();
    Region { language, territory, ..Region::default() }
}

fn compute_selection_geometry(s: &mut Session) -> SelectionGeometry {
    let focus = s.sel.focus.clone();
    let anchor = s.sel.anchor.clone();
    let is_collapsed = s.sel.is_collapsed();
    let page_hint = s.page_hint;
    let lay = s.layout();
    let caret = lay.caret_on(&focus, page_hint).map(|c| CaretRect {
        page_index: u32::try_from(c.page).unwrap_or(0),
        x_pt: c.x,
        top_pt: c.top,
        width_pt: c.width,
        height_pt: c.height,
    });
    let selection_rects = if is_collapsed {
        Vec::new()
    } else {
        lay.selection_rects(&s.doc, &anchor, &focus, page_hint)
            .into_iter()
            .map(|(pi, r)| PageSelectionRect {
                page_index: u32::try_from(pi).unwrap_or(0),
                x_pt: r.x,
                y_pt: r.y,
                width_pt: r.w,
                height_pt: r.h,
            })
            .collect()
    };
    SelectionGeometry { caret, selection_rects, is_collapsed }
}

fn build_outcome(s: &mut Session, result: &Value) -> CommandOutcome {
    let fmt = s.run("format.state", &Value::Object(Default::default())).unwrap_or(Value::Null);
    let selection = compute_selection_geometry(s);
    let page_count = {
        let lay = s.layout();
        u32::try_from(lay.pages.len()).unwrap_or(u32::MAX)
    };
    CommandOutcome {
        result_json: result.to_string(),
        format_state_json: fmt.to_string(),
        dirty: s.dirty,
        can_undo: s.can_undo(),
        can_redo: s.can_redo(),
        page_count,
        word_count: s.word_count() as u64,
        status: s.status.clone(),
        selection,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn open_save_roundtrip_and_page_count() {
        let session = MobileDocumentSession::new_blank("es-CL".to_string());
        assert_eq!(session.page_count(), 1);
        assert!(!session.is_dirty());

        let out = session
            .execute_command("text.insert".to_string(), r#"{"text":"Oficio Ordinario N° 123"}"#.to_string())
            .ok();
        assert!(out.is_some());
        assert!(session.is_dirty());
        assert_eq!(session.word_count(), 4);

        let docx = session.save_docx_bytes();
        assert!(docx.as_ref().is_ok_and(|b| b.starts_with(b"PK")));

        if let Ok(bytes) = docx {
            let reopened = MobileDocumentSession::open_bytes("oficio.docx".to_string(), bytes, None);
            assert!(reopened.is_ok());
            if let Ok(r) = reopened {
                assert_eq!(r.page_count(), 1);
                assert_eq!(r.word_count(), 4);
            }
        }
    }

    #[test]
    fn test_render_tile_non_empty() {
        let session = MobileDocumentSession::new_blank("es-CL".to_string());
        let _ = session.execute_command("text.insert".to_string(), r#"{"text":"Hola OfiOfi DocxDocx"}"#.to_string());
        let tile = session.render_tile(TileRequest {
            page_index: 0,
            dpi_scale: 1.5,
            tile_x_px: 0,
            tile_y_px: 0,
            tile_w_px: 256,
            tile_h_px: 256,
            dark_page: false,
        });
        assert!(tile.is_ok());
        if let Ok(px) = tile {
            assert_eq!(px.len(), 256 * 256 * 4);
            assert!(px.iter().any(|&b| b < 250), "expected rendered glyph pixels on white page");
        }
    }

    #[test]
    fn test_command_bold_and_state() {
        let session = MobileDocumentSession::new_blank("es-CL".to_string());
        let _ = session.execute_command("text.insert".to_string(), r#"{"text":"Texto"}"#.to_string());
        let _ = session.execute_command("select.all".to_string(), "{}".to_string());
        let outcome = session.execute_command("format.bold".to_string(), "{}".to_string());
        assert!(outcome.is_ok());
        if let Ok(o) = outcome {
            assert!(o.can_undo);
            let parsed: Value = serde_json::from_str(&o.format_state_json).unwrap_or(Value::Null);
            assert_eq!(parsed.get("bold").and_then(Value::as_bool), Some(true));
            assert!(!o.selection.is_collapsed);
            assert!(!o.selection.selection_rects.is_empty());
        }
    }

    #[test]
    fn test_touch_hit_and_handles() {
        let session = MobileDocumentSession::new_blank("es-CL".to_string());
        let _ = session.execute_command("text.insert".to_string(), r#"{"text":"Primera palabra"}"#.to_string());
        let caret = session.get_selection_geometry().caret;
        assert!(caret.is_some());
        if let Some(c) = caret {
            // Double tap near the first word inside the page margin.
            let sel = session.double_tap_at(0, (c.x_pt - 30.0).max(72.0), c.top_pt + 4.0);
            assert!(sel.is_ok());
            if let Ok(g) = sel {
                assert!(!g.is_collapsed);
                assert!(!g.selection_rects.is_empty());
            }
            let tapped = session.tap_at(0, c.x_pt, c.top_pt + 2.0);
            assert!(tapped.as_ref().is_ok_and(|g| g.is_collapsed));
        }
    }

    #[test]
    fn test_ime_spanish_accents_and_undo() {
        let session = MobileDocumentSession::new_blank("es-CL".to_string());
        let _ = session.ime_commit_text("Configuracion".to_string(), false);
        let _ = session.ime_delete_surrounding(2, 0);
        let out = session.ime_commit_text("ón".to_string(), true);
        assert!(out.is_ok());
        assert_eq!(session.word_count(), 1);
        let _ = session.execute_command("edit.undo".to_string(), "{}".to_string());
        // Undo rolls back the joined replacement cleanly without panicking.
        assert!(session.page_count() == 1);
    }

    #[test]
    fn test_export_pdf_bytes() {
        let session = MobileDocumentSession::new_blank("es-CL".to_string());
        let _ = session.execute_command("text.insert".to_string(), r#"{"text":"Export PDF test"}"#.to_string());
        let pdf = session.export_pdf_bytes();
        assert!(pdf.as_ref().is_ok_and(|b| b.starts_with(b"%PDF-")));
    }

    #[test]
    fn hostile_inputs_do_not_panic() {
        let bad_open = MobileDocumentSession::open_bytes("broken.docx".to_string(), vec![0xFF, 0x00, 0x13], None);
        assert!(bad_open.is_err());

        let session = MobileDocumentSession::new_blank("".to_string());
        assert!(
            session
                .render_tile(TileRequest {
                    page_index: 99,
                    dpi_scale: 1.0,
                    tile_x_px: 0,
                    tile_y_px: 0,
                    tile_w_px: 128,
                    tile_h_px: 128,
                    dark_page: false,
                })
                .is_err()
        );
        assert!(
            session
                .render_tile(TileRequest {
                    page_index: 0,
                    dpi_scale: f32::NAN,
                    tile_x_px: 0,
                    tile_y_px: 0,
                    tile_w_px: 128,
                    tile_h_px: 128,
                    dark_page: false,
                })
                .is_err()
        );
        assert!(
            session
                .render_tile(TileRequest {
                    page_index: 0,
                    dpi_scale: 1.0,
                    tile_x_px: 0,
                    tile_y_px: 0,
                    tile_w_px: 0,
                    tile_h_px: 128,
                    dark_page: false,
                })
                .is_err()
        );
        assert!(
            session
                .render_tile(TileRequest {
                    page_index: 0,
                    dpi_scale: 1.0,
                    tile_x_px: 0,
                    tile_y_px: 0,
                    tile_w_px: 99999,
                    tile_h_px: 128,
                    dark_page: false,
                })
                .is_err()
        );
    }

    #[test]
    fn test_metric_font_substitution_calibri_aptos() {
        assert!(wordcraft_fonts::word::metric_compatible("Calibri", "Carlito"));
        assert!(wordcraft_fonts::word::metric_compatible("Cambria", "Caladea"));
        assert!(wordcraft_fonts::word::metric_compatible("Arial", "Liberation Sans"));
        assert!(wordcraft_fonts::word::metric_compatible("Times New Roman", "Liberation Serif"));

        let assets_root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../assets");
        let fonts_dir = assets_root.join("fonts");
        let packs_dir = assets_root.join("language-packs");
        let loaded = init_mobile_assets(
            fonts_dir.to_string_lossy().into_owned(),
            Some(packs_dir.to_string_lossy().into_owned()),
        );
        assert!(loaded.is_ok(), "init_mobile_assets failed: {loaded:?}");

        let packs = proof::packs::installed();
        assert!(packs.iter().any(|p| p.locale == "es"), "expected es language pack installed");
        assert!(proof::packs::load_errors().is_empty(), "unexpected pack errors: {:?}", proof::packs::load_errors());

        // Create blank document in es-CL, switch font to Calibri and Aptos, type accented Spanish, render tile.
        let session = MobileDocumentSession::new_blank("es-CL".to_string());
        assert!(session.execute_command("format.font".to_string(), r#"{"name":"Calibri"}"#.to_string()).is_ok());
        assert!(session.execute_command("text.insert".to_string(), r#"{"text":"Prueba métrica Calibri y Aptos"}"#.to_string()).is_ok());
        assert!(session.execute_command("select.all".to_string(), "{}".to_string()).is_ok());
        assert!(session.execute_command("format.font".to_string(), r#"{"name":"Aptos"}"#.to_string()).is_ok());
        let tile = session.render_tile(TileRequest {
            page_index: 0,
            dpi_scale: 1.0,
            tile_x_px: 0,
            tile_y_px: 0,
            tile_w_px: 256,
            tile_h_px: 256,
            dark_page: false,
        });
        assert!(tile.as_ref().is_ok_and(|b| b.len() == 256 * 256 * 4));
    }

    #[test]
    fn test_ruler_metrics_and_system_fonts() {
        let fonts = load_and_list_system_fonts();
        assert!(!fonts.is_empty(), "expected bundled or system fonts");

        let session = MobileDocumentSession::new_blank("es-CL".to_string());
        let initial = session.get_ruler_metrics(0);
        assert!(initial.as_ref().is_ok_and(|m| m.width_pt > 500.0 && m.height_pt > 600.0));

        let margin_cmd = session.execute_command(
            "layout.margins".to_string(),
            r#"{"top":56.69,"bottom":56.69,"left":70.87,"right":42.52}"#.to_string(),
        );
        assert!(margin_cmd.is_ok(), "layout.margins failed: {margin_cmd:?}");

        let indent_cmd = session.execute_command(
            "para.indents".to_string(),
            r#"{"left":28.35,"right":14.17,"firstLine":14.17}"#.to_string(),
        );
        assert!(indent_cmd.is_ok(), "para.indents failed: {indent_cmd:?}");

        let updated = session.get_ruler_metrics(0);
        assert!(updated.as_ref().is_ok_and(|m| {
            (m.margin_top_pt - 56.69).abs() < 0.25
                && (m.margin_bottom_pt - 56.69).abs() < 0.25
                && (m.margin_left_pt - 70.87).abs() < 0.25
                && (m.margin_right_pt - 42.52).abs() < 0.25
                && (m.para_left_pt - 28.35).abs() < 0.25
                && (m.para_right_pt - 14.17).abs() < 0.25
                && (m.para_first_line_pt - 14.17).abs() < 0.25
        }));
    }
}
