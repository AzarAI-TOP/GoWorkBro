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

class TodoScreen extends StatefulWidget {
  const TodoScreen({super.key});

  @override
  State<TodoScreen> createState() => _TodoScreenState();
}

class _TodoScreenState extends State<TodoScreen> {
  // ---- Combined display list: TODOs first, then Habits ----
  List<Object> _combined(AppProvider p) => <Object>[...p.todos, ...p.habits];

  void _onReorder(int oldIndex, int newIndex) {
    final provider = context.read<AppProvider>();
    final todoCount = provider.todos.length;
    final habitCount = provider.habits.length;

    if (oldIndex < todoCount) {
      // Dragged a TODO → reorder within todos (clamp into todo region)
      var newTodoIndex = newIndex;
      if (newTodoIndex < 0) newTodoIndex = 0;
      if (newTodoIndex > todoCount) newTodoIndex = todoCount;
      provider.reorderTodos(oldIndex, newTodoIndex);
    } else {
      // Dragged a Habit → reorder within habits
      final oldHabitIndex = oldIndex - todoCount;
      var newHabitIndex = newIndex - todoCount;
      if (newHabitIndex < 0) newHabitIndex = 0;
      if (newHabitIndex > habitCount) newHabitIndex = habitCount;
      provider.reorderHabits(oldHabitIndex, newHabitIndex);
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

  // ---- Add sheet: choose TODO or Habit ----
  void _showAddSheet() {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.only(top: 4, bottom: 8),
              child: Text('新建', style: Theme.of(context).textTheme.titleMedium),
            ),
            ListTile(
              leading: const Icon(Icons.check_circle_outline),
              title: const Text('新建待办'),
              subtitle: const Text('一次性任务 · 支持计时'),
              onTap: () {
                Navigator.pop(ctx);
                _openTodoEdit(null);
              },
            ),
            ListTile(
              leading: const Icon(Icons.repeat_rounded),
              title: const Text('新建习惯'),
              subtitle: const Text('每日打卡 · 计量目标'),
              onTap: () {
                Navigator.pop(ctx);
                _openHabitEdit(null);
              },
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  // ---- Long-press options: edit / delete ----
  void _showItemOptions(Object item) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.edit_outlined),
              title: const Text('编辑'),
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
              title: const Text('删除', style: TextStyle(color: Colors.redAccent)),
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
        title: const Text('删除'),
        content: Text('确定删除「$title」吗？此操作不可撤销。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: Colors.redAccent),
            child: const Text('删除'),
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
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.check_circle_outline, size: 72, color: theme.dividerColor),
          const SizedBox(height: 16),
          Text('还没有待办事项', style: theme.textTheme.titleMedium),
          const SizedBox(height: 6),
          Text('点击右下角 + 添加待办或习惯', style: theme.textTheme.bodySmall),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    final items = _combined(provider);
    final doneCount = provider.todos.where((t) => t.isCompleted).length;
    final habitDoneCount = provider.habits.where((h) => h.isCompleted).length;

    return Scaffold(
      appBar: AppBar(
        title: const Text('待办'),
        actions: [
          if (items.isNotEmpty)
            Center(
              child: Padding(
                padding: const EdgeInsets.only(right: 16),
                child: Text(
                  '已完成 $doneCount/${provider.todos.length}'
                  '${provider.habits.isNotEmpty ? ' · 习惯 $habitDoneCount/${provider.habits.length}' : ''}',
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
        onPressed: _showAddSheet,
        tooltip: '添加',
        child: const Icon(Icons.add),
      ),
    );
  }
}
