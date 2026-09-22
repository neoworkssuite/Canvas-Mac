import SwiftUI
import UIKit
import Foundation
import NeoCanvasKit

final class AppStoreUpdateLookup: NSObject, NativeUpdateLookup {
    func check(onResult: @escaping (String?, String?) -> Void) {
        guard let url = URL(string: "https://itunes.apple.com/lookup?bundleId=com.neoworksuite.neocanvas") else {
            onResult(nil, "Could not build App Store update URL.")
            return
        }

        URLSession.shared.dataTask(with: url) { data, _, error in
            let json = data.flatMap { String(data: $0, encoding: .utf8) }
            let message = error?.localizedDescription
            DispatchQueue.main.async {
                if let message {
                    onResult(nil, message)
                } else if let json {
                    onResult(json, nil)
                } else {
                    onResult(nil, "App Store returned no update data.")
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
