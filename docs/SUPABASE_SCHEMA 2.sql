-- HelpNearby PostgreSQL / Supabase Schema + Row Level Security (RLS)
-- Compatible with Supabase Free Plan (PostgreSQL 15+)
-- Idempotent script: safe to copy-paste and re-run in Supabase SQL Editor.

create extension if not exists "pgcrypto";

-- ============================================================================
-- 1. CORE TABLES
-- ============================================================================

create table if not exists public.users (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null check (char_length(full_name) between 2 and 80),
  email text not null unique,
  photo_url text,
  city text not null,
  state text not null,
  country text not null default 'India',
  language text not null default 'EN',
  interests text[] not null default '{}',
  volunteer_available boolean not null default false,
  email_verified boolean not null default false,
  role text not null default 'MEMBER' check (role in ('MEMBER', 'ADMIN')),
  is_suspended boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.user_preferences (
  user_id uuid primary key references public.users(id) on delete cascade,
  theme text not null default 'SYSTEM',
  language text not null default 'EN',
  notifications_enabled boolean not null default true,
  location_enabled boolean not null default false,
  selected_city text not null default 'Bengaluru',
  selected_area text not null default 'Indiranagar'
);

create table if not exists public.help_requests (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.users(id) on delete cascade,
  owner_name text not null default '',
  title text not null check (char_length(title) between 5 and 100),
  description text not null check (char_length(description) >= 15),
  category text not null,
  city text not null,
  state text not null,
  approximate_area text not null,
  latitude double precision,
  longitude double precision,
  urgency text not null check (urgency in ('LOW', 'MEDIUM', 'HIGH')),
  status text not null default 'OPEN',
  photo_url text,
  version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  expires_at timestamptz not null
);
create index if not exists idx_help_requests_city_status on public.help_requests(city, status);
create index if not exists idx_help_requests_owner on public.help_requests(owner_id);
create index if not exists idx_help_requests_category on public.help_requests(category);

create table if not exists public.help_offers (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.help_requests(id) on delete cascade,
  volunteer_id uuid not null references public.users(id) on delete cascade,
  volunteer_name text not null default '',
  message text not null,
  status text not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'DECLINED', 'COMPLETED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(request_id, volunteer_id)
);
create index if not exists idx_help_offers_request on public.help_offers(request_id);
create index if not exists idx_help_offers_volunteer on public.help_offers(volunteer_id);

create table if not exists public.volunteer_profiles (
  user_id uuid primary key references public.users(id) on delete cascade,
  full_name text not null default '',
  photo_url text,
  city text not null default '',
  bio text not null,
  skills text[] not null default '{}',
  interests text[] not null default '{}',
  availability boolean not null default true,
  completed_count integer not null default 0,
  verification_status text not null default 'UNVERIFIED',
  created_at timestamptz not null default now()
);

create table if not exists public.donor_profiles (
  user_id uuid primary key references public.users(id) on delete cascade,
  display_name text not null default '',
  blood_group text not null check (blood_group in ('A_POS','A_NEG','B_POS','B_NEG','AB_POS','AB_NEG','O_POS','O_NEG','A+','A-','B+','B-','AB+','AB-','O+','O-')),
  city text not null,
  state text not null,
  available boolean not null default true,
  consent boolean not null check (consent = true),
  last_donation_date text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_donor_profiles_group_city on public.donor_profiles(blood_group, city, available);

create table if not exists public.donor_contact_requests (
  id uuid primary key default gen_random_uuid(),
  donor_id uuid not null references public.users(id) on delete cascade,
  requester_id uuid not null references public.users(id) on delete cascade,
  requester_name text not null default '',
  blood_group text not null default 'O_POS',
  city text not null default '',
  message text not null,
  status text not null default 'PENDING',
  created_at timestamptz not null default now()
);
create index if not exists idx_donor_contact_requests_donor on public.donor_contact_requests(donor_id);
create index if not exists idx_donor_contact_requests_requester on public.donor_contact_requests(requester_id);

create table if not exists public.lost_found_posts (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.users(id) on delete cascade,
  owner_name text not null default '',
  type text not null check (type in ('LOST', 'FOUND')),
  title text not null,
  description text not null,
  category text not null,
  city text not null,
  state text not null,
  approximate_area text not null,
  image_url text,
  contact_preference text not null default 'In-app',
  status text not null default 'ACTIVE',
  hidden_verification_hint text not null default '',
  event_date text not null default '',
  created_at timestamptz not null default now()
);
create index if not exists idx_lost_found_posts_type_city_status on public.lost_found_posts(type, city, status);

create table if not exists public.lost_found_claims (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.lost_found_posts(id) on delete cascade,
  claimant_id uuid not null references public.users(id) on delete cascade,
  claimant_name text not null default '',
  message text not null,
  verification_answer text not null default '',
  status text not null default 'PENDING',
  created_at timestamptz not null default now()
);
create index if not exists idx_lost_found_claims_post on public.lost_found_claims(post_id);
create index if not exists idx_lost_found_claims_claimant on public.lost_found_claims(claimant_id);

create table if not exists public.community_posts (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references public.users(id) on delete cascade,
  author_name text not null default '',
  title text not null,
  content text not null,
  category text not null,
  city text not null,
  status text not null default 'ACTIVE',
  like_count integer not null default 0,
  comment_count integer not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists idx_community_posts_city_cat_status on public.community_posts(city, category, status);

create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.community_posts(id) on delete cascade,
  author_id uuid not null references public.users(id) on delete cascade,
  author_name text not null default '',
  content text not null,
  created_at timestamptz not null default now()
);
create index if not exists idx_comments_post on public.comments(post_id);

create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.help_requests(id) on delete cascade,
  request_title text not null default '',
  owner_id uuid not null references public.users(id) on delete cascade,
  volunteer_id uuid not null references public.users(id) on delete cascade,
  owner_name text not null default '',
  volunteer_name text not null default '',
  participant_ids uuid[] not null,
  status text not null default 'OPEN',
  last_message_text text not null default '',
  last_message_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  unique(request_id, volunteer_id)
);
create index if not exists idx_conversations_owner on public.conversations(owner_id);
create index if not exists idx_conversations_volunteer on public.conversations(volunteer_id);

create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references public.users(id) on delete cascade,
  text text not null,
  created_at timestamptz not null default now(),
  read_at timestamptz
);
create index if not exists idx_messages_conversation_created on public.messages(conversation_id, created_at);

