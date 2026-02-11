--
-- Add unique constraint to users table to prevent duplicate OAuth users
--

ALTER TABLE public.users 
ADD CONSTRAINT unique_provider UNIQUE (provider_type, provider_id);
