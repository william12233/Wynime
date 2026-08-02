import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'Windows WebView2 plugin retains the VS2026 coroutine compatibility gate',
    () {
      final cmake = File('windows/CMakeLists.txt').readAsStringSync();

      expect(
        cmake,
        contains(
          'add_compile_definitions('
          '_SILENCE_EXPERIMENTAL_COROUTINE_DEPRECATION_WARNINGS)',
        ),
        reason:
            'The current WebView2 plugin includes Microsoft experimental '
            'coroutine headers, which fail under the VS2026 STL without this '
            'project-level compatibility definition.',
      );

      expect(
        cmake.indexOf(
          '_SILENCE_EXPERIMENTAL_COROUTINE_DEPRECATION_WARNINGS',
        ),
        lessThan(cmake.indexOf('include(flutter/generated_plugins.cmake)')),
        reason:
            'The compatibility definition must be visible to plugin targets.',
      );
    },
  );
}
