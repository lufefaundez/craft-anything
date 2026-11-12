//! Build and packaging tasks for DocxDocx mobile FFI targets (Android jniLibs + Apple XCFramework).

use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, ExitCode};

fn main() -> ExitCode {
    if let Err(err) = run() {
        eprintln!("xtask error: {err}");
        return ExitCode::FAILURE;
    }
    ExitCode::SUCCESS
}

fn run() -> Result<(), String> {
    let args: Vec<String> = env::args().skip(1).collect();
    let Some(subcmd) = args.first().map(String::as_str) else {
        return Err(usage());
    };
    let flags = &args[1..];
    let check_only = flags.iter().any(|a| a == "--check");
    let release = flags.iter().any(|a| a == "--release");
    let all_abis = flags.iter().any(|a| a == "--all-abis");
    let sim_only = flags.iter().any(|a| a == "--sim-only");

    let root = workspace_root()?;
    match subcmd {
        "mobile-android" => build_android(&root, check_only, release, all_abis),
        "mobile-apple" => build_apple(&root, check_only, release, sim_only),
        "-h" | "--help" | "help" => {
            println!("{}", usage());
            Ok(())
        }
        other => Err(format!("unknown xtask subcommand '{other}'\n{}", usage())),
    }
}

fn usage() -> String {
    "Usage:\n  cargo xtask mobile-android [--check] [--release] [--all-abis]\n  cargo xtask mobile-apple   [--check] [--release] [--sim-only]"
        .to_string()
}

fn workspace_root() -> Result<PathBuf, String> {
    let manifest_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    manifest_dir
        .parent()
        .map(Path::to_path_buf)
        .ok_or_else(|| "cannot resolve docxdocx workspace root".to_string())
}

fn resolve_tool(name: &str) -> Result<PathBuf, String> {
    if let Ok(path_var) = env::var("PATH") {
        for dir in env::split_paths(&path_var) {
            let candidate = dir.join(name);
            if candidate.is_file() {
                return Ok(candidate);
            }
        }
    }
    if let Ok(home) = env::var("HOME") {
        for extra in [".cargo/bin", "/opt/homebrew/bin"] {
            let dir = if extra.starts_with('/') {
                PathBuf::from(extra)
            } else {
                PathBuf::from(&home).join(extra)
            };
            let candidate = dir.join(name);
            if candidate.is_file() {
                return Ok(candidate);
            }
        }
    }
    Err(format!("required tool '{name}' not found in PATH, ~/.cargo/bin, or /opt/homebrew/bin"))
}

fn resolve_android_sdk() -> Result<(PathBuf, PathBuf), String> {
    let sdk = env::var_os("ANDROID_HOME")
        .or_else(|| env::var_os("ANDROID_SDK_ROOT"))
        .map(PathBuf::from)
        .or_else(|| env::var_os("HOME").map(|h| PathBuf::from(h).join("Library/Android/sdk")))
        .ok_or_else(|| "cannot determine ANDROID_HOME".to_string())?;
    if !sdk.is_dir() {
        return Err(format!("ANDROID_HOME does not exist: {}", sdk.display()));
    }

    if let Some(ndk_env) = env::var_os("ANDROID_NDK_HOME") {
        let ndk = PathBuf::from(ndk_env);
        if ndk.is_dir() {
            return Ok((sdk, ndk));
        }
    }

    let ndk_root = sdk.join("ndk");
    let mut versions: Vec<PathBuf> = fs::read_dir(&ndk_root)
        .map_err(|e| format!("failed to read {}: {e}", ndk_root.display()))?
        .filter_map(Result::ok)
        .map(|e| e.path())
        .filter(|p| p.is_dir())
        .collect();
    versions.sort();
    let ndk = versions
        .pop()
        .ok_or_else(|| format!("no NDK version installed under {}", ndk_root.display()))?;
    Ok((sdk, ndk))
}

fn run_cmd(cmd: &mut Command, label: &str) -> Result<(), String> {
    let status = cmd
        .status()
        .map_err(|e| format!("failed to spawn {label}: {e}"))?;
    if !status.success() {
        return Err(format!("{label} exited with status {status}"));
    }
    Ok(())
}

fn verify_non_empty(path: &Path) -> Result<u64, String> {
    let meta = fs::metadata(path).map_err(|e| format!("missing output {}: {e}", path.display()))?;
    if meta.len() == 0 {
        return Err(format!("empty output file: {}", path.display()));
    }
    Ok(meta.len())
}

