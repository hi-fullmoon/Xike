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
        let localReview = app.buttons["local-review"]
        scrollTo(localReview, in: app)
        localReview.tap()
        XCTAssertTrue(app.staticTexts["Period overview"].waitForExistence(timeout: 5))
        let sources = app.buttons.matching(identifier: "local-review-source").firstMatch
        scrollTo(sources, in: app)
        sources.tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS %@", note)).firstMatch.waitForExistence(timeout: 5))
        app.navigationBars["View this period’s entries"].buttons["Close"].tap()
        app.navigationBars["Local review"].buttons["Close"].tap()

        app.buttons["tab-settings"].tap()
        XCTAssertTrue(app.buttons["settings-language"].waitForExistence(timeout: 8))
        capture(app, "Settings")

        let export = app.buttons["settings-export"]
        for _ in 0..<7 where !export.isHittable { app.swipeUp() }
        XCTAssertTrue(export.isHittable)
        export.tap()
        XCTAssertTrue(app.secureTextFields["backup-password"].waitForExistence(timeout: 8))
    }

    func testResetTimeAndDiscardDraftAcrossRelaunch() {
        continueAfterFailure = false
        let app = launchEnglish()
        let mood = app.buttons["mood-NEUTRAL"]
        XCTAssertTrue(mood.waitForExistence(timeout: 15))
        mood.tap()
        let time = app.buttons["entry-time"]
        scrollTo(time, in: app)
        time.tap()
        let done = app.buttons["Done"]
        XCTAssertTrue(done.waitForExistence(timeout: 5))
        done.tap()
        let save = app.buttons["save-moment"]
        XCTAssertEqual(save.label, "Save this past moment")
        time.tap()
        let now = app.buttons["Use current time"]
        XCTAssertTrue(now.waitForExistence(timeout: 5))
        now.tap()
        XCTAssertEqual(save.label, "Save this moment")

        let discard = app.buttons["discard-draft"]
        for _ in 0..<4 where !discard.isHittable { app.swipeDown() }
        XCTAssertTrue(discard.isHittable)
        discard.tap()
        let confirm = app.buttons.matching(NSPredicate(format: "label == %@ AND identifier != %@", "Discard draft", "discard-draft")).firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: 5))
        confirm.tap()
        XCTAssertTrue(discard.waitForNonExistence(timeout: 5))
        app.terminate()
        app.launch()
        XCTAssertTrue(save.waitForExistence(timeout: 10))
        XCTAssertFalse(save.isEnabled)
        XCTAssertFalse(discard.exists)
    }

    func testAdvancedSettingsOpenFromPageTitles() {
        continueAfterFailure = false
        let app = launchEnglish()
        XCTAssertTrue(app.buttons["tab-insights"].waitForExistence(timeout: 15))
        app.buttons["tab-insights"].tap()
        let insights = app.buttons["ai-review-entry"]
        XCTAssertTrue(insights.waitForExistence(timeout: 5))
        insights.tap(withNumberOfTaps: 3, numberOfTouches: 1)
        let configure = app.buttons["Configure AI service"]
        XCTAssertTrue(configure.waitForExistence(timeout: 5))
        configure.tap()
        XCTAssertTrue(app.secureTextFields["API Key"].waitForExistence(timeout: 5))
        app.buttons["Close"].firstMatch.tap()
        app.buttons["Close"].firstMatch.tap()

        app.buttons["tab-moment"].tap()
        let outside = app.buttons["amap-settings-entry"]
        scrollTo(outside, in: app)
        outside.tap(withNumberOfTaps: 3, numberOfTouches: 1)
        XCTAssertTrue(app.navigationBars["AMap location settings"].waitForExistence(timeout: 5))
    }

    func testSearchIndexAfterUpgradingExistingJournal() throws {
        guard ProcessInfo.processInfo.environment["XIKE_VERIFY_UPGRADE"] == "1" else {
            throw XCTSkip("First run the previous release’s journal flow test on the upgrade simulator.")
        }
        continueAfterFailure = false
        let app = launchEnglish()
        XCTAssertTrue(app.buttons["tab-archive"].waitForExistence(timeout: 15))
        app.buttons["tab-archive"].tap()
        let search = app.textFields["archive-search"]
        XCTAssertTrue(search.waitForExistence(timeout: 5))
        search.tap()
        search.typeText("iPh")
        let previousEntry = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Xike iPhone check")).firstMatch
        XCTAssertTrue(previousEntry.waitForExistence(timeout: 8))
        app.buttons["Clear search"].tap()
        search.typeText("Phone")
        XCTAssertTrue(app.staticTexts["No matching entries"].waitForExistence(timeout: 8))
        XCTAssertFalse(previousEntry.exists)
        if app.keyboards.buttons["Search"].exists { app.keyboards.buttons["Search"].tap() }
        app.buttons["archive-filter-toggle"].tap()
        XCTAssertTrue(app.buttons["archive-date-preset"].waitForExistence(timeout: 5))
    }

    private func launchEnglish() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    private func scrollTo(_ item: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<8 where !item.isHittable { app.swipeUp() }
        XCTAssertTrue(item.isHittable)
    }

    private func capture(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
