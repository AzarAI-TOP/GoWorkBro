/// Timestamp helpers for last-write-wins sync.
///
/// Both local SQLite rows and cloud rows carry `updated_at` as ISO-8601.
/// Values are normalized to UTC before comparing so that equivalent
/// instants written in different formats ('Z' vs '+00:00') compare equal.
library;

String nowStamp() => DateTime.now().toUtc().toIso8601String();

/// True when [local] is strictly newer than [remote].
///
/// A null/empty local stamp is treated as "unknown age" — never newer — so a
/// fresh legacy row gets replaced by whatever the cloud holds.
bool isLocalNewer(String? local, String? remote) {
  if (local == null || local.isEmpty) return false;
  if (remote == null || remote.isEmpty) return true;
  final l = DateTime.tryParse(local);
  final r = DateTime.tryParse(remote);
  if (l == null || r == null) return local.compareTo(remote) > 0;
  return l.toUtc().isAfter(r.toUtc());
}
