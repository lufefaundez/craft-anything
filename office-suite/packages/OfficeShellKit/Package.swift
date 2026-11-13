// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "OfficeShellKit",
    defaultLocalization: "es-CL",
    platforms: [
        .iOS(.v17),
        .visionOS(.v1),
        .macOS(.v14),
    ],
    products: [
        .library(
            name: "OfficeShellKit",
            targets: ["OfficeShellKit"],
        ),
    ],
    targets: [
        .target(
            name: "OfficeShellKit",
        ),
        .testTarget(
            name: "OfficeShellKitTests",
            dependencies: ["OfficeShellKit"],
        ),
    ],
)
