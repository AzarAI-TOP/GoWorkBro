import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

/// Login / Register screen.
/// Shown when the user is not authenticated.
/// On success, AuthGate's onAuthStateChange listener navigates to the main app.
class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isLogin = true;
  bool _loading = false;
  bool _obscurePassword = true;
  String? _error;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  /// Dev quick-login with test credentials (debug mode only)
  Future<void> _devQuickLogin() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await Supabase.instance.client.auth.signInWithPassword(
        email: const String.fromEnvironment('TEST_EMAIL', defaultValue: ''),
        password: const String.fromEnvironment('TEST_PASSWORD', defaultValue: ''),
      );
    } on AuthException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (e) {
      if (mounted) setState(() => _error = '登录失败: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _submit() async {
    final email = _emailController.text.trim();
    final password = _passwordController.text.trim();

    if (email.isEmpty || password.isEmpty) {
      setState(() => _error = '请输入邮箱和密码');
      return;
    }
    if (password.length < 6) {
      setState(() => _error = '密码至少 6 位');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final client = Supabase.instance.client;
      if (_isLogin) {
        await client.auth.signInWithPassword(
          email: email,
          password: password,
        );
      } else {
        await client.auth.signUp(
          email: email,
          password: password,
        );
        if (client.auth.currentSession == null) {
          if (mounted) {
            setState(() {
              _error = '注册成功！请检查邮箱完成验证后登录。';
              _isLogin = true;
              _loading = false;
            });
            return;
          }
        }
      }
    } on AuthException catch (e) {
      if (mounted) setState(() => _error = _friendlyError(e.message));
    } catch (_) {
      if (mounted) setState(() => _error = '网络错误，请检查连接后重试');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _friendlyError(String msg) {
    if (msg.contains('Invalid login credentials')) return '邮箱或密码错误';
    if (msg.contains('Email not confirmed')) return '邮箱未验证，请检查邮箱';
    if (msg.contains('already registered')) return '该邮箱已注册';
    if (msg.contains('rate limit')) return '操作过于频繁，请稍后再试';
    return msg;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 360),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Logo
                  Container(
                    width: 80,
                    height: 80,
                    margin: const EdgeInsets.only(bottom: 24),
                    decoration: BoxDecoration(
                      color: cs.primary,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Center(
                      child: Text(
                        'GO!',
                        style: TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.w800,
                          fontStyle: FontStyle.italic,
                          color: cs.onPrimary,
                        ),
                      ),
                    ),
                  ),
                  Text(
                    _isLogin ? '登录' : '注册',
                    style: theme.textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _isLogin
                        ? '登录你的账号，同步跨设备数据'
                        : '创建新账号，开始记录你的专注时光',
                    style: theme.textTheme.bodyMedium,
                  ),
                  const SizedBox(height: 32),

                  // Email
                  TextField(
                    controller: _emailController,
                    decoration: const InputDecoration(
                      labelText: '邮箱',
                      hintText: 'you@example.com',
                      prefixIcon: Icon(Icons.email_outlined),
                    ),
                    keyboardType: TextInputType.emailAddress,
                    textInputAction: TextInputAction.next,
                    enabled: !_loading,
                  ),
                  const SizedBox(height: 16),

                  // Password with visibility toggle
                  TextField(
                    controller: _passwordController,
                    decoration: InputDecoration(
                      labelText: '密码',
                      hintText: '至少 6 位',
                      prefixIcon: const Icon(Icons.lock_outline),
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscurePassword
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined,
                        ),
                        onPressed: () {
                          setState(() => _obscurePassword = !_obscurePassword);
                        },
                      ),
                    ),
                    obscureText: _obscurePassword,
                    textInputAction: TextInputAction.done,
                    onSubmitted: (_) => _submit(),
                    enabled: !_loading,
                  ),
                  const SizedBox(height: 24),

                  // Error message
                  if (_error != null) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: cs.error.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Row(
                        children: [
                          Icon(Icons.error_outline, color: cs.error, size: 18),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _error!,
                              style: TextStyle(color: cs.error, fontSize: 13),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],

                  // Submit button
                  FilledButton(
                    onPressed: _loading ? null : _submit,
                    child: _loading
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : Text(_isLogin ? '登录' : '注册'),
                  ),
                  const SizedBox(height: 16),

                  // Toggle login/register
                  TextButton(
                    onPressed: _loading
                        ? null
                        : () {
                            setState(() {
                              _isLogin = !_isLogin;
                              _error = null;
                            });
                          },
                    child: Text(
                      _isLogin
                          ? '没有账号？点击注册'
                          : '已有账号？点击登录',
                    ),
                  ),
                  // Dev quick-login (debug mode only)
                  if (kDebugMode)
                    TextButton(
                      onPressed: _loading ? null : _devQuickLogin,
                      child: const Text(
                        '🧪 快速登录 (测试)',
                        style: TextStyle(fontSize: 12),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
