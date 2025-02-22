# Changelog
- Based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
- This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.5.0
22 February 2025

### Changed
- Behaviour of `OnDemandBucketedWindow.onDemandTumblingBuckets` argument defaults.

### Removed
- Return values of `GenericSubject`'s `addObserver` and `removeObserver` methods.

## 1.4.0
15 February 2025

### Changed
- Improve performance of `OnDemandBucketedWindow.onDemandTumblingBuckets`.

## 1.3.0
10 February 2025

### Changed
- Align Micrometer metric names to convention.

## 1.2.0
10 February 2025

### Changed
- Align package names to Maven artifact ID (`io...timeslidingwindow` -> `io...realtimeslidingwindow`).

## 1.1.0
9 February 2025

### Changed
- Now supports Kotlin 1.8+ (previously required Kotlin 1.9+).
- Now supports Java 11+ (previously required Java 21+).

## 1.0.0
8 February 2025

### Added
- Initial version.
