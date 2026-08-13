/// 待办 (Todo) screen — mixed list of TODO + Habit items
/// 番茄TODO-inspired clean geometric design
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/providers/app_provider.dart';
import 'package:goworkbro/features/timer/timer_screen.dart';
import 'package:goworkbro/features/todos/widgets/todo_card.dart';
import 'package:goworkbro/features/todos/widgets/habit_card.dart';
import 'package:goworkbro/features/todos/widgets/todo_edit_dialog.dart';
import 'package:goworkbro/features/todos/widgets/habit_edit_dialog.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';

/// Combined display order (top → bottom):
/// 1. **unfinished habits** (by sortOrder) — forced above todos;
/// 2. incomplete todos (by sortOrder);
/// 3. completed habits (by sortOrder);
/// 4. completed todos (by completedDate desc) — the bottom "done" zone, so
///    finishing a todo drops it to the very bottom of the list.
///
/// Top-level (not private) so tests can assert the ordering rules directly.
List<Object> buildCombinedList(List<Todo> todos, List<Habit> habits) {
  final unfinishedHabits = habits.where((h) => !h.isCompleted).toList()
    ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
  final completedHabits = habits.where((h) => h.isCompleted).toList()
    ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
  final incompleteTodos = todos.where((t) => !t.isCompleted).toList()
    ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
  final completedTodos = todos.where((t) => t.isCompleted).toList()
    ..sort((a, b) {
      final ad = a.completedDate ?? '';
      final bd = b.completedDate ?? '';
      return bd.compareTo(ad); // desc — most recently completed first
    });
  return <Object>[
    ...unfinishedHabits,
    ...incompleteTodos,
    ...completedHabits,
    ...completedTodos,
  ];
}

/// Result of mapping a display-space reorder to provider-space indexes.
class ReorderMapping {
  const ReorderMapping({
    required this.kind,
    required this.oldProviderIndex,
    required this.newProviderIndex,
  });

  /// 'habit' or 'todo' — which provider list the indexes refer to.
  final String kind;
  final int oldProviderIndex;
  final int newProviderIndex;
}

/// Pure mapping from ReorderableListView.onReorderItem arguments to
/// provider-space reorder indexes (extracted for tests).
///
/// `newIndex` follows onReorderItem semantics: the final insertion position
/// with the dragged item conceptually removed from the list first.
///
/// Returns null when the drag is a no-op (dropped at its own position or a
/// completed item).
ReorderMapping? mapReorder({
  required List<Todo> todos,
  required List<Habit> habits,
  required int oldIndex,
  required int newIndex,
}) {
  final items = buildCombinedList(todos, habits);
  if (oldIndex < 0 || oldIndex >= items.length) return null;
  final oldItem = items[oldIndex];

  final unfinishedHabits = habits.where((h) => !h.isCompleted).toList()
    ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
  final incompleteTodos = todos.where((t) => !t.isCompleted).toList()
    ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
  final uh = unfinishedHabits.length;
  final it = incompleteTodos.length;

  if (oldItem is Habit) {
    // Habit drag — confined to the unfinished-habit zone at the top.
    // Completed habits are not draggable (no drag handle, plus this guard).
    if (oldItem.isCompleted) return null;
    final dropAfter = newIndex > oldIndex;
    final finalPos = newIndex.clamp(0, uh);
    final oldHabitIndex = habits.indexOf(oldItem);
    int newHabitIndex;
    if (finalPos >= uh) {
      // Dropped at/after the end of the unfinished zone.
      newHabitIndex = habits.indexOf(unfinishedHabits.last) + 1;
    } else {
      newHabitIndex =
          habits.indexOf(unfinishedHabits[finalPos]) + (dropAfter ? 1 : 0);
    }
    if (newHabitIndex == oldHabitIndex || newHabitIndex == oldHabitIndex + 1) {
      return null;
    }
    return ReorderMapping(
      kind: 'habit',
      oldProviderIndex: oldHabitIndex,
      newProviderIndex: newHabitIndex,
    );
  }

  if (oldItem is Todo) {
    // Todo drag — only among incomplete todos.
    if (oldItem.isCompleted) return null;
    final dropAfter = newIndex > oldIndex;
    final finalPos = (newIndex - uh).clamp(0, it);
    final oldTodoIndex = todos.indexOf(oldItem);
    final newTodoIndex = finalPos >= it
        ? todos.length
        : todos.indexOf(incompleteTodos[finalPos]) + (dropAfter ? 1 : 0);
    if (newTodoIndex == oldTodoIndex || newTodoIndex == oldTodoIndex + 1) {
      return null;
    }
    return ReorderMapping(
      kind: 'todo',
      oldProviderIndex: oldTodoIndex,
      newProviderIndex: newTodoIndex,
    );
  }

  return null;
}

