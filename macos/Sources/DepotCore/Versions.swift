import Foundation

/// Version comparison, the same rules as Android's `compareVersions`.
public enum Versions {
    private static func segments(_ v: String) -> [Int] {
        var s = v.trimmingCharacters(in: .whitespaces)
        if s.hasPrefix("v") { s.removeFirst() }
        if let i = s.firstIndex(where: { $0 == "-" || $0 == "+" }) { s = String(s[..<i]) }
        return s.split(separator: ".", omittingEmptySubsequences: false).map { seg in
            Int(seg.prefix(while: \.isNumber)) ?? 0
        }
    }

    /// Numeric segment comparison; missing segments count as 0, `v` prefix and `-`/`+` suffixes ignored.
    public static func compare(_ a: String, _ b: String) -> Int {
        let x = segments(a), y = segments(b)
        for i in 0..<max(x.count, y.count) {
            let l = i < x.count ? x[i] : 0, r = i < y.count ? y[i] : 0
            if l != r { return l < r ? -1 : 1 }
        }
        return 0
    }
}
