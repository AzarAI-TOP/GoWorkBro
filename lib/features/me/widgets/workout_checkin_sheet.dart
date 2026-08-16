import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/l10n/app_locale.dart';

class WorkoutCheckInResult {
  const WorkoutCheckInResult({
    required this.durationMinutes,
    required this.description,
  });

  final int durationMinutes;
  final String description;
}

class WorkoutCheckInSheet extends StatefulWidget {
  const WorkoutCheckInSheet({
    super.key,
    this.initialDurationMinutes,
    this.initialDescription,
    required this.onSave,
  });

  final int? initialDurationMinutes;
  final String? initialDescription;
  final ValueChanged<WorkoutCheckInResult> onSave;

  @override
  State<WorkoutCheckInSheet> createState() => _WorkoutCheckInSheetState();
}

class _WorkoutCheckInSheetState extends State<WorkoutCheckInSheet> {
  static const _presets = [15, 30, 45, 60];

  late final TextEditingController _customDurationController;
  late final TextEditingController _descriptionController;
  int? _durationMinutes;
  bool _showDurationError = false;

  @override
  void initState() {
    super.initState();
    final initial = widget.initialDurationMinutes;
    _durationMinutes = initial ?? 30;
    _customDurationController = TextEditingController(
      text: initial != null && !_presets.contains(initial) ? '$initial' : '',
    );
    _descriptionController = TextEditingController(
      text: widget.initialDescription ?? '',
    );
  }

  @override
  void dispose() {
    _customDurationController.dispose();
    _descriptionController.dispose();
    super.dispose();
  }

  void _selectPreset(int minutes) {
    HapticFeedback.selectionClick();
    setState(() {
      _durationMinutes = minutes;
      _customDurationController.clear();
      _showDurationError = false;
    });
  }

  void _save() {
    final minutes = _durationMinutes;
    if (minutes == null || minutes <= 0 || minutes > 1440) {
      setState(() => _showDurationError = true);
      return;
    }
    HapticFeedback.lightImpact();
    widget.onSave(
      WorkoutCheckInResult(
        durationMinutes: minutes,
        description: _descriptionController.text.trim(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context.watch<AppLocaleProvider>().locale);
    final theme = Theme.of(context);

    return SafeArea(
      child: SingleChildScrollView(
        padding: EdgeInsets.fromLTRB(
          24,
          20,
          24,
          20 + MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    Icons.fitness_center_outlined,
                    color: theme.colorScheme.primary,
                  ),
                ),
                const SizedBox(width: 12),
                Text(s.workoutCheckIn, style: theme.textTheme.titleLarge),
              ],
            ),
            const SizedBox(height: 24),
            Text(s.workoutDuration, style: theme.textTheme.titleMedium),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final minutes in _presets)
                  ChoiceChip(
                    label: Text(s.minutes(minutes)),
                    selected:
                        _durationMinutes == minutes &&
                        _customDurationController.text.isEmpty,
                    onSelected: (_) => _selectPreset(minutes),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            TextField(
              key: const Key('custom-workout-duration'),
              controller: _customDurationController,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(
                labelText: s.customDuration,
                suffixText: s.minuteUnit,
                errorText: _showDurationError ? s.workoutDurationError : null,
                border: const OutlineInputBorder(),
              ),
              onChanged: (value) {
                setState(() {
                  _durationMinutes = int.tryParse(value);
                  _showDurationError = false;
                });
              },
            ),
            const SizedBox(height: 20),
            TextField(
              key: const Key('workout-description'),
              controller: _descriptionController,
              maxLength: 200,
              maxLines: 3,
              textCapitalization: TextCapitalization.sentences,
              decoration: InputDecoration(
                labelText: s.workoutDescription,
                hintText: s.workoutDescriptionHint,
                alignLabelWithHint: true,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 8),
            FilledButton.icon(
              key: const Key('save-workout-checkin'),
              onPressed: _save,
              icon: const Icon(Icons.check_rounded),
              label: Text(s.saveWorkoutCheckIn),
            ),
          ],
        ),
      ),
    );
  }
}
