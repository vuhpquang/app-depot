import Foundation

/// Lowercase, without diacritics (Vietnamese đ included), so "ghi chu" finds "Ghi chú".
public func foldForSearch(_ text: String) -> String {
    text.replacingOccurrences(of: "đ", with: "d").replacingOccurrences(of: "Đ", with: "D")
        .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US_POSIX"))
        .lowercased()
}

/// Blank matches everything; otherwise a case- and accent-insensitive substring of the name or tagline.
public func matchesQuery(_ app: CatalogApp, _ query: String) -> Bool {
    let q = foldForSearch(query.trimmingCharacters(in: .whitespaces))
    if q.isEmpty { return true }
    return foldForSearch(app.name).contains(q) || foldForSearch(app.tagline).contains(q)
}
