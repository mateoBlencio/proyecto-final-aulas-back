-- Allocation goes to allocationSize = 50 like Occurrence and AcademicEvent (V2): confirming a
-- preview or syncing allocations inserts thousands of rows and paid one nextval per row.
ALTER SEQUENCE public.asignacion_aula_id_asignacion_seq INCREMENT BY 50;
