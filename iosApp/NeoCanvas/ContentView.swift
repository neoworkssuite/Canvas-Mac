import SwiftUI
import UIKit
import NeoCanvasKit

struct NeoCanvasComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
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
            .ignoresSafeArea()
    }
}