import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';

/// Dialog for creating/editing TODOs — extracted from todo_screen.dart
class TodoEditDialog extends StatefulWidget {
  final Todo? initial;
  const TodoEditDialog({super.key, this.initial});

  @override
  State<TodoEditDialog> createState() => _TodoEditDialogState();
}

class _TodoEditDialogState extends State<TodoEditDialog> {
  late final TextEditingController _titleCtrl;
  late final TextEditingController _customCtrl;
  TimingType _timingType = TimingType.forward;
  String _durationChoice = '25'; // '15' | '25' | '40' | 'custom'
  bool _keepTomorrow = true;

  @override
  void initState() {
    super.initState();
    final t = widget.initial;
    _titleCtrl = TextEditingController(text: t?.title ?? '');
    _timingType = t?.timingType ?? TimingType.forward;
    _keepTomorrow = t?.keepTomorrow ?? true;
    if (t != null && t.timingType == TimingType.backward) {
      if (t.durationMinutes == 15) {
        _durationChoice = '15';
      } else if (t.durationMinutes == 25) {
        _durationChoice = '25';
      } else if (t.durationMinutes == 40) {
        _durationChoice = '40';
      } else {
        _durationChoice = 'custom';
      }
      _customCtrl = TextEditingController(text: t.durationMinutes.toString());
    } else {
      _customCtrl = TextEditingController(text: '30');
    }
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _customCtrl.dispose();
    super.dispose();
  }

  int _resolvedDuration() {
    if (_timingType != TimingType.backward) {
      return widget.initial?.durationMinutes ?? 25;
    }
    switch (_durationChoice) {
      case '15':
        return 15;
      case '25':
        return 25;
      case '40':
        return 40;
      default:
        final v = int.tryParse(_customCtrl.text.trim()) ?? 25;
        return v < 1 ? 25 : v;
    }
  }

  void _save() {
    final title = _titleCtrl.text.trim();
    if (title.isEmpty) {
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(
        SnackBar(
          content: Text(
            S.of(context.read<AppLocaleProvider>().locale).enterTitle,
          ),
          duration: const Duration(milliseconds: 900),
        ),
      );
      return;
    }
    final duration = _resolvedDuration();
    final Todo result;
    if (widget.initial == null) {
      result = Todo.create(
        title: title,
        timingType: _timingType,
        durationMinutes: duration,
        keepTomorrow: _keepTomorrow,
      );
    } else {
      result = widget.initial!.copyWith(
        title: title,
        timingType: _timingType,
        durationMinutes: duration,
        keepTomorrow: _keepTomorrow,
      );
    }
    Navigator.of(context).pop(result);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = S.of(context.read<AppLocaleProvider>().locale);
    return AlertDialog(
      title: Text(widget.initial == null ? s.addTodo : s.editTodo),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextField(
              controller: _titleCtrl,
              autofocus: true,
              decoration: InputDecoration(hintText: s.todoTitleHint),
              textInputAction: TextInputAction.done,
            ),
            const SizedBox(height: 16),
            Text(s.timingMethod, style: theme.textTheme.labelLarge),
            RadioGroup<TimingType>(
              groupValue: _timingType,
              onChanged: (v) {
                if (v != null) setState(() => _timingType = v);
              },
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  RadioListTile<TimingType>(
                    value: TimingType.forward,
                    title: Text(s.forwardTimer),
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                  ),
                  RadioListTile<TimingType>(
                    value: TimingType.backward,
                    title: Text(s.backwardTimer),
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                  ),
                  RadioListTile<TimingType>(
                    value: TimingType.none,
                    title: Text(s.noTimer),
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                  ),
                ],
              ),
            ),
            if (_timingType == TimingType.backward) ...[
              const SizedBox(height: 8),
              Text(s.duration, style: theme.textTheme.labelLarge),
              Wrap(
                spacing: 10,
                runSpacing: 8,
                children: [
                  ChoiceChip(
                    label: const Text('15min'),
                    selected: _durationChoice == '15',
                    onSelected: (_) => setState(() => _durationChoice = '15'),
                  ),
                  ChoiceChip(
                    label: const Text('25min'),
                    selected: _durationChoice == '25',
                    onSelected: (_) => setState(() => _durationChoice = '25'),
                  ),
                  ChoiceChip(
                    label: const Text('40min'),
                    selected: _durationChoice == '40',
                    onSelected: (_) => setState(() => _durationChoice = '40'),
                  ),
                  ChoiceChip(
                    label: Text(s.customMin),
                    selected: _durationChoice == 'custom',
                    onSelected: (_) =>
                        setState(() => _durationChoice = 'custom'),
                  ),
                ],
              ),
              if (_durationChoice == 'custom') ...[
                const SizedBox(height: 8),
                TextField(
                  controller: _customCtrl,
                  keyboardType: TextInputType.number,
                  decoration: InputDecoration(
                    hintText: s.customMinHint,
                    suffixText: 'min',
                  ),
                ),
              ],
            ],
            const SizedBox(height: 8),
            CheckboxListTile(
              value: _keepTomorrow,
              onChanged: (v) => setState(() => _keepTomorrow = v ?? false),
              title: Text(s.keepTomorrow),
              subtitle: Text(s.keepTomorrowDesc),
              dense: true,
              contentPadding: EdgeInsets.zero,
              controlAffinity: ListTileControlAffinity.leading,
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(null),
          child: Text(s.cancel),
        ),
        ElevatedButton(onPressed: _save, child: Text(s.save)),
      ],
    );
  }
}
