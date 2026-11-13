import XCTest
@testable import OfficeShellKit

final class OfficeShellKitTests: XCTestCase {
    func testAllThreeWavesSupported() {
        let flavors = OfiOfiAppFlavor.allCases
        XCTAssertEqual(flavors.count, 3)
        XCTAssertEqual(OfiOfiAppFlavor.word.engineCrateName, "wordcraft")
        XCTAssertEqual(OfiOfiAppFlavor.excel.engineCrateName, "gridcraft")
        XCTAssertEqual(OfiOfiAppFlavor.powerpoint.engineCrateName, "deckcraft")
        XCTAssertEqual(OfiOfiAppFlavor.excel.accentHexLight, "#107C41")
        XCTAssertEqual(OfiOfiAppFlavor.powerpoint.accentHexLight, "#B7472A")
    }

    func testUserOwnedStorageOnly() {
        let labels = ShellStorageTarget.allCases.map(\.labelEsCl).joined(separator: " ").lowercased()
        XCTAssertFalse(labels.contains("ofiofi cloud"))
        XCTAssertTrue(ShellStorageTarget.allCases.contains(.webdav))
        XCTAssertTrue(ShellStorageTarget.allCases.contains(.googleDrive))
        XCTAssertTrue(ShellStorageTarget.allCases.contains(.icloudDrive))
    }
}
