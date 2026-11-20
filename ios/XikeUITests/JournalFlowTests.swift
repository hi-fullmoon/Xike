import XCTest

@MainActor
final class JournalFlowTests: XCTestCase {
    func testDraftAndSavedEntrySurviveRelaunch() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let note = "Xike iPhone check " + String(UUID().uuidString.prefix(8))
        let mood = app.buttons["mood-NEUTRAL"]
        let editor = app.textViews["moment-note"]
        XCTAssertTrue(mood.waitForExistence(timeout: 15))
        mood.tap()
        editor.tap()
        editor.typeText(note)

        // Wait for the real journal write by leaving and returning through app navigation.
        app.buttons["tab-archive"].tap()
        XCTAssertTrue(app.textFields["archive-search"].waitForExistence(timeout: 5))
        app.terminate()
        app.launch()
        XCTAssertTrue(editor.waitForExistence(timeout: 10))
        XCTAssertTrue((editor.value as? String)?.contains(note) == true)

        let save = app.buttons["save-moment"]
        XCTAssertTrue(save.isEnabled)
        save.tap()
        XCTAssertTrue(app.staticTexts["Moment saved"].waitForExistence(timeout: 8))
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["tab-archive"].waitForExistence(timeout: 10))
        app.buttons["tab-archive"].tap()
        let result = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", note)).firstMatch
        XCTAssertTrue(result.waitForExistence(timeout: 8))
        capture(app, "Memories")
        result.tap()
        let favorite = app.buttons["favorite-entry"]
        XCTAssertTrue(favorite.waitForExistence(timeout: 8))
        favorite.tap()
        let favoriteSaved = NSPredicate(format: "label == %@", "Remove favorite")
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: favoriteSaved, object: favorite)],
                                     timeout: 6), .completed)
        app.buttons["Close"].firstMatch.tap()

        app.buttons["tab-insights"].tap()
        XCTAssertTrue(app.staticTexts["Mood trend"].waitForExistence(timeout: 8))
        capture(app, "Patterns")
        app.buttons["tab-settings"].tap()
        XCTAssertTrue(app.buttons["settings-language"].waitForExistence(timeout: 8))
        capture(app, "Settings")

        let export = app.buttons["settings-export"]
        for _ in 0..<7 where !export.isHittable { app.swipeUp() }
        XCTAssertTrue(export.isHittable)
        export.tap()
        XCTAssertTrue(app.secureTextFields["backup-password"].waitForExistence(timeout: 8))
    }

    private func capture(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
