enum WynimeWindowClass { compact, medium, expanded }

abstract final class WynimeBreakpoints {
  static const double compactUpperBound = 600;
  static const double mediumUpperBound = 1024;

  static WynimeWindowClass classify(double logicalWidth) {
    assert(logicalWidth >= 0, 'logicalWidth must not be negative.');
    if (logicalWidth < compactUpperBound) {
      return WynimeWindowClass.compact;
    }
    if (logicalWidth < mediumUpperBound) {
      return WynimeWindowClass.medium;
    }
    return WynimeWindowClass.expanded;
  }
}
