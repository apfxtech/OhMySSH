import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ohmyssh/components/cardlist.dart';
import 'package:ohmyssh/theme/theme.dart';

/// `crossAxisCount == items.length` stays a single row no matter how narrow it
/// gets.
Widget _host({required double width, required int tabs}) => MaterialApp(
  theme: buildAppTheme(Brightness.dark, kAccent),
  home: Scaffold(
    body: Center(
      child: SizedBox(
        width: width,
        child: GroupedCardGrid<int>(
          items: List<int>.generate(tabs, (i) => i),
          crossAxisCount: tabs,
          mainAxisExtent: 42,
          itemBuilder: (context, i) => Text('tab$i'),
        ),
      ),
    ),
  ),
);

void main() {
  testWidgets('stays one row as tabs are added', (tester) async {
    tester.view.physicalSize = const Size(1200, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    double stripHeight() =>
        tester.getSize(find.byType(GroupedCardGrid<int>)).height;

    await tester.pumpWidget(_host(width: 400, tabs: 2));
    final oneRow = stripHeight();

    for (final count in [3, 5, 8]) {
      await tester.pumpWidget(_host(width: 400, tabs: count));
      await tester.pump();
      expect(
        stripHeight(),
        oneRow,
        reason: '$count tabs wrapped to another row instead of shrinking',
      );
    }
  });

  testWidgets('tabs share the width evenly', (tester) async {
    tester.view.physicalSize = const Size(1200, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(_host(width: 400, tabs: 4));
    await tester.pump();

    final widths = [
      for (var i = 0; i < 4; i++) tester.getSize(find.text('tab$i')).width,
    ];
    for (final w in widths) {
      expect(w, greaterThan(0));
      expect((w - widths.first).abs(), lessThan(1.0));
    }
  });

  testWidgets('all tabs render at narrow widths', (tester) async {
    tester.view.physicalSize = const Size(1200, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(_host(width: 320, tabs: 6));
    await tester.pump();

    for (var i = 0; i < 6; i++) {
      expect(find.text('tab$i'), findsOneWidget);
    }
  });
}
