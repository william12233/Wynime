import 'package:flutter/material.dart';

import '../../domain/models/source_models.dart';
import '../../design_system/tokens/spacing.dart';

/// Wynime-owned line selection data for one exact source subject.
///
/// The widget deliberately accepts only the typed source model. It has no
/// WebView, URL, DOM or source-site presentation dependency.
final class SourceLineSelector extends StatelessWidget {
  const SourceLineSelector({
    required this.lines,
    required this.onSelected,
    this.selectedLineId,
    this.title = '播放線路',
    this.subtitle = '選擇同一來源的播放線路',
    super.key,
  });

  final List<SourceSubjectLine> lines;
  final ValueChanged<SourceSubjectLine> onSelected;
  final String? selectedLineId;
  final String title;
  final String subtitle;

  static bool shouldShow(Iterable<SourceSubjectLine> values) =>
      values.length > 1;

  @override
  Widget build(BuildContext context) => ListView(
    shrinkWrap: true,
    padding: const EdgeInsets.only(bottom: WynimeSpacing.sm),
    children: [
      ListTile(title: Text(title), subtitle: Text(subtitle)),
      for (final line in lines)
        ListTile(
          key: ValueKey('source-line-${line.lineId}'),
          leading: Icon(
            line.lineId == selectedLineId
                ? Icons.radio_button_checked
                : Icons.radio_button_unchecked,
          ),
          title: Text(line.title),
          subtitle: Text('${line.episodes.length} 集'),
          onTap: () => onSelected(line),
        ),
    ],
  );
}
