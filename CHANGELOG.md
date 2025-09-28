# Changelog
## [Unreleased]
### Changed
- Client version updated on [5.4.3](https://github.com/reportportal/client-java/releases/tag/5.4.3), by @HardNorth
- Replace "jsr305" annotations with "jakarta.annotation-api", by @HardNorth
- Switch on use of `Instant` class instead of `Date` to get more timestamp precision, by @HardNorth

## [5.3.10]
### Changed
- Client version updated on [5.3.17](https://github.com/reportportal/client-java/releases/tag/5.3.17), by @HardNorth

## [5.3.9]
### Changed
- Features now finish on the last scenario finish, not on the end of the Launch, by @HardNorth
- Client version updated on [5.3.16](https://github.com/reportportal/client-java/releases/tag/5.3.16), by @HardNorth

## [5.3.8]
### Fixed
- Issue [#13](https://github.com/reportportal/agent-java-cucumber7/issues/13): Attempt to finish not started features, by @HardNorth

## [5.3.7]
### Changed
- Client version updated on [5.3.15](https://github.com/reportportal/client-java/releases/tag/5.3.15), by @HardNorth

## [5.3.6]
### Fixed
- Hook suites are now correctly inherit "SKIPPED" status from child items, by @HardNorth
### Changed
- Client version updated on [5.3.14](https://github.com/reportportal/client-java/releases/tag/5.3.14), by @HardNorth

## [5.3.5]
### Removed
- Logging of hook code reference with ERROR level on hook failure, since it pollutes the logs for Auto-Analysis, by @HardNorth

## [5.3.4]
### Changed
- Client version updated on [5.3.12](https://github.com/reportportal/client-java/releases/tag/5.3.12), by @HardNorth

## [5.3.3]
### Changed
- Embedded data attachment level changed to "INFO", by @HardNorth

## [5.3.2]
### Fixed
- Agent crash on `classpath:` prefix usage, by @HardNorth

## [5.3.1]
### Fixed
- Start time issue for Virtual parent Items, by @HardNorth
### Changed
- Client version updated on [5.3.9](https://github.com/reportportal/client-java/releases/tag/5.3.9), by @HardNorth

## [5.3.0]
### Added
- Initial release of the Cucumber agent, by @HardNorth
