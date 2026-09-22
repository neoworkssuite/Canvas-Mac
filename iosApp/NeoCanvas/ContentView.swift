import SwiftUI
import UIKit
import Foundation
import NeoCanvasKit

private final class UpdateResultBox: @unchecked Sendable {
    let callback: (String?, String?) -> Void

    init(_ callback: @escaping (String?, String?) -> Void) {
        self.callback = callback
    }
}

final class AppStoreUpdateLookup: NSObject, NativeUpdateLookup {
    func check(onResult: @escaping (String?, String?) -> Void) {
        guard let url = URL(string: "https://itunes.apple.com/lookup?bundleId=com.neoworksuite.neocanvas") else {
            onResult(nil, "Could not build App Store update URL.")
            return
        }

        let resultBox = UpdateResultBox(onResult)
        URLSession.shared.dataTask(with: url) { data, _, error in
            let json = data.flatMap { String(data: $0, encoding: .utf8) }
            let message = error?.localizedDescription
            DispatchQueue.main.async {
                if let message {
                    resultBox.callback(nil, message)
                } else if let json {
                    resultBox.callback(json, nil)
                } else {
                    resultBox.callback(nil, "App Store returned no update data.")
                }
            }
        }.resume()
    }
}

struct NeoCanvasComposeView: UIViewControllerRepresentable {
    private let updateLookup = AppStoreUpdateLookup()

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(updateLookup: updateLookup)
    }

    func updateUIViewController(
        _ uiViewController: UIViewController,
        context: Context
    ) {
    }
}

struct ContentView: View {
    var body: some View {
        NeoCanvasComposeView()
    }
}
