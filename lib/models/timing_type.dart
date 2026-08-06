enum TimingType { forward, backward, none }
enum ItemType { todo, habit }

extension TimingTypeExtension on TimingType {
  String get label {
    switch (this) {
      case TimingType.forward: return '正向计时';
      case TimingType.backward: return '倒向计时';
      case TimingType.none: return '不记时';
    }
  }
  String get value {
    switch (this) {
      case TimingType.forward: return 'forward';
      case TimingType.backward: return 'backward';
      case TimingType.none: return 'none';
    }
  }
  static TimingType fromValue(String v) {
    switch (v) {
      case 'forward': return TimingType.forward;
      case 'backward': return TimingType.backward;
      default: return TimingType.none;
    }
  }
}
