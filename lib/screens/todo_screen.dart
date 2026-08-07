/// 待办 (Todo) screen — mixed list of TODO + Habit items
/// 番茄TODO-inspired clean geometric design
library;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/models.dart';
import '../providers/app_provider.dart';
import 'timer_screen.dart';
import '../widgets/todo/todo_card.dart';
import '../widgets/todo/habit_card.dart';
import '../widgets/todo/todo_edit_dialog.dart';
import '../widgets/todo/habit_edit_dialog.dart';
import '../services/app_locale.dart';

class TodoScreen extends StatefulWidget {
  const TodoScreen({super.key});

  @override
  State<TodoScreen> createState() => _TodoScreenState();
}

class _TodoScreenState extends State<TodoScreen> with SingleTickerProviderStateMixin {
  // ---- Combined display list ----
  // Order: incomplete todos (by sortOrder) → completed todos (by completedDate desc) → habits
  List<Object> _combined(AppProvider p) {
    final incompleteTodos = p.todos.where((t) => !t.isCompleted).toList()
      ..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
    final completedTodos = p.todos.where((t) => t.isCompleted).toList()
      ..sort((a, b) {
        final ad = a.completedDate ?? '';
        final bd = b.completedDate ?? '';
        return bd.compareTo(ad); // desc — most recently completed first
      });
    return <Object>[...incompleteTodos, ...completedTodos, ...p.habits];
  }

  void _onReorder(int oldIndex, int newIndex) {
    final provider = context.read<AppProvider>();
    final items = _combined(provider);
    final oldItem = items[oldIndex];

    if (oldItem is Habit) {
      // Habit drag — find the target habit index.
      final habits = provider.habits;
      final oldHabitIndex = habits.indexOf(oldItem);
      // Count how many habits come before the drop position
      final habitStartIndex = items.length - habits.length;
      var newHabitIndex = newIndex - habitStartIndex;
      if (newHabitIndex < 0) newHabitIndex = 0;
      if (newHabitIndex > habits.length) newHabitIndex = habits.length;
      if (oldHabitIndex == newHabitIndex || newHabitIndex == oldHabitIndex + 1) return;
      provider.reorderHabits(oldHabitIndex, newHabitIndex);
    } else if (oldItem is Todo) {
      // Todo drag — only allow reordering among incomplete todos.
      if (oldItem.isCompleted) return;
      final incompleteTodos = provider.todos.where((t) => !t.isCompleted).toList();
      final oldIncompleteIndex = incompleteTodos.indexOf(oldItem);
      // Map display index to incomplete-todo index
      var newIncompleteIndex = newIndex;
      if (newIncompleteIndex > oldIndex) newIncompleteIndex--; // undo Flutter's pre-adjustment
      newIncompleteIndex = newIncompleteIndex.clamp(0, incompleteTodos.length);
      if (oldIncompleteIndex == newIncompleteIndex) return;

      // Find the target position in the full provider.todos list
      // by counting incomplete todos up to the new position
      final targetTodo = newIncompleteIndex < incompleteTodos.length
          ? incompleteTodos[newIncompleteIndex]
          : null;
      final newTodoIndex = targetTodo != null
          ? provider.todos.indexOf(targetTodo)
          : provider.todos.length;
      provider.reorderTodos(provider.todos.indexOf(oldItem), newTodoIndex);
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
      Navigator.of(context).push(
        MaterialPageRoute(builder: (_) => TimerScreen(todo: todo)),
      );
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
    scaleAnim = CurvedAnimation(parent: animController, curve: Curves.easeOutBack);

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
              leading: const Icon(Icons.delete_outline, color: Colors.redAccent),
              title: Text(s.delete, style: const TextStyle(color: Colors.redAccent)),
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

  Future<void> _confirmDelete(Object item) async {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    String title;
    if (item is Todo) {
      title = item.title;
    } else if (item is Habit) {
      title = item.title;
    } else {
      title = '此项';
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(s.delete),
        content: Text(s.confirmDelete(title)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(s.cancel)),
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
      await provider.addHabit(result.copyWith(sortOrder: provider.habits.length));
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
              // onReorderItem delivers a *pre-adjusted* newIndex (the dragged
              // item is conceptually already removed from oldIndex), whereas
              // the provider's reorder* methods expect the legacy onReorder
              // convention (newIndex may point one past the slot). Undo the
              // adjustment so we can hand the provider the index it expects.
              onReorderItem: (oldIndex, newIndex) {
                if (newIndex > oldIndex) newIndex += 1;
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
                    showDragHandle: !item.isCompleted,
                  );
                }
                if (item is Habit) {
                  return HabitCard(
                    key: Key('habit_${item.id}'),
                    habit: item,
                    index: index,
                    onIncrement: () =>
                        context.read<AppProvider>().incrementHabit(item),
                    onDecrement: () =>
                        context.read<AppProvider>().decrementHabit(item),
                    onLongPress: () => _showItemOptions(item),
                  );
                }
                return SizedBox.shrink(key: Key('empty_$index'));
              },
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: _showAddOverlay,
        tooltip: '添加',
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
    final screenSize = MediaQuery.of(context).size;

    return Material(
      color: Colors.black54,
      child: GestureDetector(
        onTap: onDismiss,
        behavior: HitTestBehavior.opaque,
        child: Container(
          width: screenSize.width,
          height: screenSize.height,
          color: Colors.transparent,
          child: Center(
            child: GestureDetector(
              onTap: () {}, // swallow taps on the buttons row
              child: ScaleTransition(
                scale: scaleAnim,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    // Left button — TODO (25% width, orange)
                    _BigButton(
                      label: 'TODO',
                      color: const Color(0xFFE85D3C),
                      widthFraction: 0.25,
                      onTap: onTodo,
                    ),
                    const SizedBox(width: 16),
                    // Right button — Habit (75% width, blue)
                    _BigButton(
                      label: 'Habit',
                      color: const Color(0xFF4A90D9),
                      widthFraction: 0.75,
                      onTap: onHabit,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _BigButton extends StatelessWidget {
  final String label;
  final Color color;
  final double widthFraction;
  final VoidCallback onTap;

  const _BigButton({
    required this.label,
    required this.color,
    required this.widthFraction,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final screenW = MediaQuery.of(context).size.width;
    final btnWidth = screenW * widthFraction;
    // Keep ~120 tall, but if width fraction is small, use at least 120
    final btnHeight = 120.0;

    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: btnWidth,
        height: btnHeight,
        decoration: BoxDecoration(
          color: color,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: color.withValues(alpha: 0.4),
              blurRadius: 16,
              offset: const Offset(0, 6),
            ),
          ],
        ),
        child: Center(
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
      ),
    );
  }
}
