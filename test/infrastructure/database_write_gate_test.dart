import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/infrastructure/database/database_write_gate.dart';

void main() {
  test('quiesce drains admitted writes and rejects later writes', () async {
    final gate = DatabaseWriteGate();
    final release = Completer<void>();
    var completed = 0;

    final admitted = gate.run(() async {
      await release.future;
      completed++;
    });
    final quiesced = gate.quiesce();
    await Future<void>.delayed(Duration.zero);

    expect(gate.isQuiesced, isTrue);
    expect(completed, 0);
    expect(gate.run(() async {}), throwsA(isA<StateError>()));

    release.complete();
    await admitted;
    await quiesced;
    expect(completed, 1);

    gate.resume();
    await gate.run(() async {
      completed++;
    });
    expect(completed, 2);
  });
}
