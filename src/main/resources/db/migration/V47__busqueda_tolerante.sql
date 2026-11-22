CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;

-- Búsqueda tolerante de un término sobre un texto: ignora mayúsculas y acentos, acepta el término como
-- subcadena y, si no, como palabra a distancia de edición <= 1 (4-5 caracteres) o <= 2 (6 o más).
CREATE OR REPLACE FUNCTION public.siga_busqueda_coincide(p_texto text, p_termino text) RETURNS boolean
    LANGUAGE sql STABLE
AS $$
    WITH t AS (
        SELECT public.unaccent(lower(left(p_termino, 50))) AS termino,
               public.unaccent(lower(coalesce(p_texto, ''))) AS texto
    ), d AS (
        SELECT termino, texto,
               CASE WHEN length(termino) >= 6 THEN 2 WHEN length(termino) >= 4 THEN 1 ELSE 0 END AS maxima
        FROM t
    )
    SELECT texto LIKE '%' || replace(replace(replace(termino, '\', '\\'), '%', '\%'), '_', '\_') || '%'
           OR (maxima > 0 AND EXISTS (
               SELECT 1 FROM regexp_split_to_table(texto, '[^a-z0-9]+') AS palabra
               WHERE palabra <> '' AND length(palabra) <= 100
                 AND levenshtein_less_equal(palabra, termino, maxima) <= maxima))
    FROM d
$$;
