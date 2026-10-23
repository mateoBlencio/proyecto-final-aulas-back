DROP FUNCTION IF EXISTS public.siga_busqueda_coincide(text, text);

-- Relevancia de un término sobre un texto, sin mayúsculas ni acentos. 0 = no coincide;
-- 4 = palabra completa; 3 = prefijo de palabra; 2 = subcadena; 1 = palabra a distancia de edición <= 1 (4-5
-- letras) o <= 2 (6 o más).
-- Términos con algún dígito o de hasta 3 caracteres: solo palabra completa, o prefijo de una palabra de 2 o
-- más letras si el término es todo letras. Subcadena y distancia de edición aplican solo a términos de 4 o más
-- letras, y la distancia solo contra palabras de letras (no cruza códigos parecidos como 1K1 y 2K1).
CREATE OR REPLACE FUNCTION public.siga_busqueda_rango(p_texto text, p_termino text) RETURNS integer
    LANGUAGE sql STABLE
AS $$
    WITH t AS (
        SELECT public.unaccent(lower(left(p_termino, 50))) AS termino,
               public.unaccent(lower(coalesce(p_texto, ''))) AS texto
    ), d AS (
        SELECT termino, texto,
               (termino ~ '[0-9]' OR length(termino) <= 3) AS estricto,
               CASE WHEN termino ~ '[0-9]' OR length(termino) <= 3 THEN 0
                    WHEN length(termino) >= 6 THEN 2 ELSE 1 END AS maxima
        FROM t
    )
    SELECT CASE WHEN termino = '' THEN 0 ELSE greatest(
        coalesce((
            SELECT max(CASE
                WHEN palabra = termino THEN 4
                WHEN termino !~ '[0-9]' AND left(palabra, length(termino)) = termino
                     AND (NOT estricto OR palabra ~ '^[a-z]{2,}$') THEN 3
                WHEN maxima > 0 AND palabra ~ '^[a-z]+$'
                     AND levenshtein_less_equal(left(palabra, 100), termino, maxima) <= maxima THEN 1
                ELSE 0 END)
            FROM regexp_split_to_table(texto, '[^a-z0-9]+') AS palabra
            WHERE palabra <> ''), 0),
        CASE WHEN NOT estricto AND position(termino IN texto) > 0 THEN 2 ELSE 0 END) END
    FROM d
$$;
