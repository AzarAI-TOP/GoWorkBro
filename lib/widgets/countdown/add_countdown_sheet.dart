import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../models/models.dart';
import '../../providers/app_provider.dart';
import '../../services/app_locale.dart';
import '../../theme/app_theme.dart';

class AddCountdownSheet extends StatefulWidget {
  final AppProvider provider;
  final Countdown? existing;

  const AddCountdownSheet({super.key, required this.provider, this.existing});

  @override
  State<AddCountdownSheet> createState() => AddCountdownSheetState();
}

class AddCountdownSheetState extends State<AddCountdownSheet> {
  final _titleController = TextEditingController();
  DateTime _selectedDate = DateTime.now().add(const Duration(hours: 1));
  TimeOfDay _selectedTime = TimeOfDay.fromDateTime(
    DateTime.now().add(const Duration(hours: 1)),
  );
  int _selectedColor = 0;

  @override
  void initState() {
    super.initState();
    if (widget.existing != null) {
      _titleController.text = widget.existing!.title;
      _selectedDate = widget.existing!.targetDateTime;
      _selectedTime = TimeOfDay.fromDateTime(widget.existing!.targetDateTime);
      _selectedColor = widget.existing!.colorIndex;
    }
  }

  @override
  void dispose() {
    _titleController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final isEditing = widget.existing != null;
    return Padding(
      padding: EdgeInsets.only(
        left: 24,
        right: 24,
        top: 24,
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 20),
          Text(
            isEditing ? s.editCountdown : s.newCountdown,
            style: theme.textTheme.titleLarge,
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _titleController,
            decoration: InputDecoration(
              labelText: s.title,
              hintText: s.countdownTitleHint,
            ),
            autofocus: true,
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: _pickDate,
                  icon: const Icon(Icons.calendar_today, size: 18),
                  label: Text(
                    '${_selectedDate.year}/${_selectedDate.month}/${_selectedDate.day}',
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: _pickTime,
                  icon: const Icon(Icons.access_time, size: 18),
                  label: Text(
                    '${_selectedTime.hour.toString().padLeft(2, '0')}:${_selectedTime.minute.toString().padLeft(2, '0')}',
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(s.color, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 12,
            children: List.generate(6, (i) {
              final color = AppTheme.chartColors[i];
              final selected = _selectedColor == i;
              return GestureDetector(
                onTap: () => setState(() => _selectedColor = i),
                child: Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: color,
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: selected
                          ? theme.colorScheme.onSurface
                          : Colors.transparent,
                      width: 3,
                    ),
                  ),
                  child: selected
                      ? const Icon(Icons.check, color: Colors.white, size: 18)
                      : null,
                ),
              );
            }),
          ),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: _save,
              child: Text(isEditing ? s.save : s.create),
            ),
          ),
        ],
      ),
    );
  }

  void _save() {
    final title = _titleController.text.trim();
    if (title.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            S.of(context.read<AppLocaleProvider>().locale).enterTitle,
          ),
          duration: const Duration(milliseconds: 900),
        ),
      );
      return;
    }
    final target = DateTime(
      _selectedDate.year,
      _selectedDate.month,
      _selectedDate.day,
      _selectedTime.hour,
      _selectedTime.minute,
    );
    if (!target.isAfter(DateTime.now())) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            S.of(context.read<AppLocaleProvider>().locale).targetMustBeFuture,
          ),
          duration: const Duration(milliseconds: 1200),
        ),
      );
      return;
    }
    if (widget.existing != null) {
      // #7: Update in place instead of delete-then-add (no data loss risk)
      widget.provider.updateCountdown(
        Countdown(
          id: widget.existing!.id,
          title: title,
          targetDateTime: target,
          createdDate: widget.existing!.createdDate,
          colorIndex: _selectedColor,
        ),
      );
    } else {
      widget.provider.addCountdown(
        Countdown.create(
          title: title,
          targetDateTime: target,
          colorIndex: _selectedColor,
        ),
      );
    }
    Navigator.pop(context);
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _selectedDate.isAfter(DateTime.now())
          ? _selectedDate
          : DateTime.now(),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365 * 5)),
    );
    if (picked != null) setState(() => _selectedDate = picked);
  }

  Future<void> _pickTime() async {
    final picked = await showTimePicker(
      context: context,
      initialTime: _selectedTime,
    );
    if (picked != null) setState(() => _selectedTime = picked);
  }
}
