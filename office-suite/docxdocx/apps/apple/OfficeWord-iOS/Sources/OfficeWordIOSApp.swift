import SwiftUI

@main
public struct OfficeWordIOSApp: App {
    @State private var store = IOSDocxStore()

    public init() {}

    public var body: some Scene {
        WindowGroup {
            AdaptiveIOSShellView(store: store)
                .onOpenURL { url in
                    store.openSecurityScopedUrl(url)
                }
        }
    }
}