class TodoScreen extends StatefulWidget {
  const TodoScreen({super.key});

  @override
  State<TodoScreen> createState() => _TodoScreenState();
}

class _TodoScreenState extends State<TodoScreen> with TickerProviderStateMixin {
  // ---- Combined display list (order rules in buildCombinedList) ----
  List<Object> _combined(AppProvider p) => buildCombinedList(p.todos, p.habits);

  void _onReorder(int oldIndex, int newIndex) {
    final provider = context.read<AppProvider>();
    final mapping = mapReorder(
      todos: provider.todos,
      habits: provider.habits,
      oldIndex: oldIndex,
      newIndex: newIndex,
    );
    if (mapping == null) return;
    if (mapping.kind == 'habit') {
      provider.reorderHabits(mapping.oldProviderIndex, mapping.newProviderIndex);
    } else {
      provider.reorderTodos(mapping.oldProviderIndex, mapping.newProviderIndex);
    }
  }

  // ---- Tap on TODO: start timer if timed, toggle if no timing ----
  void _onTodoTap(BuildContext context, Todo todo) {
    if (todo.isCompleted) {
      context.read<AppProvider>().toggleTodoComplete(todo);
      return;
    }
    if (todo.timingType == TimingType.none) {
      context.read<AppProvider>().toggleTodoComplete(todo);
    } else {
      Navigator.of(
        context,
      ).push(MaterialPageRoute(builder: (_) => TimerScreen(todo: todo)));
    }
  }

  // ---- Custom FAB overlay: two large buttons (TODO / Habit) ----
  bool _overlayShowing = false;

  void _showAddOverlay() {
    if (_overlayShowing) return; // #8: debounce — prevent multiple overlays
    _overlayShowing = true;

    final overlay = Overlay.of(context);
    late OverlayEntry entry;
    late AnimationController animController;
    late Animation<double> scaleAnim;

    animController = AnimationController(
      duration: const Duration(milliseconds: 200),
      vsync: this,
    );
    scaleAnim = CurvedAnimation(
      parent: animController,
      curve: Curves.easeOutBack,
    );

    void dismiss() {
      _overlayShowing = false;
      animController.reverse().then((_) {
        entry.remove();
        animController.dispose();
      });
    }

    entry = OverlayEntry(
      builder: (ctx) => _AddOverlay(
        scaleAnim: scaleAnim,
        onDismiss: dismiss,
        onTodo: () {
          dismiss();
          _openTodoEdit(null);
        },
        onHabit: () {
          dismiss();
          _openHabitEdit(null);
        },
      ),
    );
    overlay.insert(entry);
    animController.forward();
  }

