-- Add workout duration and the timestamps required by sleep-record LWW sync.
alter table public.sleep_records
  add column if not exists workout_time text;
alter table public.sleep_records
  add column if not exists workout_duration_minutes integer;
alter table public.sleep_records
  add column if not exists note text;
alter table public.sleep_records
  add column if not exists updated_at timestamptz;

update public.sleep_records
set updated_at = now()
where updated_at is null;

alter table public.sleep_records
  alter column updated_at set default now();
alter table public.sleep_records
  alter column updated_at set not null;

-- Older clients could create multiple UUID rows for one logical sleep/workout
-- day. Merge all non-null fields into the newest row before adding the natural
-- key used by PostgREST upserts.
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

create unique index if not exists idx_sleep_records_user_record_date_unique
  on public.sleep_records(user_id, record_date);

-- Reject stale user-setting writes at the database boundary. The closing
-- boundary is monotonic, so the greater valid ISO date wins despite clock skew.
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
