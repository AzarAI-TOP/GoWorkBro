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
  workout_duration_minutes int,
  note text,
  updated_at timestamptz not null default now()
);

-- Upgrade existing projects created before workout/sync schema parity.
alter table public.sleep_records
  add column if not exists workout_time text;
alter table public.sleep_records
  add column if not exists workout_duration_minutes int;
alter table public.sleep_records
  add column if not exists note text;
alter table public.sleep_records
  add column if not exists updated_at timestamptz not null default now();
update public.sleep_records
  set updated_at = now()
  where updated_at is null;
alter table public.sleep_records
  alter column updated_at set default now();
alter table public.sleep_records
  alter column updated_at set not null;

-- Older clients used per-device UUIDs and could create more than one row for
-- the same user/day. Merge the newest non-null values into the most recently
-- updated row before installing the natural-key uniqueness required by
-- PostgREST upsert. This keeps partial check-ins from either device.
with ranked as (
  select
    id,
    row_number() over day_window as row_rank,
    first_value(wake_time) over wake_window as merged_wake_time,
    first_value(sleep_time) over sleep_window as merged_sleep_time,
    first_value(workout_time) over workout_window as merged_workout_time,
    first_value(workout_duration_minutes) over duration_window
      as merged_workout_duration_minutes,
    first_value(note) over note_window as merged_note,
    max(updated_at) over day_partition as merged_updated_at
  from public.sleep_records
  window
    day_partition as (partition by user_id, record_date),
    day_window as (
      day_partition order by updated_at desc nulls last, id desc
    ),
    wake_window as (
      day_partition order by (wake_time is not null) desc,
        updated_at desc nulls last, id desc
    ),
    sleep_window as (
      day_partition order by (sleep_time is not null) desc,
        updated_at desc nulls last, id desc
    ),
    workout_window as (
      day_partition order by (workout_time is not null) desc,
        updated_at desc nulls last, id desc
    ),
    duration_window as (
      day_partition order by (workout_duration_minutes is not null) desc,
        updated_at desc nulls last, id desc
    ),
    note_window as (
      day_partition order by (note is not null) desc,
        updated_at desc nulls last, id desc
    )
)
update public.sleep_records as target
set
  wake_time = ranked.merged_wake_time,
  sleep_time = ranked.merged_sleep_time,
  workout_time = ranked.merged_workout_time,
  workout_duration_minutes = ranked.merged_workout_duration_minutes,
  note = ranked.merged_note,
  updated_at = ranked.merged_updated_at
from ranked
where target.id = ranked.id and ranked.row_rank = 1;

with ranked as (
  select
    id,
    row_number() over (
      partition by user_id, record_date
      order by updated_at desc nulls last, id desc
    ) as row_rank
  from public.sleep_records
)
delete from public.sleep_records as target
using ranked
where target.id = ranked.id and ranked.row_rank > 1;

-- One logical sleep/workout row per user and wake-up date. This is also the
-- conflict target used by clients syncing records created on different devices.
create unique index if not exists idx_sleep_records_user_record_date_unique
  on public.sleep_records(user_id, record_date);

-- 6. User settings table (key-value)
create table if not exists public.user_settings (
  key text not null,
  user_id uuid references auth.users(id) default auth.uid(),
  value text not null,
  updated_at timestamptz not null default now(),
  primary key (user_id, key)
);

-- Enforce last-write-wins at the database boundary. Client-side timestamp
-- inventory prevents most stale writes, while this trigger closes the race
-- between that comparison and the subsequent PostgREST upsert. The logical
-- day closing boundary is a monotonic value, so a greater ISO date wins even
-- when it was produced by a device whose clock is behind.
create or replace function public.merge_user_setting_lww()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if old.key = 'late_night_closed_through'
      and new.value ~ '^\d{4}-\d{2}-\d{2}$'
      and old.value ~ '^\d{4}-\d{2}-\d{2}$'
      and new.value <> old.value then
    if new.value > old.value then
      return new;
    end if;
    return old;
  end if;

  if new.updated_at is null or new.updated_at <= old.updated_at then
    return old;
  end if;
  return new;
end;
$$;

drop trigger if exists user_settings_lww on public.user_settings;
create trigger user_settings_lww
before update on public.user_settings
for each row execute function public.merge_user_setting_lww();

