import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../models/models.dart';

/// Dialog for creating/editing habits — with custom unit support
class HabitEditDialog extends StatefulWidget {
  final Habit? initial;
  const HabitEditDialog({super.key, this.initial});

  @override
  State<HabitEditDialog> createState() => _HabitEditDialogState();
}

class _HabitEditDialogState extends State<HabitEditDialog> {
  static const _presetUnits = ['次', '分钟', '小时', '个', '页', '道'];

  late final TextEditingController _titleCtrl;
  late final TextEditingController _targetCtrl;
  late final TextEditingController _customUnitCtrl;
  late String _unit;
  bool _isCustomUnit = false;
  List<String> _savedUnits = [];

  @override
  void initState() {
    super.initState();
    _titleCtrl = TextEditingController(text: widget.initial?.title ?? '');
    _targetCtrl =
        TextEditingController(text: (widget.initial?.targetCount ?? 1).toString());
    final u = widget.initial?.unit ?? '次';
    _customUnitCtrl = TextEditingController();
    _loadSavedUnits();
    if (_presetUnits.contains(u)) {
      _unit = u;
      _isCustomUnit = false;
    } else {
      _unit = u;
      _isCustomUnit = true;
      _customUnitCtrl.text = u;
    }
  }

  Future<void> _loadSavedUnits() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _savedUnits = prefs.getStringList('custom_habit_units') ?? [];
    });
  }

  Future<void> _saveCustomUnit(String unit) async {
    final prefs = await SharedPreferences.getInstance();
    final list = prefs.getStringList('custom_habit_units') ?? [];
    if (!list.contains(unit)) {
      list.add(unit);
      await prefs.setStringList('custom_habit_units', list);
    }
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _targetCtrl.dispose();
    _customUnitCtrl.dispose();
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

    String finalUnit = _unit;
    if (_isCustomUnit) {
      finalUnit = _customUnitCtrl.text.trim();
      if (finalUnit.isEmpty) finalUnit = '次';
      _saveCustomUnit(finalUnit);
    }

    final Habit result;
    if (widget.initial == null) {
      result = Habit.create(title: title, targetCount: target, unit: finalUnit);
    } else {
      result = widget.initial!.copyWith(
          title: title, targetCount: target, unit: finalUnit);
    }
    Navigator.of(context).pop(result);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
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
                hintText: '每日目标数量',
                prefixText: '每日 ',
              ),
            ),
            const SizedBox(height: 16),
            Text('量词', style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 6,
              children: [
                ..._presetUnits.map((u) => ChoiceChip(
                      label: Text(u),
                      selected: !_isCustomUnit && _unit == u,
                      onSelected: (_) {
                        setState(() {
                          _unit = u;
                          _isCustomUnit = false;
                        });
                      },
                    )),
                ..._savedUnits
                    .where((u) => !_presetUnits.contains(u))
                    .map((u) => ChoiceChip(
                          label: Text(u),
                          selected: !_isCustomUnit && _unit == u,
                          onSelected: (_) {
                            setState(() {
                              _unit = u;
                              _isCustomUnit = false;
                            });
                          },
                        )),
                ChoiceChip(
                  label: const Text('自定义'),
                  selected: _isCustomUnit,
                  onSelected: (_) {
                    setState(() => _isCustomUnit = true);
                  },
                ),
              ],
            ),
            if (_isCustomUnit) ...[
              const SizedBox(height: 8),
              TextField(
                controller: _customUnitCtrl,
                decoration: const InputDecoration(
                  hintText: '输入自定义量词',
                  isDense: true,
                ),
              ),
            ],
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