create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id) on delete cascade,
  type text not null,
  title text not null,
  body text not null,
  related_id text,
  read_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists idx_notifications_user_created on public.notifications(user_id, created_at);

create table if not exists public.user_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.users(id) on delete cascade,
  target_type text not null,
  target_id text not null,
  reason text not null,
  description text not null default '',
  status text not null default 'PENDING',
  created_at timestamptz not null default now()
);
create index if not exists idx_user_reports_status_created on public.user_reports(status, created_at);

create table if not exists public.blocked_users (
  id uuid primary key default gen_random_uuid(),
  blocker_id uuid not null references public.users(id) on delete cascade,
  blocked_user_id uuid not null references public.users(id) on delete cascade,
  blocked_user_name text not null default '',
  created_at timestamptz not null default now(),
  unique(blocker_id, blocked_user_id)
);

create table if not exists public.admin_audit_logs (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid not null references public.users(id),
  action text not null,
  target_id text not null,
  details text not null default '',
  created_at timestamptz not null default now()
);
create index if not exists idx_admin_audit_logs_created on public.admin_audit_logs(created_at);

-- ============================================================================
-- 2. ENABLE ROW LEVEL SECURITY (RLS) ON EVERY TABLE
-- ============================================================================

alter table public.users enable row level security;
alter table public.user_preferences enable row level security;
alter table public.help_requests enable row level security;
alter table public.help_offers enable row level security;
alter table public.volunteer_profiles enable row level security;
alter table public.donor_profiles enable row level security;
alter table public.donor_contact_requests enable row level security;
alter table public.lost_found_posts enable row level security;
alter table public.lost_found_claims enable row level security;
alter table public.community_posts enable row level security;
alter table public.comments enable row level security;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;
alter table public.notifications enable row level security;
alter table public.user_reports enable row level security;
alter table public.blocked_users enable row level security;
alter table public.admin_audit_logs enable row level security;

-- ============================================================================
-- 3. ZERO-TRUST RLS POLICIES
-- ============================================================================

-- users: public safe read for authenticated members; self-insert as MEMBER only; no role escalation on update
drop policy if exists "users_read_public_safe" on public.users;
create policy "users_read_public_safe" on public.users
  for select to authenticated using (true);

drop policy if exists "users_insert_self_member" on public.users;
create policy "users_insert_self_member" on public.users
  for insert to authenticated
  with check (auth.uid() = id and role = 'MEMBER' and is_suspended = false);

