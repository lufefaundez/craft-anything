//! Headless parity runner for OfiOfi Chilean/global `.docx` corpus fixtures.

#![deny(
    clippy::unwrap_used,
    clippy::expect_used,
    clippy::panic,
    clippy::todo,
    clippy::unimplemented,
    clippy::unreachable
)]

use std::env;
use std::fs;
use std::path::PathBuf;
use std::process::ExitCode;

use serde_json::json;
use word_mobile_ffi::{MobileDocumentSession, TileRequest, init_mobile_assets};

fn main() -> ExitCode {
    let args: Vec<String> = env::args().skip(1).collect();
    let corpus_dir = args
        .first()
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("../corpus/word"));
    let fonts_dir = args
        .get(1)
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("../assets/fonts"));

    if fonts_dir.is_dir() {
        let _ = init_mobile_assets(fonts_dir.to_string_lossy().into_owned(), None);
    }

    let entries = match fs::read_dir(&corpus_dir) {
        Ok(rd) => rd,
        Err(e) => {
            eprintln!("failed to read corpus dir {}: {e}", corpus_dir.display());
            return ExitCode::FAILURE;
        }
    };

    let mut files: Vec<PathBuf> = entries
        .filter_map(Result::ok)
        .map(|e| e.path())
        .filter(|p| p.extension().and_then(|s| s.to_str()) == Some("docx"))
        .collect();
    files.sort();

    if files.is_empty() {
        eprintln!("no .docx files found in {}", corpus_dir.display());
        return ExitCode::FAILURE;
    }

    let mut docs_json = Vec::new();
    let mut all_ok = true;

    for path in files {
        let name = path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("unknown.docx")
            .to_string();
        let bytes = match fs::read(&path) {
            Ok(b) => b,
            Err(e) => {
                all_ok = false;
                docs_json.push(json!({"file": name, "ok": false, "error": e.to_string()}));
                continue;
            }
        };

        let session = match MobileDocumentSession::open_bytes(name.clone(), bytes, None) {
            Ok(s) => s,
            Err(e) => {
                all_ok = false;
                docs_json.push(json!({"file": name, "ok": false, "error": e.to_string()}));
                continue;
            }
        };

        let pages = session.page_count();
        let words = session.word_count();
        let size = match session.page_size_pt(0) {
            Ok(s) => s,
            Err(e) => {
                all_ok = false;
                docs_json.push(json!({"file": name, "ok": false, "error": e.to_string()}));
                continue;
            }
        };

        let tile_w = (size.width_pt.round() as u32).clamp(1, 2048);
        let tile_h = (size.height_pt.round() as u32).clamp(1, 2048);
        let rgba = match session.render_tile(TileRequest {
            page_index: 0,
            dpi_scale: 1.0,
            tile_x_px: 0,
            tile_y_px: 0,
            tile_w_px: tile_w,
            tile_h_px: tile_h,
            dark_page: false,
        }) {
            Ok(buf) => buf,
            Err(e) => {
                all_ok = false;
                docs_json.push(json!({"file": name, "ok": false, "error": e.to_string()}));
                continue;
            }
        };

        let non_white_px = rgba
            .as_chunks::<4>()
            .0
            .iter()
            .filter(|px| px[0] < 245 || px[1] < 245 || px[2] < 245)
            .count();

        let pdf_ok = session
            .export_pdf_bytes()
            .map(|b| b.starts_with(b"%PDF-"))
            .unwrap_or(false);

        let roundtrip_ok = session
            .save_docx_bytes()
            .and_then(|saved| MobileDocumentSession::open_bytes(name.clone(), saved, None))
            .map(|reopened| reopened.page_count() == pages && reopened.word_count() == words)
            .unwrap_or(false);

        let doc_ok = pages >= 1 && words > 0 && non_white_px > 20 && pdf_ok && roundtrip_ok;
        if !doc_ok {
            all_ok = false;
        }
        docs_json.push(json!({
            "file": name,
            "ok": doc_ok,
            "pages": pages,
            "words": words,
            "width_pt": size.width_pt,
            "height_pt": size.height_pt,
            "non_white_pixels": non_white_px,
            "pdf_ok": pdf_ok,
            "roundtrip_ok": roundtrip_ok
        }));
    }

    // Hostile input resistance check (corrupt zip, empty bytes, oversized tile)
    let corrupt_err = MobileDocumentSession::open_bytes("bad.docx".into(), b"not-a-zip".to_vec(), None).is_err();
    let empty_err = MobileDocumentSession::open_bytes("empty.docx".into(), Vec::new(), None).is_err();
    let blank = MobileDocumentSession::new_blank("es-CL".into());
    let tile_err = blank
        .render_tile(TileRequest {
            page_index: 0,
            dpi_scale: 1.0,
            tile_x_px: 0,
            tile_y_px: 0,
            tile_w_px: 99999,
            tile_h_px: 99999,
            dark_page: false,
        })
        .is_err();
    let hostile_ok = corrupt_err && empty_err && tile_err;
    if !hostile_ok {
        all_ok = false;
    }

    println!(
        "{}",
        json!({
            "ok": all_ok,
            "hostile_ok": hostile_ok,
            "documents": docs_json
        })
    );

    if all_ok { ExitCode::SUCCESS } else { ExitCode::FAILURE }
}
