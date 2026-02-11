--
-- Fix unique constraint to allow multiple inactive users with empty provider_id
-- Replace constraint with partial unique index
--

-- Remove the old constraint
ALTER TABLE public.users DROP CONSTRAINT IF EXISTS unique_provider;

-- Create partial unique index (only for ACTIVE users with non-empty provider_id)
CREATE UNIQUE INDEX IF NOT EXISTS unique_active_provider 
ON public.users (provider_type, provider_id) 
WHERE status = 'ACTIVE' AND provider_id != '';
