import Foundation
import XCTest

final class NeoCanvasManualScreenshotTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .landscapeLeft
        XCUIDevice.shared.appearance = .light
        app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_GB"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Gallery"].waitForExistence(timeout: 15))
    }

    func testCaptureManualScreenshotPack() throws {
        try capture("01-gallery-light.png")

        XCUIDevice.shared.appearance = .dark
        sleep(2)
        try capture("02-gallery-dark.png")
        XCUIDevice.shared.appearance = .light
        sleep(2)

        try tap("Kids activities")
        XCTAssertTrue(app.staticTexts["Back to Gallery"].waitForExistence(timeout: 8))
        try capture("10-kids-activities.png")
        try tap("Back to Gallery")
        XCTAssertTrue(app.staticTexts["Gallery"].waitForExistence(timeout: 8))

        try tap("Create artwork")
        XCTAssertTrue(app.staticTexts["New canvas"].waitForExistence(timeout: 8))
        try capture("03-new-canvas.png")
        try tap("Create")
        XCTAssertTrue(element("Drawing canvas").waitForExistence(timeout: 15))
        try capture("04-editor.png")

        try capturePanel(control: "Brush library", file: "05-brush-library.png")
        try capturePanel(control: "Open Colour Studio", file: "06-colour-studio.png")
        try capturePanel(control: "Layers", file: "07-layers.png")
        try capturePanel(control: "FX and adjustments", file: "08-fx-adjustments.png")
        try capturePanel(control: "Settings", file: "09-settings.png")
    }

    private func capturePanel(control: String, file: String) throws {
        try tap(control)
        sleep(1)
        try capture(file)
        try tap(control == "Settings" ? "Done" : control)
        sleep(1)
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func tap(_ identifier: String) throws {
        let target = element(identifier)
        XCTAssertTrue(target.waitForExistence(timeout: 8), "Missing accessible control: \(identifier)")
        target.tap()
    }

    private func capture(_ filename: String) throws {
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = filename
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