  // ---- Long-press options: edit / delete ----
  void _showItemOptions(Object item) {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: Text(s.edit),
              onTap: () {
                Navigator.pop(ctx);
                if (item is Todo) {
                  _openTodoEdit(item);
                } else if (item is Habit) {
                  _openHabitEdit(item);
                }
              },
            ),
            ListTile(
              leading: const Icon(
                Icons.delete_outline,
                color: Colors.redAccent,
              ),
              title: Text(
                s.delete,
                style: const TextStyle(color: Colors.redAccent),
              ),
              onTap: () {
                Navigator.pop(ctx);
                _confirmDelete(item);
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  Future<void> _showDesktopContextMenu(
    Object item,
    TapDownDetails details,
  ) async {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final overlay =
        Overlay.of(context).context.findRenderObject()! as RenderBox;
    final action = await showMenu<String>(
      context: context,
      position: RelativeRect.fromRect(
        Rect.fromPoints(details.globalPosition, details.globalPosition),
        Offset.zero & overlay.size,
      ),
      items: [
        PopupMenuItem(
          value: 'edit',
          child: ListTile(
            dense: true,
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.edit_outlined),
            title: Text(s.edit),
          ),
        ),
        PopupMenuItem(
          value: 'delete',
          child: ListTile(
            dense: true,
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.delete_outline, color: Colors.redAccent),
            title: Text(
              s.delete,
              style: const TextStyle(color: Colors.redAccent),
            ),
          ),
        ),
      ],
    );
    if (!mounted || action == null) return;
    if (action == 'edit') {
      if (item is Todo) await _openTodoEdit(item);
      if (item is Habit) await _openHabitEdit(item);
    } else if (action == 'delete') {
      await _confirmDelete(item);
    }
  }

  Future<void> _confirmDelete(Object item) async {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    String title;
    if (item is Todo) {
      title = item.title;
    } else if (item is Habit) {
      title = item.title;
    } else {
      title = s.genericItem;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(s.delete),
        content: Text(s.confirmDelete(title)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text(s.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: Colors.redAccent),
            child: Text(s.delete),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    final provider = context.read<AppProvider>();
    if (item is Todo) {
      await provider.deleteTodo(item.id);
    } else if (item is Habit) {
      await provider.deleteHabit(item.id);
    }
  }

  // ---- TODO edit/add ----
  Future<void> _openTodoEdit(Todo? existing) async {
    final result = await showDialog<Todo>(
      context: context,
      builder: (_) => TodoEditDialog(initial: existing),
    );
    if (result == null || !mounted) return;
    final provider = context.read<AppProvider>();
    if (existing == null) {
      await provider.addTodo(result.copyWith(sortOrder: provider.todos.length));
    } else {
      await provider.updateTodo(result);
    }
  }

  // ---- Habit edit/add ----
  Future<void> _openHabitEdit(Habit? existing) async {
    final result = await showDialog<Habit>(
      context: context,
      builder: (_) => HabitEditDialog(initial: existing),
    );
    if (result == null || !mounted) return;
    final provider = context.read<AppProvider>();
    if (existing == null) {
      await provider.addHabit(
        result.copyWith(sortOrder: provider.habits.length),
      );
    } else {
      await provider.updateHabit(result);
    }
  }

  Widget _proxyDecorator(Widget child, int index, Animation<double> animation) {
    return Material(
      color: Colors.transparent,
      elevation: 6,
      shadowColor: Colors.black26,
      borderRadius: BorderRadius.circular(16),
      child: child,
    );
  }

  Widget _buildEmptyState() {
    final theme = Theme.of(context);
    final s = S.of(context.read<AppLocaleProvider>().locale);
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.check_circle_outline, size: 72, color: theme.dividerColor),
          const SizedBox(height: 16),
          Text(s.noTodos, style: theme.textTheme.titleMedium),
          const SizedBox(height: 6),
          Text(s.noTodosHint, style: theme.textTheme.bodySmall),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final items = _combined(provider);
    final doneCount = provider.todos.where((t) => t.isCompleted).length;
    final habitDoneCount = provider.habits.where((h) => h.isCompleted).length;

    return Scaffold(
      appBar: AppBar(
        title: Text(s.todo),
        actions: [
          if (items.isNotEmpty)
            Center(
              child: Padding(
                padding: const EdgeInsets.only(right: 16),
                child: Text(
                  '${s.completed} $doneCount/${provider.todos.length}'
                  '${provider.habits.isNotEmpty ? ' · ${s.habits} $habitDoneCount/${provider.habits.length}' : ''}',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ),
            ),
        ],
      ),
      body: items.isEmpty
          ? _buildEmptyState()
          : ReorderableListView.builder(
              buildDefaultDragHandles: false,
              itemCount: items.length,
              // onReorderItem already delivers the final insertion index with
              // the dragged item conceptually removed — hand it to _onReorder
              // unchanged (see buildCombinedList for the zone layout).
              onReorderItem: (oldIndex, newIndex) {
                _onReorder(oldIndex, newIndex);
              },
              proxyDecorator: _proxyDecorator,
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 88),
              itemBuilder: (context, index) {
                final item = items[index];
                if (item is Todo) {
                  return TodoCard(
                    key: Key('todo_${item.id}'),
                    todo: item,
                    index: index,
                    onToggle: () => _onTodoTap(context, item),
                    onLongPress: () => _showItemOptions(item),
                    onSecondaryTapDown: (details) =>
                        _showDesktopContextMenu(item, details),
                    showDragHandle: !item.isCompleted,
                  );
                }
                if (item is Habit) {
                  return HabitCard(
                    key: Key('habit_${item.id}'),
                    habit: item,
                    index: index,
                    showDragHandle: !item.isCompleted,
                    onIncrement: () =>
                        context.read<AppProvider>().incrementHabit(item),
                    onDecrement: () =>
                        context.read<AppProvider>().decrementHabit(item),
                    onLongPress: () => _showItemOptions(item),
                    onSecondaryTapDown: (details) =>
                        _showDesktopContextMenu(item, details),
                  );
                }
                return SizedBox.shrink(key: Key('empty_$index'));
              },
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: _showAddOverlay,
        tooltip: s.add,
        child: const Icon(Icons.add),
      ),
    );
  }
}

// ---- Full-screen overlay with two large buttons ----
class _AddOverlay extends StatelessWidget {
  final Animation<double> scaleAnim;
  final VoidCallback onDismiss;
  final VoidCallback onTodo;
  final VoidCallback onHabit;

