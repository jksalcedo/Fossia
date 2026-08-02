-- Update delete_account() to delete from auth.users instead of public.profiles.
-- full cascade

CREATE OR REPLACE FUNCTION public.delete_account() RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = public
    AS $$
BEGIN
    DELETE FROM auth.users WHERE id = auth.uid();
END;
$$;
