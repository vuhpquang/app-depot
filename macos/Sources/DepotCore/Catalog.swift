import Foundation

/// The latest release of one catalog app (`GET /api/depot/catalog`).
public struct CatalogRelease: Codable, Equatable, Hashable, Sendable {
    public var version: String
    public var build: String?
    public var notes: String
    public var assetName: String
    public var downloadUrl: String
    public var sha256: String
    public var size: Int64
    public var publishedAt: String
    public var minOs: String?

    public init(version: String, build: String? = nil, notes: String = "", assetName: String, downloadUrl: String,
                sha256: String, size: Int64, publishedAt: String, minOs: String? = nil) {
        self.version = version
        self.build = build
        self.notes = notes
        self.assetName = assetName
        self.downloadUrl = downloadUrl
        self.sha256 = sha256
        self.size = size
        self.publishedAt = publishedAt
        self.minOs = minOs
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decode(String.self, forKey: .version)
        build = try c.decodeIfPresent(String.self, forKey: .build)
        notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        assetName = try c.decode(String.self, forKey: .assetName)
        downloadUrl = try c.decode(String.self, forKey: .downloadUrl)
        sha256 = try c.decode(String.self, forKey: .sha256)
        size = try c.decode(Int64.self, forKey: .size)
        publishedAt = try c.decode(String.self, forKey: .publishedAt)
        minOs = try c.decodeIfPresent(String.self, forKey: .minOs)
    }
}

/// One app of the catalog. Unknown keys are ignored; missing optional ones get Android's defaults.
public struct CatalogApp: Codable, Equatable, Hashable, Sendable, Identifiable {
    public var slug: String
    public var name: String
    public var tagline: String
    public var platform: String
    public var appId: String
    public var repo: String
    public var iconUrl: String?
    public var projectSlug: String?
    /// macOS: the code requirement a first install must satisfy (null → first install refused).
    public var signer: String?
    public var relaunchArgs: [String]
    /// `managed` | `selfUpdates` (Wisp) | `none` (DevKit).
    public var installMode: String
    public var latest: CatalogRelease?

    public var id: String { slug }

    public init(slug: String, name: String, tagline: String, platform: String, appId: String, repo: String,
                iconUrl: String? = nil, projectSlug: String? = nil, signer: String? = nil, relaunchArgs: [String] = [],
                installMode: String = "managed", latest: CatalogRelease? = nil) {
        self.slug = slug
        self.name = name
        self.tagline = tagline
        self.platform = platform
        self.appId = appId
        self.repo = repo
        self.iconUrl = iconUrl
        self.projectSlug = projectSlug
        self.signer = signer
        self.relaunchArgs = relaunchArgs
        self.installMode = installMode
        self.latest = latest
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        slug = try c.decode(String.self, forKey: .slug)
        name = try c.decode(String.self, forKey: .name)
        tagline = try c.decodeIfPresent(String.self, forKey: .tagline) ?? ""
        platform = try c.decode(String.self, forKey: .platform)
        appId = try c.decode(String.self, forKey: .appId)
        repo = try c.decode(String.self, forKey: .repo)
        iconUrl = try c.decodeIfPresent(String.self, forKey: .iconUrl)
        projectSlug = try c.decodeIfPresent(String.self, forKey: .projectSlug)
        signer = try c.decodeIfPresent(String.self, forKey: .signer)
        relaunchArgs = try c.decodeIfPresent([String].self, forKey: .relaunchArgs) ?? []
        installMode = try c.decodeIfPresent(String.self, forKey: .installMode) ?? "managed"
        latest = try c.decodeIfPresent(CatalogRelease.self, forKey: .latest)
    }

    public var isManaged: Bool { installMode == "managed" }
}

public enum InstallMode {
    public static let managed = "managed"
    public static let selfUpdates = "selfUpdates"
    public static let none = "none"
}
