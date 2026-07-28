import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ohmyssh/pages/session/file_editor.dart';

List<String> _lines(String text) => text.split('\n');

void main() {
  group('computeModifiedLines', () {
    test('an untouched file has nothing marked', () {
      final saved = _lines('alpha\nbeta\ngamma');
      expect(
        computeModifiedLines(saved, _lines('alpha\nbeta\ngamma')),
        isEmpty,
      );
    });

    test('marks only the edited line', () {
      final saved = _lines('alpha\nbeta\ngamma');
      final current = _lines('alpha\nBETA\ngamma');
      expect(computeModifiedLines(saved, current), {1});
    });

    test('marks an inserted line, not the ones it pushed down', () {
      final saved = _lines('alpha\nbeta\ngamma');
      final current = _lines('alpha\ninserted\nbeta\ngamma');
      expect(computeModifiedLines(saved, current), {1});
    });

    test('a deletion marks nothing — the line is gone', () {
      final saved = _lines('alpha\nbeta\ngamma');
      final current = _lines('alpha\ngamma');
      expect(computeModifiedLines(saved, current), isEmpty);
    });

    test('marks appended lines at the end', () {
      final saved = _lines('alpha\nbeta');
      final current = _lines('alpha\nbeta\ngamma\ndelta');
      expect(computeModifiedLines(saved, current), {2, 3});
    });

    test('indices stay absolute after a long common prefix', () {
      final saved = [for (var i = 0; i < 500; i++) 'line $i'];
      final current = [...saved]..[400] = 'edited';
      expect(computeModifiedLines(saved, current), {400});
    });

    test('a repeated line is matched at its own position', () {
      // Naive prefix/suffix trimming with no LCS would blame the wrong one of
      // these identical lines.
      final saved = _lines('x\nsame\nsame\nsame\ny');
      final current = _lines('x\nsame\nsame\nsame\nsame\ny');
      final modified = computeModifiedLines(saved, current);
      expect(modified.length, 1);
      expect(modified.single, inInclusiveRange(1, 4));
    });

    test('replacing everything falls back instead of hanging', () {
      // Well past the LCS cutoff: this must return promptly rather than run a
      // 4000x4000 table.
      final saved = [for (var i = 0; i < 4000; i++) 'old $i'];
      final current = [for (var i = 0; i < 4000; i++) 'new $i'];
      final watch = Stopwatch()..start();
      final modified = computeModifiedLines(saved, current);
      watch.stop();
      expect(modified.length, 4000);
      expect(
        watch.elapsedMilliseconds,
        lessThan(500),
        reason: 'the O(n*m) guard did not kick in',
      );
    });

    test('an empty file gains every line', () {
      expect(computeModifiedLines([''], _lines('a\nb')), {0, 1});
    });
  });

  group('line positions', () {
    const fontSize = 13.0;
    const lineHeight = fontSize * 1.4;

    Future<List<double>> layout(
      WidgetTester tester,
      String text, {
      double width = 200,
    }) async {
      final controller = TextEditingController(text: text);
      addTearDown(controller.dispose);
      final key = GlobalKey();

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SizedBox(
              width: width,
              height: 400,
              child: TextField(
                key: key,
                controller: controller,
                maxLines: null,
                expands: true,
                textAlignVertical: TextAlignVertical.top,
                style: const TextStyle(
                  fontFamily: 'monospace',
                  fontSize: fontSize,
                  height: lineHeight / fontSize,
                ),
                decoration: const InputDecoration(
                  border: InputBorder.none,
                  isCollapsed: true,
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump();

      final editable = findRenderEditable(
        key.currentContext?.findRenderObject(),
      );
      expect(editable, isNotNull, reason: 'no RenderEditable in the subtree');

      final ys = <double>[];
      var offset = 0;
      for (final line in text.split('\n')) {
        ys.add(
          editable!.getLocalRectForCaret(TextPosition(offset: offset)).top,
        );
        offset += line.length + 1;
      }
      return ys;
    }

    testWidgets('short lines sit one line-height apart', (tester) async {
      final ys = await layout(tester, 'a\nb\nc\nd');
      expect(ys.length, 4);
      for (var i = 1; i < ys.length; i++) {
        expect(
          ys[i] - ys[i - 1],
          closeTo(lineHeight, 1.0),
          reason: 'line $i is not one row below line ${i - 1}',
        );
      }
    });

    testWidgets('a wrapped line pushes the next number down by its rows', (
      tester,
    ) async {
      // Line 1 wraps, so line 2's number must skip the extra visual rows rather
      // than land inside line 1.
      final long = List.filled(60, 'wide').join(' ');
      final ys = await layout(tester, 'short\n$long\ntail', width: 200);

      // Measured rather than assumed: the test font rounds the row height, so
      // the first (unwrapped) gap is used instead of font metrics.
      final row = ys[1] - ys[0];
      expect(row, closeTo(lineHeight, 1.0));

      final wrappedRows = (ys[2] - ys[1]) / row;
      expect(
        wrappedRows,
        greaterThan(1.5),
        reason: 'the long line did not wrap — the test is not proving anything',
      );
      expect(
        wrappedRows,
        closeTo(wrappedRows.roundToDouble(), 0.05),
        reason: 'line 2 landed mid-row, so the number would not line up',
      );
    });

    testWidgets('blank lines still get their own row', (tester) async {
      final ys = await layout(tester, 'a\n\n\nb');
      expect(ys.length, 4);
      expect(ys[3] - ys[0], closeTo(lineHeight * 3, 1.0));
    });
  });
}