  const _AddOverlay({
    required this.scaleAnim,
    required this.onDismiss,
    required this.onTodo,
    required this.onHabit,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.black54,
      child: GestureDetector(
        onTap: onDismiss,
        behavior: HitTestBehavior.opaque,
        child: SizedBox.expand(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final diameter = (constraints.maxWidth * 0.22).clamp(
                112.0,
                184.0,
              );
              return Stack(
                children: [
                  Positioned(
                    left: constraints.maxWidth * 0.25 - diameter / 2,
                    top: constraints.maxHeight / 2 - diameter / 2,
                    child: ScaleTransition(
                      scale: scaleAnim,
                      child: _BigButton(
                        label: 'TODO',
                        color: const Color(0xFFE85D3C),
                        diameter: diameter,
                        onTap: onTodo,
                      ),
                    ),
                  ),
                  Positioned(
                    left: constraints.maxWidth * 0.75 - diameter / 2,
                    top: constraints.maxHeight / 2 - diameter / 2,
                    child: ScaleTransition(
                      scale: scaleAnim,
                      child: _BigButton(
                        label: 'HABIT',
                        color: const Color(0xFF4A90D9),
                        diameter: diameter,
                        onTap: onHabit,
                      ),
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class _BigButton extends StatelessWidget {
  final String label;
  final Color color;
  final double diameter;
  final VoidCallback onTap;

  const _BigButton({
    required this.label,
    required this.color,
    required this.diameter,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: diameter,
        height: diameter,
        decoration: BoxDecoration(
          color: color,
          shape: BoxShape.circle,
          boxShadow: [
            BoxShadow(
              color: color.withValues(alpha: 0.4),
              blurRadius: 16,
              offset: const Offset(0, 6),
            ),
          ],
        ),
        alignment: Alignment.center,
        child: Text(
          label,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 24,
            fontWeight: FontWeight.w900,
            letterSpacing: 1.5,
            decoration: TextDecoration.none,
          ),
        ),
      ),
    );
  }
}
