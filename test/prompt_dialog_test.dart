import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ohmyssh/theme/theme.dart';
import 'package:ohmyssh/widgets/fields.dart';

/// Regression: the controller used to be built in the calling function and
/// disposed in a `finally`, which tore it out from under a widget that was
/// still rebuilding.
Widget _host(void Function(BuildContext) onReady) => MaterialApp(
  theme: buildAppTheme(Brightness.dark, kAccent),
  home: Scaffold(
    body: Builder(
      builder: (context) => ElevatedButton(
        onPressed: () => onReady(context),
        child: const Text('open'),
      ),
    ),
  ),
);

void main() {
  testWidgets('survives cancel through the dismissal animation', (
    tester,
  ) async {
    Future<String?>? result;
    await tester.pumpWidget(
      _host((context) {
        result = promptForText(context, title: 'Rename', label: 'New name');
      }),
    );

    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(find.text('Rename'), findsOneWidget);

    await tester.tap(find.text('Cancel'));
    // Not pumpAndSettle in one go: step through the closing animation, exactly
    // the window the old code disposed the controller in.
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 30));
    }
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(await result, isNull);
  });

  testWidgets('returns the typed value and disposes cleanly', (tester) async {
    Future<String?>? result;
    await tester.pumpWidget(
      _host((context) {
        result = promptForText(
          context,
          title: 'New directory',
          label: 'Name',
          actionLabel: 'Create',
        );
      }),
    );

    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), '  logs  ');
    await tester.tap(find.text('Create'));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(await result, 'logs');
  });

  testWidgets('reopening after a dismiss still works', (tester) async {
    Future<String?>? result;
    void open(BuildContext context) {
      result = promptForText(context, title: 'Rename', label: 'New name');
    }

    await tester.pumpWidget(_host(open));

    for (var round = 0; round < 2; round++) {
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull, reason: 'round $round');
    }
    expect(await result, isNull);
  });
}
