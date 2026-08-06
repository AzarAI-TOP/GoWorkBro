import 'package:flutter/material.dart';

import '../../models/models.dart';

/// Dialog for creating/editing habits — extracted from todo_screen.dart
class HabitEditDialog extends StatefulWidget {
  final Habit? initial;
  const HabitEditDialog({super.key, this.initial});

  @override
  State<HabitEditDialog> createState() => _HabitEditDialogState();
}

class _HabitEditDialogState extends State<HabitEditDialog> {
  static const _units = ['次', '分钟', '小时', '个', '页', '道'];

  late final TextEditingController _titleCtrl;
  late final TextEditingController _targetCtrl;
  late String _unit;

  @override
  void initState() {
    super.initState();
    _titleCtrl = TextEditingController(text: widget.initial?.title ?? '');
    _targetCtrl =
        TextEditingController(text: (widget.initial?.targetCount ?? 1).toString());
    final u = widget.initial?.unit ?? '次';
    _unit = _units.contains(u) ? u : '次';
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _targetCtrl.dispose();
    super.dispose();
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
    var target = int.tryParse(_targetCtrl.text.trim()) ?? 1;
    if (target < 1) target = 1;
    final Habit result;
    if (widget.initial == null) {
      result = Habit.create(title: title, targetCount: target, unit: _unit);
    } else {
      result = widget.initial!.copyWith(
          title: title, targetCount: target, unit: _unit);
    }
    Navigator.of(context).pop(result);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.initial == null ? '新建习惯' : '编辑习惯'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextField(
              controller: _titleCtrl,
              autofocus: true,
              decoration: const InputDecoration(hintText: '习惯名称'),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _targetCtrl,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                hintText: '目标数量',
                suffixText: '次',
              ),
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String>(
              initialValue: _unit,
              decoration: const InputDecoration(labelText: '单位'),
              items: _units
                  .map((u) => DropdownMenuItem<String>(
                        value: u,
                        child: Text(u),
                      ))
                  .toList(),
              onChanged: (v) => setState(() => _unit = v ?? '次'),
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
