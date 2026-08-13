import 'package:flutter_test/flutter_test.dart';
import 'package:goworkbro/core/sync/avatar_sync.dart';

void main() {
  group('AvatarSync.applyDecision', () {
    test('null value (cloud removal) clears the local avatar', () {
      expect(AvatarSync.applyDecision(null), AvatarApplyAction.clear);
    });

    test('legacy device-local paths are ignored, not cleared', () {
      expect(
        AvatarSync.applyDecision(r'C:\Users\ASUS\avatar.jpg'),
        AvatarApplyAction.ignore,
      );
      expect(
        AvatarSync.applyDecision('/storage/emulated/0/DCIM/avatar.jpg'),
        AvatarApplyAction.ignore,
      );
      expect(AvatarSync.applyDecision(''), AvatarApplyAction.ignore);
    });

    test('storage object paths apply', () {
      const uuid = '123e4567-e89b-42d3-a456-426614174000';
      expect(
        AvatarSync.applyDecision('$uuid/avatar.jpg'),
        AvatarApplyAction.apply,
      );
      expect(
        AvatarSync.applyDecision('$uuid/avatar.png'),
        AvatarApplyAction.apply,
      );
    });
  });
}
