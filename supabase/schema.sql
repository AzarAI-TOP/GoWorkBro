-- ============================================================
-- GoWorkBro — Supabase Schema
-- Run this in: Supabase Dashboard → SQL Editor → New query
-- ============================================================

-- 1. Todos table
create table if not exists public.todos (
  id text primary key,
  user_id uuid references auth.users(id) default auth.uid(),
  title text not null,
  timing_type text not null default 'forward',
  duration_minutes int not null default 25,
  is_completed boolean not null default false,
  sort_order int not null default 0,
  keep_tomorrow boolean not null default true,
  created_date text not null,
  completed_date text,
  actual_duration_seconds int not null default 0,
  updated_at timestamptz not null default now()
);

-- 2. Habits table
create table if not exists public.habits (
  id text primary key,
  user_id uuid references auth.users(id) default auth.uid(),
  title text not null,
  target_count int not null default 1,
  unit text not null default '次',
  sort_order int not null default 0,
  created_date text not null,
  current_count int not null default 0,
  last_reset_date text,
  updated_at timestamptz not null default now()
);

-- 3. Focus sessions table
create table if not exists public.focus_sessions (
  id text primary key,
  user_id uuid references auth.users(id) default auth.uid(),
  todo_id text,
  source_type text not null,
  source_title text not null,
  start_time text not null,
  end_time text not null,
  duration_seconds int not null,
  session_date text not null,
  created_at timestamptz not null default now()
);

-- 4. Countdowns table
create table if not exists public.countdowns (
  id text primary key,
  user_id uuid references auth.users(id) default auth.uid(),
  title text not null,
  target_datetime text not null,
  created_date text not null,
  color_index int not null default 0,
  updated_at timestamptz not null default now()
);

-- 5. Sleep records table
create table if not exists public.sleep_records (
  id text primary key,
  user_id uuid references auth.users(id) default auth.uid(),
  record_date text not null,
  wake_time text,
  sleep_time text,
  workout_time text,
  note text,
  updated_at timestamptz not null default now()
);

-- 6. User settings table (key-value)
create table if not exists public.user_settings (
  key text not null,
  user_id uuid references auth.users(id) default auth.uid(),
  value text not null,
  updated_at timestamptz not null default now(),
  primary key (user_id, key)
);

-- ============================================================
-- Row Level Security (RLS) — each user only sees their own data
-- ============================================================
alter table public.todos enable row level security;
alter table public.habits enable row level security;
alter table public.focus_sessions enable row level security;
alter table public.countdowns enable row level security;
alter table public.sleep_records enable row level security;
alter table public.user_settings enable row level security;

-- Policies: users can do everything with their own rows
create policy "Users can CRUD own todos" on public.todos
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "Users can CRUD own habits" on public.habits
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "Users can CRUD own focus_sessions" on public.focus_sessions
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "Users can CRUD own countdowns" on public.countdowns
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "Users can CRUD own sleep_records" on public.sleep_records
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "Users can CRUD own settings" on public.user_settings
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ============================================================
-- Realtime — enable live sync between devices
-- ============================================================
alter publication supabase_realtime add table public.todos;
alter publication supabase_realtime add table public.habits;
alter publication supabase_realtime add table public.focus_sessions;
alter publication supabase_realtime add table public.countdowns;
alter publication supabase_realtime add table public.sleep_records;
alter publication supabase_realtime add table public.user_settings;

-- ============================================================
-- Indexes for faster queries
-- ============================================================
create index if not exists idx_todos_user on public.todos(user_id);
create index if not exists idx_habits_user on public.habits(user_id);
create index if not exists idx_focus_sessions_user_date on public.focus_sessions(user_id, session_date);
create index if not exists idx_countdowns_user on public.countdowns(user_id);
create index if not exists idx_sleep_records_user on public.sleep_records(user_id);
create index if not exists idx_user_settings_user on public.user_settings(user_id);
