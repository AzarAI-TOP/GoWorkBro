-- These indexes are fully covered by wider unique indexes:
--   sleep_records(user_id, record_date)
--   user_settings(user_id, key)
--   ustc_news(date) UNIQUE (a btree can scan backward for ORDER BY date DESC)
-- None back a constraint.
drop index if exists public.idx_sleep_records_user;
drop index if exists public.idx_user_settings_user;
drop index if exists public.idx_ustc_news_date;

-- Cloud-synced personal data is available only to authenticated users. Wrapping
-- auth.uid() in SELECT lets Postgres evaluate it once per statement rather than
-- once per row.
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