-- 7. USTC daily news table (public read; written only via the validated RPC
--    defined at the bottom of this file)
create table if not exists public.ustc_news (
  id bigint generated by default as identity primary key,
  date text not null unique,
  title text not null,
  content text not null,
  created_at timestamptz not null default now()
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
alter table public.ustc_news enable row level security;

-- Policies: users can do everything with their own rows
drop policy if exists "Users can CRUD own todos" on public.todos;
create policy "Users can CRUD own todos" on public.todos
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists "Users can CRUD own habits" on public.habits;
create policy "Users can CRUD own habits" on public.habits
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists "Users can CRUD own focus_sessions" on public.focus_sessions;
create policy "Users can CRUD own focus_sessions" on public.focus_sessions
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists "Users can CRUD own countdowns" on public.countdowns;
create policy "Users can CRUD own countdowns" on public.countdowns
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists "Users can CRUD own sleep_records" on public.sleep_records;
create policy "Users can CRUD own sleep_records" on public.sleep_records
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists "Users can CRUD own settings" on public.user_settings;
create policy "Users can CRUD own settings" on public.user_settings
  for all to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists "Anyone can read USTC news" on public.ustc_news;
create policy "Anyone can read USTC news" on public.ustc_news
  for select to anon, authenticated
  using (true);

-- ============================================================
-- Realtime — enable live sync between devices
-- ============================================================
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'todos'
  ) then alter publication supabase_realtime add table public.todos; end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'habits'
  ) then alter publication supabase_realtime add table public.habits; end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'focus_sessions'
  ) then alter publication supabase_realtime add table public.focus_sessions; end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'countdowns'
  ) then alter publication supabase_realtime add table public.countdowns; end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'sleep_records'
  ) then alter publication supabase_realtime add table public.sleep_records; end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'user_settings'
  ) then alter publication supabase_realtime add table public.user_settings; end if;
end $$;

-- ============================================================
-- Indexes for faster queries
-- ============================================================
create index if not exists idx_todos_user on public.todos(user_id);
create index if not exists idx_habits_user on public.habits(user_id);
create index if not exists idx_focus_sessions_user_date on public.focus_sessions(user_id, session_date);
create index if not exists idx_countdowns_user on public.countdowns(user_id);

-- ============================================================
-- Storage: avatar bucket (added in v1.1.1)
-- Run this section once in: Supabase Dashboard → SQL Editor
-- ============================================================

-- Public bucket: avatars are readable without auth; uploads restricted
-- to the owning user's own folder (<uid>/avatar.ext).
insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do nothing;

drop policy if exists "avatars upload own" on storage.objects;
create policy "avatars upload own"
  on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "avatars update own" on storage.objects;
create policy "avatars update own"
  on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "avatars delete own" on storage.objects;
create policy "avatars delete own"
  on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- ============================================================
-- News ingestion RPC + API surface hardening
-- ============================================================

-- Validated, idempotent news upsert. SECURITY DEFINER so the anon-key upload
-- script needs no secret, but writes are limited to this exact operation with
-- validated inputs (ISO date, not in the future, size caps).
create or replace function public.upsert_ustc_news(
  p_date text,
  p_title text,
  p_content text
)
returns public.ustc_news
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_date date;
  v_row public.ustc_news;
begin
  if p_date !~ '^\d{4}-\d{2}-\d{2}$' then
    raise exception 'date must be a valid ISO date (YYYY-MM-DD)';
  end if;
  v_date := to_date(p_date, 'YYYY-MM-DD');
  if v_date > current_date then
    raise exception 'news date cannot be in the future';
  end if;
  if p_title is null or btrim(p_title) = '' then
    raise exception 'title is required';
  end if;
  if p_content is null or btrim(p_content) = '' then
    raise exception 'content is required';
  end if;
  if length(p_title) > 300 then
    raise exception 'title exceeds 300 characters';
  end if;
  if length(p_content) > 500000 then
    raise exception 'content exceeds 500000 characters';
  end if;

  insert into public.ustc_news (date, title, content)
  values (p_date, btrim(p_title), p_content)
  on conflict (date) do update
    set title = excluded.title,
        content = excluded.content
  returning * into v_row;

  return v_row;
end;
$$;

-- API surface hardening: future objects do not leak grants automatically,
-- and the current blanket grants are replaced with the explicit matrix below.
alter default privileges in schema public revoke all on tables from anon, authenticated;
alter default privileges in schema public revoke all on sequences from anon, authenticated;
alter default privileges in schema public revoke all on functions from anon, authenticated;

revoke all on all tables in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
revoke all on all functions in schema public from anon, authenticated;

-- Read: public news
grant select on table public.ustc_news to anon, authenticated;

-- Read/write: the user's own synced tables (rows filtered by RLS)
grant select, insert, update, delete on table public.todos to authenticated;
grant select, insert, update, delete on table public.habits to authenticated;
grant select, insert, update, delete on table public.focus_sessions to authenticated;
grant select, insert, update, delete on table public.countdowns to authenticated;
grant select, insert, update, delete on table public.sleep_records to authenticated;
grant select, insert, update, delete on table public.user_settings to authenticated;

-- Ingestion RPC: anon role only (upload script); PUBLIC default revoked above.
revoke execute on function public.upsert_ustc_news(text, text, text) from public;
grant execute on function public.upsert_ustc_news(text, text, text) to anon;

-- LWW trigger executes as the DML user.
grant execute on function public.merge_user_setting_lww() to authenticated;
