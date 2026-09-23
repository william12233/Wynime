import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_models.dart';
import 'package:wynime/src/presentation/playback/source_line_selector.dart';

void main() {
  testWidgets('multiple lines render three Wynime-owned choices', (
    tester,
  ) async {
    SourceSubjectLine? selected;
    final lines = _lines(3);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SourceLineSelector(
            lines: lines,
            onSelected: (line) => selected = line,
          ),
        ),
      ),
    );

    expect(find.text('主線-1'), findsOneWidget);
    expect(find.text('主線-2'), findsOneWidget);
    expect(find.text('備用-1'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('source-line-line-2')));
    expect(selected?.lineId, 'line-2');
  });

  test('one line does not require a selector', () {
    expect(SourceLineSelector.shouldShow(_lines(1)), isFalse);
    expect(SourceLineSelector.shouldShow(_lines(3)), isTrue);
  });

  testWidgets('selected line keeps the exact line identity', (tester) async {
    final lines = _lines(3);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SourceLineSelector(
            lines: lines,
            selectedLineId: 'line-2',
            onSelected: (_) {},
          ),
        ),
      ),
    );

    final selectedIcon = tester.widget<Icon>(
      find.descendant(
        of: find.byKey(const ValueKey('source-line-line-2')),
        matching: find.byType(Icon),
      ),
    );
    expect(selectedIcon.icon, Icons.radio_button_checked);
  });
}

List<SourceSubjectLine> _lines(int count) {
  final subject = SourceSubjectIdentity(
    sourceId: 'xifan',
    subjectId: 'subject',
  );
  final titles = ['主線-1', '主線-2', '備用-1'];
  return [
    for (var index = 0; index < count; index++)
      SourceSubjectLine(
        identity: subject,
        lineId: 'line-${index + 1}',
        title: titles[index],
        episodes: [
          SourceEpisode(
            identity: SourceEpisodeIdentity(
              sourceId: subject.sourceId,
              lineId: 'line-${index + 1}',
              subjectId: subject.subjectId,
              episodeId: 'episode-24',
            ),
            title: '第 24 集',
          ),
        ],
      ),
  ];
}
