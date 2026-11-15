import DepotCore
import Testing

@Suite struct VersionsTests {
    @Test func numericNotLexical() { #expect(Versions.compare("0.15.10", "0.15.2") > 0) }
    @Test func equalWithMissingSegments() { #expect(Versions.compare("1.2", "1.2.0") == 0) }
    @Test func lessThan() { #expect(Versions.compare("0.15.1", "0.15.2") < 0) }
    @Test func ignoresSuffixAndPrefix() { #expect(Versions.compare("v1.2.3", "1.2.3-debug") == 0) }
}
