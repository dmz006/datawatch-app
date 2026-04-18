// Pre-MVP iOS skeleton entry point. Sprint 1 Day 2 replaces with a working
// KMP-shared-framework integration. Until then, this file exists so the Xcode
// target has something to compile.

import SwiftUI

@main
struct DatawatchClientApp: App {
    var body: some Scene {
        WindowGroup {
            VStack {
                Text("datawatch")
                Text("iOS skeleton — content phase follows Android production")
                    .font(.caption)
            }
            .padding()
        }
    }
}
