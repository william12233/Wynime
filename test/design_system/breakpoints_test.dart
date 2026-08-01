import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/design_system/tokens/breakpoints.dart';

void main() {
  group('WynimeBreakpoints', () {
    test('classifies exact boundaries authoritatively', () {
      expect(WynimeBreakpoints.classify(599.99), WynimeWindowClass.compact);
      expect(WynimeBreakpoints.classify(600), WynimeWindowClass.medium);
      expect(WynimeBreakpoints.classify(1023.99), WynimeWindowClass.medium);
      expect(WynimeBreakpoints.classify(1024), WynimeWindowClass.expanded);
    });
  });
}