fn copy_dir_recursive(src: &Path, dst: &Path) -> Result<usize, String> {
    if !src.is_dir() {
        return Ok(0);
    }
    fs::create_dir_all(dst).map_err(|e| format!("failed to create {}: {e}", dst.display()))?;
    let mut copied = 0usize;
    let entries = fs::read_dir(src).map_err(|e| format!("failed to read {}: {e}", src.display()))?;
    for entry in entries {
        let entry = entry.map_err(|e| format!("dir entry error in {}: {e}", src.display()))?;
        let name = entry.file_name();
        let src_path = entry.path();
        let dst_path = dst.join(&name);
        if src_path.is_dir() {
            copied += copy_dir_recursive(&src_path, &dst_path)?;
        } else if src_path.is_file() {
            fs::copy(&src_path, &dst_path)
                .map_err(|e| format!("failed to copy {} -> {}: {e}", src_path.display(), dst_path.display()))?;
            copied += 1;
        }
    }
    Ok(copied)
}

fn stage_shared_assets(root: &Path, target_assets_dir: &Path) -> Result<usize, String> {
    let shared_assets = root.join("../assets");
    let mut count = 0usize;
    for sub in ["fonts", "language-packs"] {
        count += copy_dir_recursive(&shared_assets.join(sub), &target_assets_dir.join(sub))?;
    }
    Ok(count)
}

fn build_android(
    root: &Path,
    check_only: bool,
    release: bool,
    all_abis: bool,
) -> Result<(), String> {
    let (_sdk, ndk) = resolve_android_sdk()?;
    let cargo = resolve_tool("cargo")?;
    let _cargo_ndk = resolve_tool("cargo-ndk")?;
    let uniffi_bindgen = resolve_tool("uniffi-bindgen")?;

    let abis: Vec<(&str, &str)> = if check_only && !all_abis {
        vec![("arm64-v8a", "aarch64-linux-android")]
    } else {
        vec![
            ("arm64-v8a", "aarch64-linux-android"),
            ("armeabi-v7a", "armv7-linux-androideabi"),
            ("x86_64", "x86_64-linux-android"),
        ]
    };

    let jni_libs = root.join("apps/android/app/src/main/jniLibs");
    fs::create_dir_all(&jni_libs)
        .map_err(|e| format!("failed to create {}: {e}", jni_libs.display()))?;

    let mut cmd = Command::new(&cargo);
    cmd.current_dir(root)
        .env("ANDROID_NDK_HOME", &ndk)
        .arg("ndk");
    for (abi, _) in &abis {
        cmd.arg("-t").arg(abi);
    }
    cmd.arg("-o").arg(&jni_libs).arg("build").arg("-p").arg("word-mobile-ffi");
    if release {
        cmd.arg("--release");
    }
    run_cmd(&mut cmd, "cargo ndk build word-mobile-ffi")?;

    let profile = if release { "release" } else { "debug" };
    let primary_so = jni_libs.join("arm64-v8a/libword_mobile_ffi.so");
    let so_bytes = verify_non_empty(&primary_so)?;

    let kt_out = root.join("apps/android/app/src/main/java");
    fs::create_dir_all(&kt_out)
        .map_err(|e| format!("failed to create {}: {e}", kt_out.display()))?;
    let mut bind = Command::new(uniffi_bindgen);
    bind.current_dir(root)
        .args(["generate", "--library"])
        .arg(&primary_so)
        .args(["--language", "kotlin", "--out-dir"])
        .arg(&kt_out);
    run_cmd(&mut bind, "uniffi-bindgen kotlin")?;

    let kt_file = kt_out.join("uniffi/word_mobile_ffi/word_mobile_ffi.kt");
    let kt_bytes = verify_non_empty(&kt_file)?;
    let android_assets = root.join("apps/android/app/src/main/assets");
    let staged = stage_shared_assets(root, &android_assets)?;

    println!(
        "mobile-android ({profile}) OK: {} ({} KB), Kotlin {} KB, {} asset files staged, ABIs: {}",
        primary_so.display(),
        so_bytes / 1024,
        kt_bytes / 1024,
        staged,
        abis.iter().map(|(a, _)| *a).collect::<Vec<_>>().join(", ")
    );
    Ok(())
}

