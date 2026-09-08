/// Serializes application writes and provides an update handoff quiescence
/// barrier. Reads remain available while the barrier is active, but admitted
/// writes finish before [quiesce] completes and later writes are rejected.
final class DatabaseWriteGate {
  Future<void> _tail = Future<void>.value();
  bool _quiesced = false;

  Future<T> run<T>(Future<T> Function() operation) {
    if (_quiesced) {
      return Future<T>.error(StateError('database_quiesced'));
    }

    // Admission is synchronous: a write that reached this method before
    // quiesce was requested is allowed to drain before the snapshot.
    final result = _tail.then<T>((_) => operation());
    _tail = result.then<void>((_) {}, onError: (_, _) {});
    return result;
  }

  Future<void> quiesce() async {
    _quiesced = true;
    await _tail;
  }

  void resume() {
    _quiesced = false;
  }

  bool get isQuiesced => _quiesced;
}
