import Testing
import Foundation
@testable import SilentFitKit

/// Validates the decoder against real captured transfers paired with the vendor parser's output.
/// Captures contain personal health data and are not committed; set SILENTFIT_CAPTURES to a
/// file produced by the reverse-engineering tools (see docs/REVERSE_ENGINEERING.md) to run it.
@Suite struct CapturedSyncTests {
    struct Expected: Codable {
        var type: Int?
        var day: Int?
        var month: Int?
        var steps: Int?
        var hourlySteps: [Int]?
        var sleepTotal: Int?
        var deep: Int?
        var standing: [Int]?
    }

    struct Capture: Codable {
        var idlist: String
        var stream: String
        var expected: [Expected]
    }

    @Test func decoderMatchesVendorParser() throws {
        guard let path = ProcessInfo.processInfo.environment["SILENTFIT_CAPTURES"] else { return }
        let captures = try JSONDecoder().decode([Capture].self, from: Data(contentsOf: URL(fileURLWithPath: path)))
        #expect(!captures.isEmpty)
        var checked = 0
        for capture in captures {
            let idUnits = BerryStreamParser().feed(ByteUtils.fromHex(capture.idlist))
            let first = try #require(idUnits.first)
            let message = try #require(WearMessage.parse(first.payload))
            let menu = try #require(message.body(14))
            let ids = FitnessDecoder.parseIdList(menu)
            #expect(!ids.isEmpty)

            let units = BerryStreamParser().feed(ByteUtils.fromHex(capture.stream))
            let file = try #require(units.last { $0.kind == BerryInboundKind.largeFile })
            var days: [String: HealthDay] = [:]
            _ = FitnessDecoder.decode(file: file.payload, ids: ids, into: &days)

            for exp in capture.expected {
                guard let type = exp.type, let day = exp.day, let month = exp.month else { continue }
                guard let match = days.values.first(where: { d in
                    let c = Calendar.current.dateComponents([.month, .day], from: d.date)
                    return c.month == month && c.day == day
                }) else { continue }
                if type == FitnessDecoder.typeDaily, let hourly = exp.hourlySteps {
                    #expect(match.hourlySteps == hourly)
                    #expect(match.steps == exp.steps)
                    checked += 1
                }
                if type == FitnessDecoder.typeSleep, let total = exp.sleepTotal {
                    #expect(match.sleep?.totalMinutes == total)
                    #expect(match.sleep?.deepMinutes == exp.deep)
                    checked += 1
                }
                if type == FitnessDecoder.typeStanding, let standing = exp.standing {
                    #expect(match.standingHours == standing)
                    checked += 1
                }
            }
        }
        #expect(checked > 0)
    }
}
