-- Fix FK ON DELETE rules for all user-referencing tables.

BEGIN;

-- user_submissions

ALTER TABLE public.user_submissions
    DROP CONSTRAINT IF EXISTS fk_submissions_profiles;

ALTER TABLE public.user_submissions
    ADD CONSTRAINT fk_submissions_profiles
    FOREIGN KEY (submitter_id) REFERENCES public.profiles(id) ON DELETE SET NULL;

ALTER TABLE public.user_submissions
    DROP CONSTRAINT IF EXISTS user_submissions_last_edited_by_fkey;

ALTER TABLE public.user_submissions
    ADD CONSTRAINT user_submissions_last_edited_by_fkey
    FOREIGN KEY (last_edited_by) REFERENCES public.profiles(id) ON DELETE SET NULL;

-- user_linking_submissions

ALTER TABLE public.user_linking_submissions
    DROP CONSTRAINT IF EXISTS user_linking_submissions_submitter_id_fkey;

ALTER TABLE public.user_linking_submissions
    ADD CONSTRAINT user_linking_submissions_submitter_id_fkey
    FOREIGN KEY (submitter_id) REFERENCES public.profiles(id) ON DELETE SET NULL;

ALTER TABLE public.user_linking_submissions
    DROP CONSTRAINT IF EXISTS user_linking_submissions_last_edited_by_fkey;

ALTER TABLE public.user_linking_submissions
    ADD CONSTRAINT user_linking_submissions_last_edited_by_fkey
    FOREIGN KEY (last_edited_by) REFERENCES public.profiles(id) ON DELETE SET NULL;

--  user_reports
ALTER TABLE public.user_reports
    DROP CONSTRAINT IF EXISTS user_reports_submitter_id_fkey;

-- submitter_id is NOT NULL, so we must first allow NULL before SET NULL can work
ALTER TABLE public.user_reports
    ALTER COLUMN submitter_id DROP NOT NULL;

ALTER TABLE public.user_reports
    ADD CONSTRAINT user_reports_submitter_id_fkey
    FOREIGN KEY (submitter_id) REFERENCES public.profiles(id) ON DELETE SET NULL;

-- signing_key_votes

ALTER TABLE public.signing_key_votes
    DROP CONSTRAINT IF EXISTS signing_key_votes_submitter_id_fkey;

ALTER TABLE public.signing_key_votes
    ADD CONSTRAINT signing_key_votes_submitter_id_fkey
    FOREIGN KEY (submitter_id) REFERENCES public.profiles(id) ON DELETE SET NULL;

-- comments
-- comments.user_id has TWO FK constraints (auth.users + profiles).
-- Both need SET NULL; user_id must allow NULL first.

ALTER TABLE public.comments
    DROP CONSTRAINT IF EXISTS comments_user_id_fkey;

ALTER TABLE public.comments
    DROP CONSTRAINT IF EXISTS comments_profile_id_fkey;

ALTER TABLE public.comments
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE public.comments
    ADD CONSTRAINT comments_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

ALTER TABLE public.comments
    ADD CONSTRAINT comments_profile_id_fkey
    FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE SET NULL;

-- app_corrections
ALTER TABLE public.app_corrections
    DROP CONSTRAINT IF EXISTS app_corrections_user_id_fkey;

ALTER TABLE public.app_corrections
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE public.app_corrections
    ADD CONSTRAINT app_corrections_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

-- app_reports

ALTER TABLE public.app_reports
    DROP CONSTRAINT IF EXISTS app_reports_user_id_fkey;

ALTER TABLE public.app_reports
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE public.app_reports
    ADD CONSTRAINT app_reports_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

-- admin_users

ALTER TABLE public.admin_users
    DROP CONSTRAINT IF EXISTS admin_users_created_by_fkey;

ALTER TABLE public.admin_users
    ADD CONSTRAINT admin_users_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- submission_votes (personal activity -> CASCADE)

ALTER TABLE public.submission_votes
    DROP CONSTRAINT IF EXISTS submission_votes_user_id_fkey;

ALTER TABLE public.submission_votes
    ADD CONSTRAINT submission_votes_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- app_scan_stats

ALTER TABLE public.app_scan_stats
    DROP CONSTRAINT IF EXISTS app_scan_stats_user_id_fkey;

ALTER TABLE public.app_scan_stats
    ADD CONSTRAINT app_scan_stats_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

COMMIT;