drop policy if exists "users_update_self_no_role_escalation" on public.users;
create policy "users_update_self_no_role_escalation" on public.users
  for update to authenticated
  using (auth.uid() = id)
  with check (auth.uid() = id and role = (select role from public.users where id = auth.uid()));

drop policy if exists "users_delete_self" on public.users;
create policy "users_delete_self" on public.users
  for delete to authenticated
  using (auth.uid() = id);

-- user_preferences: strict owner-only access
drop policy if exists "prefs_owner_only" on public.user_preferences;
create policy "prefs_owner_only" on public.user_preferences
  for all to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- help_requests: hide UNDER_REVIEW from non-owners; only owner can insert/update/delete
drop policy if exists "requests_read_active" on public.help_requests;
create policy "requests_read_active" on public.help_requests
  for select to anon, authenticated
  using (status <> 'UNDER_REVIEW' or owner_id = auth.uid());

drop policy if exists "requests_insert_owner" on public.help_requests;
create policy "requests_insert_owner" on public.help_requests
  for insert to authenticated
  with check (auth.uid() = owner_id);

drop policy if exists "requests_update_delete_owner" on public.help_requests;
create policy "requests_update_delete_owner" on public.help_requests
  for update to authenticated
  using (auth.uid() = owner_id)
  with check (auth.uid() = owner_id);

drop policy if exists "requests_delete_owner" on public.help_requests;
create policy "requests_delete_owner" on public.help_requests
  for delete to authenticated
  using (auth.uid() = owner_id);

-- help_offers: readable/updatable only by the volunteer or the request owner
drop policy if exists "offers_read_participants" on public.help_offers;
create policy "offers_read_participants" on public.help_offers
  for select to authenticated
  using (
    volunteer_id = auth.uid()
    or exists (select 1 from public.help_requests r where r.id = request_id and r.owner_id = auth.uid())
  );

drop policy if exists "offers_insert_volunteer" on public.help_offers;
create policy "offers_insert_volunteer" on public.help_offers
  for insert to authenticated
  with check (volunteer_id = auth.uid());

drop policy if exists "offers_update_participants" on public.help_offers;
create policy "offers_update_participants" on public.help_offers
  for update to authenticated
  using (
    volunteer_id = auth.uid()
    or exists (select 1 from public.help_requests r where r.id = request_id and r.owner_id = auth.uid())
  )
  with check (
    volunteer_id = auth.uid()
    or exists (select 1 from public.help_requests r where r.id = request_id and r.owner_id = auth.uid())
  );

-- volunteer_profiles: readable by guests & authenticated users; users cannot self-verify
drop policy if exists "volunteers_read_all" on public.volunteer_profiles;
create policy "volunteers_read_all" on public.volunteer_profiles
  for select to anon, authenticated using (true);

drop policy if exists "volunteers_insert_self_unverified" on public.volunteer_profiles;
create policy "volunteers_insert_self_unverified" on public.volunteer_profiles
  for insert to authenticated
  with check (auth.uid() = user_id and verification_status = 'UNVERIFIED');

drop policy if exists "volunteers_update_self_no_verify_escalation" on public.volunteer_profiles;
create policy "volunteers_update_self_no_verify_escalation" on public.volunteer_profiles
  for update to authenticated
  using (auth.uid() = user_id)
  with check (
    auth.uid() = user_id
    and verification_status = (select verification_status from public.volunteer_profiles where user_id = auth.uid())
  );

-- donor_profiles: only consented & available donors are visible (plus own record)
drop policy if exists "donors_read_consented" on public.donor_profiles;
create policy "donors_read_consented" on public.donor_profiles
  for select to anon, authenticated
  using ((consent = true and available = true) or auth.uid() = user_id);

drop policy if exists "donors_manage_own" on public.donor_profiles;
create policy "donors_manage_own" on public.donor_profiles
  for all to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- donor_contact_requests: visible and manageable only by requester or donor
drop policy if exists "donor_requests_participants" on public.donor_contact_requests;
create policy "donor_requests_participants" on public.donor_contact_requests
  for all to authenticated
  using (auth.uid() = donor_id or auth.uid() = requester_id)
  with check (auth.uid() = requester_id or auth.uid() = donor_id);

-- lost_found_posts: hide UNDER_REVIEW from non-owners; owner controls writes
drop policy if exists "lost_found_read_active" on public.lost_found_posts;
create policy "lost_found_read_active" on public.lost_found_posts
  for select to anon, authenticated
  using (status <> 'UNDER_REVIEW' or owner_id = auth.uid());

