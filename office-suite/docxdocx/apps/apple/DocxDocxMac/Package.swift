// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "DocxDocxMac",
    defaultLocalization: "es-CL",
    platforms: [
        .macOS(.v14),
    ],
    dependencies: [
        .package(path: "../Packages/OfficeCoreKit"),
    ],
    targets: [
        .executableTarget(
            name: "DocxDocxMac",
            dependencies: [
                .product(name: "OfficeCoreKit", package: "OfficeCoreKit"),
            ],
        ),
    ],
)
