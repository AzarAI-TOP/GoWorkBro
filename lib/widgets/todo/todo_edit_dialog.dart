import 'package:flutter/material.dart';

import '../../models/models.dart';

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
        const SnackBar(
          content: Text('请输入标题'),
          duration: Duration(milliseconds: 900),
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
    return AlertDialog(
      title: Text(widget.initial == null ? '新建待办' : '编辑待办'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextField(
              controller: _titleCtrl,
              autofocus: true,
              decoration: const InputDecoration(hintText: '输入待办标题'),
              textInputAction: TextInputAction.done,
            ),
            const SizedBox(height: 16),
            Text('计时方式', style: theme.textTheme.labelLarge),
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
                    title: const Text('正向计时'),
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                  ),
                  RadioListTile<TimingType>(
                    value: TimingType.backward,
                    title: const Text('倒向计时'),
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                  ),
                  RadioListTile<TimingType>(
                    value: TimingType.none,
                    title: const Text('不记时'),
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                  ),
                ],
              ),
            ),
            if (_timingType == TimingType.backward) ...[
              const SizedBox(height: 8),
              Text('时长', style: theme.textTheme.labelLarge),
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
                    label: const Text('自定义'),
                    selected: _durationChoice == 'custom',
                    onSelected: (_) => setState(() => _durationChoice = 'custom'),
                  ),
                ],
              ),
              if (_durationChoice == 'custom') ...[
                const SizedBox(height: 8),
                TextField(
                  controller: _customCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    hintText: '自定义分钟数',
                    suffixText: 'min',
                  ),
                ),
              ],
            ],
            const SizedBox(height: 8),
            CheckboxListTile(
              value: _keepTomorrow,
              onChanged: (v) => setState(() => _keepTomorrow = v ?? false),
              title: const Text('明天继续'),
              subtitle: const Text('完成后明天自动重建'),
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
          child: const Text('取消'),
        ),
        ElevatedButton(onPressed: _save, child: const Text('保存')),
      ],
    );
  }
}