drop policy if exists "lost_found_manage_owner" on public.lost_found_posts;
create policy "lost_found_manage_owner" on public.lost_found_posts
  for all to authenticated
  using (auth.uid() = owner_id)
  with check (auth.uid() = owner_id);

-- lost_found_claims: only claimant or post owner can read/respond
drop policy if exists "lost_found_claims_participants" on public.lost_found_claims;
create policy "lost_found_claims_participants" on public.lost_found_claims
  for all to authenticated
  using (
    claimant_id = auth.uid()
    or exists (select 1 from public.lost_found_posts p where p.id = post_id and p.owner_id = auth.uid())
  )
  with check (
    claimant_id = auth.uid()
    or exists (select 1 from public.lost_found_posts p where p.id = post_id and p.owner_id = auth.uid())
  );

-- community_posts: active posts public; author manages own posts
drop policy if exists "community_posts_read_active" on public.community_posts;
create policy "community_posts_read_active" on public.community_posts
  for select to anon, authenticated
  using (status = 'ACTIVE' or author_id = auth.uid());

drop policy if exists "community_posts_manage_author" on public.community_posts;
create policy "community_posts_manage_author" on public.community_posts
  for all to authenticated
  using (auth.uid() = author_id)
  with check (auth.uid() = author_id);

-- comments: public read; author manages own comments
drop policy if exists "comments_read_all" on public.comments;
create policy "comments_read_all" on public.comments
  for select to anon, authenticated using (true);

drop policy if exists "comments_manage_author" on public.comments;
create policy "comments_manage_author" on public.comments
  for all to authenticated
  using (auth.uid() = author_id)
  with check (auth.uid() = author_id);

-- conversations: strict participant-only access
drop policy if exists "conversations_participant_only" on public.conversations;
create policy "conversations_participant_only" on public.conversations
  for all to authenticated
  using (auth.uid() = any(participant_ids))
  with check (auth.uid() = any(participant_ids));

-- messages: strict conversation participant + sender identity enforcement
drop policy if exists "messages_participant_only" on public.messages;
create policy "messages_participant_only" on public.messages
  for all to authenticated
  using (exists (
    select 1 from public.conversations c
    where c.id = conversation_id and auth.uid() = any(c.participant_ids)
  ))
  with check (
    sender_id = auth.uid()
    and exists (
      select 1 from public.conversations c
      where c.id = conversation_id and auth.uid() = any(c.participant_ids)
    )
  );

-- notifications: strict recipient-only access
drop policy if exists "notifications_owner_only" on public.notifications;
create policy "notifications_owner_only" on public.notifications
  for all to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- blocked_users: strict blocker-only access
drop policy if exists "blocked_owner_only" on public.blocked_users;
create policy "blocked_owner_only" on public.blocked_users
  for all to authenticated
  using (auth.uid() = blocker_id)
  with check (auth.uid() = blocker_id);

-- user_reports: reporter can submit & read their own reports; admins moderate all reports
drop policy if exists "reports_insert_authenticated" on public.user_reports;
create policy "reports_insert_authenticated" on public.user_reports
  for insert to authenticated
  with check (auth.uid() = reporter_id);

drop policy if exists "reports_read_own" on public.user_reports;
create policy "reports_read_own" on public.user_reports
  for select to authenticated
  using (auth.uid() = reporter_id);

-- Admin moderation restricted strictly to server-verified JWT claim `app_metadata.role = 'admin'`
drop policy if exists "admin_reports_read_write" on public.user_reports;
create policy "admin_reports_read_write" on public.user_reports
  for all to authenticated
  using ((auth.jwt() -> 'app_metadata' ->> 'role') = 'admin')
  with check ((auth.jwt() -> 'app_metadata' ->> 'role') = 'admin');

drop policy if exists "admin_audit_logs_only" on public.admin_audit_logs;
create policy "admin_audit_logs_only" on public.admin_audit_logs
  for all to authenticated
  using ((auth.jwt() -> 'app_metadata' ->> 'role') = 'admin')
  with check ((auth.jwt() -> 'app_metadata' ->> 'role') = 'admin');

-- ============================================================================
-- 4. SUPABASE REALTIME PUBLICATION
-- ============================================================================

do $$
declare
  tbl text;
begin
  foreach tbl in array array['help_requests', 'help_offers', 'messages', 'notifications'] loop
    if not exists (
      select 1 from pg_publication_tables
      where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = tbl
    ) then
      execute format('alter publication supabase_realtime add table public.%I', tbl);
    end if;
  end loop;
end $$;