fn build_apple(
    root: &Path,
    check_only: bool,
    release: bool,
    sim_only: bool,
) -> Result<(), String> {
    let cargo = resolve_tool("cargo")?;
    let xcodebuild = resolve_tool("xcodebuild")?;
    let uniffi_bindgen = resolve_tool("uniffi-bindgen")?;

    let targets: Vec<&str> = if sim_only {
        vec!["aarch64-apple-ios-sim", "aarch64-apple-visionos-sim"]
    } else {
        vec![
            "aarch64-apple-ios",
            "aarch64-apple-ios-sim",
            "aarch64-apple-visionos",
            "aarch64-apple-visionos-sim",
        ]
    };

    let profile = if release { "release" } else { "debug" };
    for target in &targets {
        let mut cmd = Command::new(&cargo);
        cmd.current_dir(root)
            .args(["build", "-p", "word-mobile-ffi", "--target", target]);
        if release {
            cmd.arg("--release");
        }
        run_cmd(&mut cmd, &format!("cargo build --target {target}"))?;
    }

    let first_lib = root
        .join("target")
        .join(targets[0])
        .join(profile)
        .join("libword_mobile_ffi.a");
    let _ = verify_non_empty(&first_lib)?;

    let swift_stage = root.join("target/uniffi-apple-stage");
    if swift_stage.exists() {
        fs::remove_dir_all(&swift_stage)
            .map_err(|e| format!("failed to clean {}: {e}", swift_stage.display()))?;
    }
    fs::create_dir_all(&swift_stage)
        .map_err(|e| format!("failed to create {}: {e}", swift_stage.display()))?;

    let mut bind = Command::new(uniffi_bindgen);
    bind.current_dir(root)
        .args(["generate", "--library"])
        .arg(&first_lib)
        .args(["--language", "swift", "--out-dir"])
        .arg(&swift_stage);
    run_cmd(&mut bind, "uniffi-bindgen swift")?;

    let generated_dir = root.join("apps/apple/Packages/OfficeCoreKit/Sources/OfficeCoreKit/Generated");
    fs::create_dir_all(&generated_dir)
        .map_err(|e| format!("failed to create {}: {e}", generated_dir.display()))?;
    let swift_src = swift_stage.join("word_mobile_ffi.swift");
    let swift_dst = generated_dir.join("word_mobile_ffi.swift");
    fs::copy(&swift_src, &swift_dst)
        .map_err(|e| format!("failed to copy swift bindings: {e}"))?;
    let swift_bytes = verify_non_empty(&swift_dst)?;

    let headers_dir = swift_stage.join("headers");
    fs::create_dir_all(&headers_dir)
        .map_err(|e| format!("failed to create headers dir: {e}"))?;
    fs::copy(
        swift_stage.join("word_mobile_ffiFFI.h"),
        headers_dir.join("word_mobile_ffiFFI.h"),
    )
    .map_err(|e| format!("failed to copy C header: {e}"))?;
    fs::copy(
        swift_stage.join("word_mobile_ffiFFI.modulemap"),
        headers_dir.join("module.modulemap"),
    )
    .map_err(|e| format!("failed to copy module.modulemap: {e}"))?;

    let frameworks_dir = root.join("apps/apple/Frameworks");
    fs::create_dir_all(&frameworks_dir)
        .map_err(|e| format!("failed to create {}: {e}", frameworks_dir.display()))?;
    let xcfw_path = frameworks_dir.join("WordMobileFFI.xcframework");
    if xcfw_path.exists() {
        fs::remove_dir_all(&xcfw_path)
            .map_err(|e| format!("failed to remove old xcframework: {e}"))?;
    }

    let mut xcb = Command::new(xcodebuild);
    xcb.current_dir(root).arg("-create-xcframework");
    for target in &targets {
        let lib = root
            .join("target")
            .join(target)
            .join(profile)
            .join("libword_mobile_ffi.a");
        let _ = verify_non_empty(&lib)?;
        xcb.arg("-library").arg(&lib).arg("-headers").arg(&headers_dir);
    }
    xcb.arg("-output").arg(&xcfw_path);
    run_cmd(&mut xcb, "xcodebuild -create-xcframework")?;

    let plist = xcfw_path.join("Info.plist");
    let _ = verify_non_empty(&plist)?;
    let apple_resources = root.join("apps/apple/Packages/OfficeCoreKit/Sources/OfficeCoreKit/Resources");
    let staged = stage_shared_assets(root, &apple_resources)?;

    println!(
        "mobile-apple ({profile}, check={check_only}) OK: {} ({} slices), Swift {} KB, {} resource files staged",
        xcfw_path.display(),
        targets.len(),
        swift_bytes / 1024,
        staged
    );
    Ok(())
}
