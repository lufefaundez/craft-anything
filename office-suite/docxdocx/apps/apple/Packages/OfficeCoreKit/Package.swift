// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "OfficeCoreKit",
    defaultLocalization: "es-CL",
    platforms: [
        .iOS(.v17),
        .visionOS(.v1),
        .macOS(.v14),
    ],
    products: [
        .library(
            name: "OfficeCoreKit",
            targets: ["OfficeCoreKit"],
        ),
    ],
    targets: [
        .binaryTarget(
            name: "word_mobile_ffiFFI",
            path: "../../Frameworks/WordMobileFFI.xcframework",
        ),
        .target(
            name: "OfficeCoreKit",
            dependencies: ["word_mobile_ffiFFI"],
            resources: [
                .process("Resources/OfficeIcons.xcassets"),
                .copy("Resources/fonts"),
                .copy("Resources/language-packs"),
                .copy("Resources/ribbon-schema.json"),
            ],
        ),
        .testTarget(
            name: "OfficeCoreKitTests",
            dependencies: ["OfficeCoreKit"],
        ),
    ],
)
