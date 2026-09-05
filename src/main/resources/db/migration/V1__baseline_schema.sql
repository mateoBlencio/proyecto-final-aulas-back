--
-- Baseline del schema. Es una foto de la base de dev tomada el 04/09/2026, despues de
-- emparejar dev y test a mano (ver plans/migracion-db-dev-a-test-2026-09-04/).
--
-- En dev y test NO se ejecuta: esas bases ya tienen estas 38 tablas, asi que Flyway las
-- marca como baseline y arranca a aplicar desde la V2. Solo corre de verdad sobre una base
-- vacia: los tests de integracion y cualquier ambiente nuevo.
--
-- No incluye GRANTs a proposito: los roles de la app se llaman distinto en cada ambiente
-- (back_user_dev, back_user_test) y son parte del provisioning, no del schema.
--



--
-- Name: reset_asignaciones(boolean); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reset_asignaciones(p_limpiar_auditoria boolean DEFAULT true) RETURNS TABLE(asignaciones_borradas bigint, ocurrencias_revertidas bigint)
    LANGUAGE plpgsql SECURITY DEFINER
    AS $$
DECLARE
    v_asignaciones bigint;
    v_ocurrencias bigint;
BEGIN
    SELECT count(*) INTO v_asignaciones
    FROM asignacion_aula;

    TRUNCATE TABLE asignacion_aula RESTART IDENTITY;

    UPDATE ocurrencia
    SET estado = 'SCHEDULED'
    WHERE estado = 'ASSIGNED';

    GET DIAGNOSTICS v_ocurrencias = ROW_COUNT;

    IF p_limpiar_auditoria THEN
        TRUNCATE TABLE asignacion_aula_aud;
    END IF;

    RETURN QUERY
    SELECT v_asignaciones, v_ocurrencias;
END;
$$;


--
-- Name: reset_eventos(boolean); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reset_eventos(p_limpiar_auditoria boolean DEFAULT true) RETURNS TABLE(eventos_borrados bigint, ocurrencias_borradas bigint, asignaciones_borradas bigint)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$                                                                                                                                                                                                                                  
  DECLARE                                                                                                                                                                                                                                
      v_eventos      bigint;                                                                                                                                                                                                             
      v_ocurrencias  bigint;                                                                                                                                                                                                             
      v_asignaciones bigint;                                                                                                                                                                                                             
  BEGIN                                                                                                                                                                                                                                  
      SELECT count(*) INTO v_eventos      FROM evento_academico;                                                                                                                                                                         
      SELECT count(*) INTO v_ocurrencias  FROM ocurrencia;                                                                                                                                                                               
      SELECT count(*) INTO v_asignaciones FROM asignacion_aula;                                                                                                                                                                          
                                                                                                                                                                                                                                         
      TRUNCATE TABLE                                                                                                                                                                                                                     
          evento_academico,                                                                                                                                                                                                              
          evento_recurrente,                                                                                                                                                                                                             
          evento_unico_academico,                                                                                                                                                                                                        
          evento_recurrente_fecha_excluida,                                                                                                                                                                                              
          ocurrencia,                                                                                                                                                                                                                    
          asignacion_aula                                                                                                                                                                                                                
      RESTART IDENTITY CASCADE;                                                                                                                                                                                                          
                                                                                                                                                                                                                                         
      IF p_limpiar_auditoria THEN                                                                                                                                                                                                        
          TRUNCATE TABLE                                                                                                                                                                                                                 
              evento_academico_aud,                                                                                                                                                                                                      
              evento_recurrente_aud,                                                                                                                                                                                                     
              evento_unico_academico_aud,                                                                                                                                                                                                
              ocurrencia_aud,                                                                                                                                                                                                            
              asignacion_aula_aud;                                                                                                                                                                                                       
      END IF;                                                                                                                                                                                                                            
                                                                                                                                                                                                                                         
      RETURN QUERY SELECT v_eventos, v_ocurrencias, v_asignaciones;                                                                                                                                                                      
  END;                                                                                                                                                                                                                                   
  $$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: asignacion_aula; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asignacion_aula (
    id_asignacion bigint NOT NULL,
    id_aula bigint NOT NULL,
    fecha_creacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    observaciones character varying(255),
    origen character varying(255) DEFAULT 'MANUAL'::character varying NOT NULL,
    id_ocurrencia bigint NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_asignacion_source CHECK (((origen)::text = ANY (ARRAY[('MANUAL'::character varying)::text, ('AUTOMATIC'::character varying)::text, ('IMPORTED'::character varying)::text, ('SYSACAD'::character varying)::text])))
);


--
-- Name: asignacion_aula_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asignacion_aula_aud (
    id_asignacion bigint NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    id_ocurrencia bigint,
    id_aula bigint,
    origen character varying(255),
    fecha_creacion timestamp(6) without time zone,
    observaciones character varying(255),
    CONSTRAINT asignacion_aula_aud_origen_check CHECK (((origen)::text = ANY (ARRAY[('MANUAL'::character varying)::text, ('AUTOMATIC'::character varying)::text, ('IMPORTED'::character varying)::text, ('SYSACAD'::character varying)::text])))
);


--
-- Name: asignacion_aula_id_asignacion_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.asignacion_aula_id_asignacion_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asignacion_aula_id_asignacion_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.asignacion_aula_id_asignacion_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asignacion_aula_id_asignacion_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.asignacion_aula_id_asignacion_seq1 OWNED BY public.asignacion_aula.id_asignacion;


--
-- Name: aula; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.aula (
    id_aula bigint NOT NULL,
    id_edificio bigint NOT NULL,
    id_tipo_aula bigint NOT NULL,
    capacidad integer NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sincronizado_en timestamp without time zone,
    hash_sysacad character varying(64),
    version bigint DEFAULT 0 NOT NULL,
    habilitada_sysacad boolean DEFAULT false NOT NULL,
    numero integer NOT NULL,
    eliminado_en timestamp without time zone,
    observaciones character varying(500),
    modo_permiso character varying(20) DEFAULT 'ALL'::character varying NOT NULL,
    CONSTRAINT chk_aula_capacidad CHECK ((capacidad >= 0))
);


--
-- Name: aula_id_aula_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.aula_id_aula_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: aula_id_aula_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.aula_id_aula_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: aula_id_aula_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.aula_id_aula_seq1 OWNED BY public.aula.id_aula;


--
-- Name: aula_permiso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.aula_permiso (
    id_aula_permiso bigint NOT NULL,
    id_aula bigint NOT NULL,
    tipo_objetivo character varying(20) NOT NULL,
    id_objetivo bigint NOT NULL,
    eliminado_en timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    actualizado_en timestamp without time zone NOT NULL
);


--
-- Name: aula_permiso_id_aula_permiso_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.aula_permiso_id_aula_permiso_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: aula_permiso_id_aula_permiso_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.aula_permiso_id_aula_permiso_seq OWNED BY public.aula_permiso.id_aula_permiso;


--
-- Name: aula_recurso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.aula_recurso (
    id_aula_recurso bigint NOT NULL,
    id_aula bigint NOT NULL,
    id_tipo_recurso bigint NOT NULL,
    cantidad integer NOT NULL,
    eliminado_en timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    actualizado_en timestamp without time zone NOT NULL
);


--
-- Name: aula_recurso_id_aula_recurso_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.aula_recurso_id_aula_recurso_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: aula_recurso_id_aula_recurso_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.aula_recurso_id_aula_recurso_seq OWNED BY public.aula_recurso.id_aula_recurso;


--
-- Name: comision; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comision (
    id_comision bigint NOT NULL,
    codigo_curso character varying(255) NOT NULL,
    id_periodo_academico bigint NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sincronizado_en timestamp without time zone,
    hash_sysacad character varying(64),
    version bigint DEFAULT 0 NOT NULL,
    habilitado_sysacad boolean DEFAULT false NOT NULL,
    eliminado_en timestamp without time zone
);


--
-- Name: comision_id_comision_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.comision_id_comision_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: comision_id_comision_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.comision_id_comision_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: comision_id_comision_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.comision_id_comision_seq1 OWNED BY public.comision.id_comision;


--
-- Name: configuracion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.configuracion (
    clave character varying(255) NOT NULL,
    valor character varying(255) NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: configuracion_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.configuracion_aud (
    clave character varying(255) NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    valor character varying(255)
);


--
-- Name: edificio; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edificio (
    id_edificio bigint NOT NULL,
    nombre character varying(100) NOT NULL,
    codigo_edificio integer,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sincronizado_en timestamp without time zone,
    hash_sysacad character varying(64),
    version bigint DEFAULT 0 NOT NULL,
    eliminado_en timestamp without time zone
);


--
-- Name: edificio_id_edificio_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.edificio_id_edificio_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: edificio_id_edificio_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.edificio_id_edificio_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: edificio_id_edificio_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.edificio_id_edificio_seq1 OWNED BY public.edificio.id_edificio;


--
-- Name: especialidad; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.especialidad (
    id_especialidad bigint NOT NULL,
    codigo_especialidad integer NOT NULL,
    nombre character varying(255) NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sincronizado_en timestamp without time zone,
    hash_sysacad character varying(64),
    version bigint DEFAULT 0 NOT NULL,
    abreviatura character varying(255)
);


--
-- Name: especialidad_id_especialidad_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.especialidad_id_especialidad_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: especialidad_id_especialidad_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.especialidad_id_especialidad_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: especialidad_id_especialidad_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.especialidad_id_especialidad_seq1 OWNED BY public.especialidad.id_especialidad;


--
-- Name: event_publication; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.event_publication (
    id uuid NOT NULL,
    listener_id character varying(255) NOT NULL,
    event_type character varying(255) NOT NULL,
    serialized_event character varying(255) NOT NULL,
    publication_date timestamp with time zone NOT NULL,
    completion_date timestamp with time zone,
    completion_attempts integer NOT NULL,
    last_resubmission_date timestamp(6) with time zone,
    status character varying(255),
    CONSTRAINT event_publication_status_check CHECK (((status)::text = ANY (ARRAY[('PUBLISHED'::character varying)::text, ('PROCESSING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text, ('RESUBMITTED'::character varying)::text])))
);


--
-- Name: evento_academico; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_academico (
    id_evento_academico bigint NOT NULL,
    cantidad_inscriptos integer NOT NULL,
    hora_inicio time without time zone NOT NULL,
    duracion_minutos integer NOT NULL,
    tipo_evento character varying(31) NOT NULL,
    eliminado boolean DEFAULT false NOT NULL,
    id_materia bigint,
    id_comision bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sincronizado_en timestamp without time zone,
    hash_sysacad character varying(64),
    habilitado_sysacad boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_evento_academico_duration CHECK ((duracion_minutos >= 1)),
    CONSTRAINT chk_evento_academico_enrolled CHECK ((cantidad_inscriptos >= 0)),
    CONSTRAINT chk_evento_academico_tipo CHECK (((tipo_evento)::text = ANY (ARRAY[('RECURRING'::character varying)::text, ('UNIQUE_EVENT'::character varying)::text])))
);


--
-- Name: evento_academico_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_academico_aud (
    id_evento_academico bigint NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    tipo_evento character varying(31) NOT NULL,
    cantidad_inscriptos integer,
    hora_inicio time(0) without time zone,
    duracion_minutos integer,
    id_materia bigint,
    id_comision bigint
);


--
-- Name: evento_academico_id_evento_academico_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.evento_academico_id_evento_academico_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: evento_academico_id_evento_academico_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.evento_academico_id_evento_academico_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: evento_academico_id_evento_academico_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.evento_academico_id_evento_academico_seq1 OWNED BY public.evento_academico.id_evento_academico;


--
-- Name: evento_recurrente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_recurrente (
    id_evento_academico bigint NOT NULL,
    dia_semana character varying(255) NOT NULL,
    fecha_inicio date NOT NULL,
    fecha_fin date,
    CONSTRAINT chk_evento_recurrente_day CHECK (((dia_semana)::text = ANY (ARRAY[('MONDAY'::character varying)::text, ('TUESDAY'::character varying)::text, ('WEDNESDAY'::character varying)::text, ('THURSDAY'::character varying)::text, ('FRIDAY'::character varying)::text, ('SATURDAY'::character varying)::text, ('SUNDAY'::character varying)::text]))),
    CONSTRAINT chk_evento_recurrente_fechas CHECK (((fecha_fin IS NULL) OR (fecha_fin >= fecha_inicio)))
);


--
-- Name: evento_recurrente_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_recurrente_aud (
    id_evento_academico bigint NOT NULL,
    rev integer NOT NULL,
    dia_semana character varying(255),
    fecha_inicio date,
    fecha_fin date,
    CONSTRAINT evento_recurrente_aud_dia_semana_check CHECK (((dia_semana)::text = ANY (ARRAY[('MONDAY'::character varying)::text, ('TUESDAY'::character varying)::text, ('WEDNESDAY'::character varying)::text, ('THURSDAY'::character varying)::text, ('FRIDAY'::character varying)::text, ('SATURDAY'::character varying)::text, ('SUNDAY'::character varying)::text])))
);


--
-- Name: evento_unico; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_unico (
    id_evento_academico bigint NOT NULL,
    fecha date NOT NULL,
    descripcion character varying(255),
    tipo_actividad character varying(255) NOT NULL,
    CONSTRAINT chk_evento_unico_tipo_actividad CHECK (((tipo_actividad)::text = ANY (ARRAY[('PARCIAL'::character varying)::text, ('TRABAJO_PRACTICO'::character varying)::text, ('EXAMEN_FINAL'::character varying)::text, ('OTRO'::character varying)::text])))
);


--
-- Name: evento_unico_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento_unico_aud (
    id_evento_academico bigint NOT NULL,
    rev integer NOT NULL,
    fecha date,
    descripcion character varying(255),
    tipo_actividad character varying(255)
);


--
-- Name: materia; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.materia (
    id_materia bigint NOT NULL,
    codigo_materia integer NOT NULL,
    nombre character varying(255) NOT NULL,
    id_plan bigint NOT NULL,
    dictado character varying(255),
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    eliminado_en timestamp without time zone,
    sincronizado_en timestamp without time zone,
    hash_sysacad character varying(64),
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: materia_comision; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.materia_comision (
    id_materia bigint NOT NULL,
    id_comision bigint NOT NULL,
    cantidad_inscriptos integer DEFAULT 0 NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    es_presencial boolean DEFAULT true NOT NULL,
    eliminado_en timestamp without time zone,
    CONSTRAINT chk_mc_cantidad_inscriptos CHECK ((cantidad_inscriptos >= 0))
);


--
-- Name: materia_comision_id_materia_comision_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.materia_comision_id_materia_comision_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: materia_id_materia_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.materia_id_materia_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: materia_id_materia_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.materia_id_materia_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: materia_id_materia_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.materia_id_materia_seq1 OWNED BY public.materia.id_materia;


--
-- Name: ocurrencia; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ocurrencia (
    id_ocurrencia bigint NOT NULL,
    id_evento_academico bigint NOT NULL,
    fecha date NOT NULL,
    estado character varying(255) DEFAULT 'NEEDS_ROOM'::character varying NOT NULL,
    eliminado boolean DEFAULT false NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_ocurrencia_status CHECK (((estado)::text = ANY (ARRAY[('NEEDS_ROOM'::character varying)::text, ('ROOM_RELEASED'::character varying)::text])))
);


--
-- Name: ocurrencia_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ocurrencia_aud (
    id_ocurrencia bigint NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    id_evento_academico bigint,
    fecha date,
    estado character varying(255),
    CONSTRAINT ocurrencia_aud_estado_check CHECK (((estado)::text = ANY (ARRAY[('NEEDS_ROOM'::character varying)::text, ('ROOM_RELEASED'::character varying)::text])))
);


--
-- Name: ocurrencia_id_ocurrencia_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ocurrencia_id_ocurrencia_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ocurrencia_id_ocurrencia_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ocurrencia_id_ocurrencia_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ocurrencia_id_ocurrencia_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ocurrencia_id_ocurrencia_seq1 OWNED BY public.ocurrencia.id_ocurrencia;


--
-- Name: periodo_academico; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.periodo_academico (
    id_periodo bigint NOT NULL,
    anio integer NOT NULL,
    cuatrimestre integer NOT NULL,
    fecha_inicio date,
    fecha_fin date,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    eliminado_en timestamp without time zone,
    CONSTRAINT chk_periodo_cuatrimestre CHECK ((cuatrimestre = ANY (ARRAY[0, 1, 2]))),
    CONSTRAINT chk_periodo_fechas CHECK (((fecha_fin IS NULL) OR (fecha_inicio IS NULL) OR (fecha_fin >= fecha_inicio)))
);


--
-- Name: periodo_academico_id_periodo_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.periodo_academico_id_periodo_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: periodo_academico_id_periodo_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.periodo_academico_id_periodo_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: periodo_academico_id_periodo_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.periodo_academico_id_periodo_seq1 OWNED BY public.periodo_academico.id_periodo;


--
-- Name: plan_estudio; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.plan_estudio (
    id_plan bigint NOT NULL,
    codigo_plan integer NOT NULL,
    id_especialidad bigint NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    eliminado_en timestamp without time zone,
    sincronizado_en timestamp without time zone,
    hash_sysacad character varying(64),
    version bigint DEFAULT 0 NOT NULL
);


--
-- Name: plan_estudio_id_plan_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.plan_estudio_id_plan_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: plan_estudio_id_plan_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.plan_estudio_id_plan_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: plan_estudio_id_plan_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.plan_estudio_id_plan_seq1 OWNED BY public.plan_estudio.id_plan;


--
-- Name: refresh_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_token (
    id_refresh_token bigint NOT NULL,
    id_usuario bigint NOT NULL,
    token_hash character varying(255) NOT NULL,
    revocado boolean DEFAULT false NOT NULL,
    fecha_revocacion timestamp without time zone,
    reemplazado_por bigint,
    motivo_revocacion character varying(255),
    fecha_expiracion timestamp without time zone NOT NULL,
    fecha_creacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_refresh_token_motivo CHECK (((motivo_revocacion IS NULL) OR ((motivo_revocacion)::text = ANY (ARRAY[('ROTATION'::character varying)::text, ('LOGOUT'::character varying)::text, ('CASCADE'::character varying)::text]))))
);


--
-- Name: refresh_token_id_refresh_token_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.refresh_token_id_refresh_token_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: refresh_token_id_refresh_token_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.refresh_token_id_refresh_token_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: refresh_token_id_refresh_token_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.refresh_token_id_refresh_token_seq1 OWNED BY public.refresh_token.id_refresh_token;


--
-- Name: revinfo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.revinfo (
    rev integer NOT NULL,
    fecha_revision timestamp(6) without time zone NOT NULL,
    usuario character varying(255)
);


--
-- Name: revinfo_rev_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.revinfo_rev_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 2147483647
    CACHE 1;


--
-- Name: revinfo_rev_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.revinfo ALTER COLUMN rev ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.revinfo_rev_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: solicitud_aula; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solicitud_aula (
    id_solicitud bigint NOT NULL,
    tipo_solicitud character varying(40) NOT NULL,
    ambito character varying(30) NOT NULL,
    docente_nombre character varying(150) NOT NULL,
    docente_email character varying(150) NOT NULL,
    docente_telefono character varying(40) NOT NULL,
    id_materia bigint,
    fecha_creacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    id_glpi bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_solicitud_aula_ambito CHECK (((ambito)::text = ANY (ARRAY['GRADO'::text, 'TECNICATURA'::text, 'LICENCIATURA'::text, 'EXTENSION'::text, 'POSTGRADO'::text]))),
    CONSTRAINT chk_solicitud_aula_tipo CHECK (((tipo_solicitud)::text = ANY ((ARRAY['ONE_TIME_ROOM_CHANGE'::character varying, 'REGULAR_ROOM_CHANGE'::character varying, 'PARTIAL_EXAM_IN_CLASS'::character varying, 'PARTIAL_EXAM_OFF_SCHEDULE'::character varying, 'FINAL_EXAM'::character varying, 'CONFERENCE'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: solicitud_aula_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solicitud_aula_aud (
    id_solicitud bigint NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    id_glpi bigint,
    id_materia bigint,
    ambito character varying(30),
    docente_telefono character varying(40),
    tipo_solicitud character varying(40),
    docente_email character varying(150),
    docente_nombre character varying(150),
    CONSTRAINT solicitud_aula_aud_ambito_check CHECK (((ambito)::text = ANY (ARRAY[('GRADO'::character varying)::text, ('TECNICATURA'::character varying)::text, ('LICENCIATURA'::character varying)::text, ('EXTENSION'::character varying)::text, ('POSTGRADO'::character varying)::text]))),
    CONSTRAINT solicitud_aula_aud_tipo_solicitud_check CHECK (((tipo_solicitud)::text = ANY ((ARRAY['ONE_TIME_ROOM_CHANGE'::character varying, 'REGULAR_ROOM_CHANGE'::character varying, 'PARTIAL_EXAM_IN_CLASS'::character varying, 'PARTIAL_EXAM_OFF_SCHEDULE'::character varying, 'FINAL_EXAM'::character varying, 'CONFERENCE'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: solicitud_aula_id_solicitud_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.solicitud_aula_id_solicitud_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: solicitud_aula_id_solicitud_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.solicitud_aula ALTER COLUMN id_solicitud ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.solicitud_aula_id_solicitud_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: solicitud_aula_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solicitud_aula_item (
    id_item bigint NOT NULL,
    id_solicitud bigint NOT NULL,
    orden integer NOT NULL,
    id_comision bigint,
    fecha date,
    hora_inicio time without time zone,
    duracion_minutos integer,
    cantidad_estimada integer,
    cantidad_aulas integer DEFAULT 1 NOT NULL,
    requiere_proyector boolean DEFAULT false NOT NULL,
    requiere_computadoras boolean DEFAULT false NOT NULL,
    cantidad_computadoras integer,
    requiere_usuarios_examen boolean,
    software_requerido character varying(255),
    observaciones character varying(1000),
    estado character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    decidido_por character varying(150),
    fecha_decision timestamp without time zone,
    motivo_decision character varying(255),
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    id_evento_recurrente bigint,
    dia_semana character varying(20),
    id_aula_actual bigint,
    cantidad_inscriptos integer,
    CONSTRAINT chk_solicitud_item_aulas CHECK ((cantidad_aulas >= 1)),
    CONSTRAINT chk_solicitud_item_computadoras CHECK ((((requiere_computadoras = false) AND (cantidad_computadoras IS NULL)) OR ((requiere_computadoras = true) AND (cantidad_computadoras >= 1)))),
    CONSTRAINT chk_solicitud_item_decision CHECK ((((estado)::text = 'PENDING'::text) OR ((decidido_por IS NOT NULL) AND (fecha_decision IS NOT NULL)))),
    CONSTRAINT chk_solicitud_item_duracion CHECK ((duracion_minutos >= 1)),
    CONSTRAINT chk_solicitud_item_estado CHECK (((estado)::text = ANY (ARRAY['PENDING'::text, 'PRE_APPROVED'::text, 'CANCELLED'::text]))),
    CONSTRAINT chk_solicitud_item_estimada CHECK ((cantidad_estimada >= 0)),
    CONSTRAINT chk_solicitud_item_orden CHECK ((orden >= 1)),
    CONSTRAINT solicitud_aula_item_dia_semana_check CHECK (((dia_semana)::text = ANY ((ARRAY['MONDAY'::character varying, 'TUESDAY'::character varying, 'WEDNESDAY'::character varying, 'THURSDAY'::character varying, 'FRIDAY'::character varying, 'SATURDAY'::character varying, 'SUNDAY'::character varying])::text[])))
);


--
-- Name: solicitud_aula_item_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solicitud_aula_item_aud (
    id_item bigint NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    id_solicitud bigint,
    orden integer,
    id_comision bigint,
    fecha date,
    hora_inicio time without time zone,
    duracion_minutos integer,
    cantidad_estimada integer,
    cantidad_aulas integer,
    requiere_proyector boolean,
    requiere_computadoras boolean,
    cantidad_computadoras integer,
    requiere_usuarios_examen boolean,
    software_requerido character varying(255),
    observaciones character varying(1000),
    estado character varying(30),
    decidido_por character varying(150),
    fecha_decision timestamp without time zone,
    motivo_decision character varying(255),
    id_evento_recurrente bigint,
    dia_semana character varying(20),
    CONSTRAINT solicitud_aula_item_aud_estado_check CHECK (((estado)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('PRE_APPROVED'::character varying)::text, ('CANCELLED'::character varying)::text])))
);


--
-- Name: solicitud_aula_item_id_item_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.solicitud_aula_item_id_item_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: solicitud_aula_item_id_item_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.solicitud_aula_item ALTER COLUMN id_item ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.solicitud_aula_item_id_item_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: solicitud_aula_preferencia; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solicitud_aula_preferencia (
    id_preferencia bigint NOT NULL,
    id_item bigint NOT NULL,
    id_aula bigint NOT NULL,
    orden integer NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_solicitud_preferencia_orden CHECK ((orden >= 1))
);


--
-- Name: solicitud_aula_preferencia_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solicitud_aula_preferencia_aud (
    id_preferencia bigint NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    id_item bigint,
    id_aula bigint,
    orden integer
);


--
-- Name: solicitud_aula_preferencia_id_preferencia_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.solicitud_aula_preferencia_id_preferencia_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: solicitud_aula_preferencia_id_preferencia_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.solicitud_aula_preferencia ALTER COLUMN id_preferencia ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.solicitud_aula_preferencia_id_preferencia_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sysacad_sync_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sysacad_sync_state (
    id_estado_sync bigint NOT NULL,
    vista character varying(32) NOT NULL,
    ultimo_sync_ok timestamp without time zone,
    filas_afectadas integer,
    ultimo_error character varying(1000),
    ultimo_error_en timestamp without time zone,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sysacad_sync_state_id_estado_sync_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sysacad_sync_state_id_estado_sync_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sysacad_sync_state_id_estado_sync_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.sysacad_sync_state ALTER COLUMN id_estado_sync ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.sysacad_sync_state_id_estado_sync_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tipo_aula; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tipo_aula (
    id_tipo_aula bigint NOT NULL,
    descripcion character varying(50) NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    eliminado_en timestamp without time zone
);


--
-- Name: tipo_aula_id_tipo_aula_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tipo_aula_id_tipo_aula_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipo_aula_id_tipo_aula_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tipo_aula_id_tipo_aula_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipo_aula_id_tipo_aula_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tipo_aula_id_tipo_aula_seq1 OWNED BY public.tipo_aula.id_tipo_aula;


--
-- Name: tipo_recurso; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tipo_recurso (
    id_tipo_recurso bigint NOT NULL,
    nombre character varying(100) NOT NULL,
    tipo_valor character varying(20) NOT NULL,
    eliminado_en timestamp without time zone,
    creado_en timestamp without time zone NOT NULL,
    actualizado_en timestamp without time zone NOT NULL
);


--
-- Name: tipo_recurso_id_tipo_recurso_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tipo_recurso_id_tipo_recurso_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipo_recurso_id_tipo_recurso_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tipo_recurso_id_tipo_recurso_seq OWNED BY public.tipo_recurso.id_tipo_recurso;


--
-- Name: usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario (
    id_usuario bigint NOT NULL,
    correo character varying(150) NOT NULL,
    habilitado boolean DEFAULT true NOT NULL,
    password_hash character varying(255) NOT NULL
);


--
-- Name: usuario_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario_aud (
    id_usuario bigint NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    correo character varying(150),
    password_hash character varying(255),
    habilitado boolean
);


--
-- Name: usuario_id_usuario_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.usuario_id_usuario_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuario_id_usuario_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.usuario ALTER COLUMN id_usuario ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.usuario_id_usuario_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: usuario_rol; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario_rol (
    id_usuario bigint NOT NULL,
    rol character varying(255) NOT NULL,
    CONSTRAINT chk_usuario_rol_valor CHECK (((rol)::text = ANY (ARRAY[('SUBSECRETARIA'::character varying)::text, ('AUXILIAR_AULICO'::character varying)::text])))
);


--
-- Name: usuario_rol_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario_rol_aud (
    id_usuario bigint NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    rol character varying(255) NOT NULL,
    CONSTRAINT usuario_rol_aud_rol_check CHECK (((rol)::text = ANY (ARRAY[('SUBSECRETARIA'::character varying)::text, ('AUXILIAR_AULICO'::character varying)::text])))
);


--
-- Name: asignacion_aula id_asignacion; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asignacion_aula ALTER COLUMN id_asignacion SET DEFAULT nextval('public.asignacion_aula_id_asignacion_seq1'::regclass);


--
-- Name: aula id_aula; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula ALTER COLUMN id_aula SET DEFAULT nextval('public.aula_id_aula_seq1'::regclass);


--
-- Name: aula_permiso id_aula_permiso; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula_permiso ALTER COLUMN id_aula_permiso SET DEFAULT nextval('public.aula_permiso_id_aula_permiso_seq'::regclass);


--
-- Name: aula_recurso id_aula_recurso; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula_recurso ALTER COLUMN id_aula_recurso SET DEFAULT nextval('public.aula_recurso_id_aula_recurso_seq'::regclass);


--
-- Name: comision id_comision; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comision ALTER COLUMN id_comision SET DEFAULT nextval('public.comision_id_comision_seq1'::regclass);


--
-- Name: edificio id_edificio; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edificio ALTER COLUMN id_edificio SET DEFAULT nextval('public.edificio_id_edificio_seq1'::regclass);


--
-- Name: especialidad id_especialidad; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.especialidad ALTER COLUMN id_especialidad SET DEFAULT nextval('public.especialidad_id_especialidad_seq1'::regclass);


--
-- Name: evento_academico id_evento_academico; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_academico ALTER COLUMN id_evento_academico SET DEFAULT nextval('public.evento_academico_id_evento_academico_seq1'::regclass);


--
-- Name: materia id_materia; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materia ALTER COLUMN id_materia SET DEFAULT nextval('public.materia_id_materia_seq1'::regclass);


--
-- Name: ocurrencia id_ocurrencia; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ocurrencia ALTER COLUMN id_ocurrencia SET DEFAULT nextval('public.ocurrencia_id_ocurrencia_seq1'::regclass);


--
-- Name: periodo_academico id_periodo; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.periodo_academico ALTER COLUMN id_periodo SET DEFAULT nextval('public.periodo_academico_id_periodo_seq1'::regclass);


--
-- Name: plan_estudio id_plan; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_estudio ALTER COLUMN id_plan SET DEFAULT nextval('public.plan_estudio_id_plan_seq1'::regclass);


--
-- Name: refresh_token id_refresh_token; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token ALTER COLUMN id_refresh_token SET DEFAULT nextval('public.refresh_token_id_refresh_token_seq1'::regclass);


--
-- Name: tipo_aula id_tipo_aula; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_aula ALTER COLUMN id_tipo_aula SET DEFAULT nextval('public.tipo_aula_id_tipo_aula_seq1'::regclass);


--
-- Name: tipo_recurso id_tipo_recurso; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_recurso ALTER COLUMN id_tipo_recurso SET DEFAULT nextval('public.tipo_recurso_id_tipo_recurso_seq'::regclass);


--
-- Name: asignacion_aula_aud asignacion_aula_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asignacion_aula_aud
    ADD CONSTRAINT asignacion_aula_aud_pkey PRIMARY KEY (rev, id_asignacion);


--
-- Name: asignacion_aula asignacion_aula_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asignacion_aula
    ADD CONSTRAINT asignacion_aula_pkey PRIMARY KEY (id_asignacion);


--
-- Name: aula_permiso aula_permiso_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula_permiso
    ADD CONSTRAINT aula_permiso_pkey PRIMARY KEY (id_aula_permiso);


--
-- Name: aula aula_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula
    ADD CONSTRAINT aula_pkey PRIMARY KEY (id_aula);


--
-- Name: aula_recurso aula_recurso_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula_recurso
    ADD CONSTRAINT aula_recurso_pkey PRIMARY KEY (id_aula_recurso);


--
-- Name: comision comision_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comision
    ADD CONSTRAINT comision_pkey PRIMARY KEY (id_comision);


--
-- Name: edificio edificio_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edificio
    ADD CONSTRAINT edificio_pkey PRIMARY KEY (id_edificio);


--
-- Name: especialidad especialidad_codigo_especialidad_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.especialidad
    ADD CONSTRAINT especialidad_codigo_especialidad_key UNIQUE (codigo_especialidad);


--
-- Name: especialidad especialidad_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.especialidad
    ADD CONSTRAINT especialidad_pkey PRIMARY KEY (id_especialidad);


--
-- Name: event_publication event_publication_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.event_publication
    ADD CONSTRAINT event_publication_pkey PRIMARY KEY (id);


--
-- Name: evento_academico_aud evento_academico_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_academico_aud
    ADD CONSTRAINT evento_academico_aud_pkey PRIMARY KEY (rev, id_evento_academico);


--
-- Name: evento_academico evento_academico_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_academico
    ADD CONSTRAINT evento_academico_pkey PRIMARY KEY (id_evento_academico);


--
-- Name: evento_recurrente_aud evento_recurrente_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_recurrente_aud
    ADD CONSTRAINT evento_recurrente_aud_pkey PRIMARY KEY (rev, id_evento_academico);


--
-- Name: evento_recurrente evento_recurrente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_recurrente
    ADD CONSTRAINT evento_recurrente_pkey PRIMARY KEY (id_evento_academico);


--
-- Name: evento_unico_aud evento_unico_academico_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_unico_aud
    ADD CONSTRAINT evento_unico_academico_aud_pkey PRIMARY KEY (rev, id_evento_academico);


--
-- Name: evento_unico evento_unico_academico_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_unico
    ADD CONSTRAINT evento_unico_academico_pkey PRIMARY KEY (id_evento_academico);


--
-- Name: materia_comision materia_comision_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materia_comision
    ADD CONSTRAINT materia_comision_pkey PRIMARY KEY (id_materia, id_comision);


--
-- Name: materia materia_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materia
    ADD CONSTRAINT materia_pkey PRIMARY KEY (id_materia);


--
-- Name: ocurrencia_aud ocurrencia_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ocurrencia_aud
    ADD CONSTRAINT ocurrencia_aud_pkey PRIMARY KEY (rev, id_ocurrencia);


--
-- Name: ocurrencia ocurrencia_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ocurrencia
    ADD CONSTRAINT ocurrencia_pkey PRIMARY KEY (id_ocurrencia);


--
-- Name: periodo_academico periodo_academico_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.periodo_academico
    ADD CONSTRAINT periodo_academico_pkey PRIMARY KEY (id_periodo);


--
-- Name: configuracion pk_configuracion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuracion
    ADD CONSTRAINT pk_configuracion PRIMARY KEY (clave);


--
-- Name: configuracion_aud pk_configuracion_aud; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuracion_aud
    ADD CONSTRAINT pk_configuracion_aud PRIMARY KEY (clave, rev);


--
-- Name: solicitud_aula_aud pk_solicitud_aula_aud; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_aud
    ADD CONSTRAINT pk_solicitud_aula_aud PRIMARY KEY (rev, id_solicitud);


--
-- Name: solicitud_aula_item_aud pk_solicitud_aula_item_aud; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_item_aud
    ADD CONSTRAINT pk_solicitud_aula_item_aud PRIMARY KEY (rev, id_item);


--
-- Name: solicitud_aula_preferencia_aud pk_solicitud_aula_preferencia_aud; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_preferencia_aud
    ADD CONSTRAINT pk_solicitud_aula_preferencia_aud PRIMARY KEY (rev, id_preferencia);


--
-- Name: usuario_aud pk_usuario_aud; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_aud
    ADD CONSTRAINT pk_usuario_aud PRIMARY KEY (id_usuario, rev);


--
-- Name: usuario_rol_aud pk_usuario_rol_aud; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_rol_aud
    ADD CONSTRAINT pk_usuario_rol_aud PRIMARY KEY (id_usuario, rev, rol);


--
-- Name: plan_estudio plan_estudio_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_estudio
    ADD CONSTRAINT plan_estudio_pkey PRIMARY KEY (id_plan);


--
-- Name: refresh_token refresh_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT refresh_token_pkey PRIMARY KEY (id_refresh_token);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: solicitud_aula_item solicitud_aula_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_item
    ADD CONSTRAINT solicitud_aula_item_pkey PRIMARY KEY (id_item);


--
-- Name: solicitud_aula solicitud_aula_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula
    ADD CONSTRAINT solicitud_aula_pkey PRIMARY KEY (id_solicitud);


--
-- Name: solicitud_aula_preferencia solicitud_aula_preferencia_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_preferencia
    ADD CONSTRAINT solicitud_aula_preferencia_pkey PRIMARY KEY (id_preferencia);


--
-- Name: sysacad_sync_state sysacad_sync_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sysacad_sync_state
    ADD CONSTRAINT sysacad_sync_state_pkey PRIMARY KEY (id_estado_sync);


--
-- Name: tipo_aula tipo_aula_descripcion_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_aula
    ADD CONSTRAINT tipo_aula_descripcion_key UNIQUE (descripcion);


--
-- Name: tipo_aula tipo_aula_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_aula
    ADD CONSTRAINT tipo_aula_pkey PRIMARY KEY (id_tipo_aula);


--
-- Name: tipo_recurso tipo_recurso_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_recurso
    ADD CONSTRAINT tipo_recurso_pkey PRIMARY KEY (id_tipo_recurso);


--
-- Name: plan_estudio uk149w966iwnk7i5t1ttyip6s7l; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_estudio
    ADD CONSTRAINT uk149w966iwnk7i5t1ttyip6s7l UNIQUE (codigo_plan, id_especialidad);


--
-- Name: periodo_academico ukndf1ui3mk8lss011ethd5bkf9; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.periodo_academico
    ADD CONSTRAINT ukndf1ui3mk8lss011ethd5bkf9 UNIQUE (anio, cuatrimestre);


--
-- Name: asignacion_aula uq_asignacion_ocurrencia; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asignacion_aula
    ADD CONSTRAINT uq_asignacion_ocurrencia UNIQUE (id_ocurrencia);


--
-- Name: aula_permiso uq_aula_permiso; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula_permiso
    ADD CONSTRAINT uq_aula_permiso UNIQUE (id_aula, tipo_objetivo, id_objetivo);


--
-- Name: aula_recurso uq_aula_recurso; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula_recurso
    ADD CONSTRAINT uq_aula_recurso UNIQUE (id_aula, id_tipo_recurso);


--
-- Name: edificio uq_edificio_codigo_edificio; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edificio
    ADD CONSTRAINT uq_edificio_codigo_edificio UNIQUE (codigo_edificio);


--
-- Name: materia uq_materia_plan_codigo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materia
    ADD CONSTRAINT uq_materia_plan_codigo UNIQUE (id_plan, codigo_materia);


--
-- Name: ocurrencia uq_ocurrencia_evento_fecha; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ocurrencia
    ADD CONSTRAINT uq_ocurrencia_evento_fecha UNIQUE (id_evento_academico, fecha);


--
-- Name: refresh_token uq_refresh_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash);


--
-- Name: solicitud_aula uq_solicitud_aula_glpi; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula
    ADD CONSTRAINT uq_solicitud_aula_glpi UNIQUE (id_glpi);


--
-- Name: solicitud_aula_item uq_solicitud_item_orden; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_item
    ADD CONSTRAINT uq_solicitud_item_orden UNIQUE (id_solicitud, orden);


--
-- Name: solicitud_aula_preferencia uq_solicitud_preferencia_item_aula; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_preferencia
    ADD CONSTRAINT uq_solicitud_preferencia_item_aula UNIQUE (id_item, id_aula);


--
-- Name: solicitud_aula_preferencia uq_solicitud_preferencia_item_orden; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_preferencia
    ADD CONSTRAINT uq_solicitud_preferencia_item_orden UNIQUE (id_item, orden);


--
-- Name: sysacad_sync_state uq_sysacad_sync_state_vista; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sysacad_sync_state
    ADD CONSTRAINT uq_sysacad_sync_state_vista UNIQUE (vista);


--
-- Name: tipo_recurso uq_tipo_recurso_nombre; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_recurso
    ADD CONSTRAINT uq_tipo_recurso_nombre UNIQUE (nombre);


--
-- Name: usuario_rol uq_usuario_rol; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_rol
    ADD CONSTRAINT uq_usuario_rol UNIQUE (id_usuario, rol);


--
-- Name: usuario usuario_correo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_correo_key UNIQUE (correo);


--
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id_usuario);


--
-- Name: idx_asignacion_aula; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asignacion_aula ON public.asignacion_aula USING btree (id_aula);


--
-- Name: idx_asignacion_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asignacion_source ON public.asignacion_aula USING btree (origen);


--
-- Name: idx_aula_edificio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_aula_edificio ON public.aula USING btree (id_edificio);


--
-- Name: idx_aula_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_aula_tipo ON public.aula USING btree (id_tipo_aula);


--
-- Name: idx_comision_periodo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_comision_periodo ON public.comision USING btree (id_periodo_academico);


--
-- Name: idx_event_publication_by_completion_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_event_publication_by_completion_date ON public.event_publication USING btree (completion_date);


--
-- Name: idx_evento_academico_comision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_academico_comision ON public.evento_academico USING btree (id_comision);


--
-- Name: idx_evento_academico_materia; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_academico_materia ON public.evento_academico USING btree (id_materia);


--
-- Name: idx_evento_academico_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_academico_tipo ON public.evento_academico USING btree (tipo_evento);


--
-- Name: idx_evento_recurrente_day; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_recurrente_day ON public.evento_recurrente USING btree (dia_semana);


--
-- Name: idx_evento_recurrente_fechas; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_recurrente_fechas ON public.evento_recurrente USING btree (fecha_inicio, fecha_fin);


--
-- Name: idx_evento_unico_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_unico_fecha ON public.evento_unico USING btree (fecha);


--
-- Name: idx_evento_unico_tipo_actividad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_unico_tipo_actividad ON public.evento_unico USING btree (tipo_actividad);


--
-- Name: idx_materia_comision_comision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_materia_comision_comision ON public.materia_comision USING btree (id_comision);


--
-- Name: idx_materia_comision_materia; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_materia_comision_materia ON public.materia_comision USING btree (id_materia);


--
-- Name: idx_materia_plan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_materia_plan ON public.materia USING btree (id_plan);


--
-- Name: idx_ocurrencia_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ocurrencia_evento ON public.ocurrencia USING btree (id_evento_academico);


--
-- Name: idx_ocurrencia_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ocurrencia_fecha ON public.ocurrencia USING btree (fecha);


--
-- Name: idx_ocurrencia_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ocurrencia_status ON public.ocurrencia USING btree (estado);


--
-- Name: idx_plan_especialidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_plan_especialidad ON public.plan_estudio USING btree (id_especialidad);


--
-- Name: idx_refresh_token_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_token_usuario ON public.refresh_token USING btree (id_usuario);


--
-- Name: idx_solicitud_aula_fecha_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitud_aula_fecha_creacion ON public.solicitud_aula USING btree (fecha_creacion);


--
-- Name: idx_solicitud_aula_materia; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitud_aula_materia ON public.solicitud_aula USING btree (id_materia);


--
-- Name: idx_solicitud_item_comision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitud_item_comision ON public.solicitud_aula_item USING btree (id_comision);


--
-- Name: idx_solicitud_item_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitud_item_estado ON public.solicitud_aula_item USING btree (estado);


--
-- Name: idx_solicitud_item_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitud_item_fecha ON public.solicitud_aula_item USING btree (fecha);


--
-- Name: idx_solicitud_item_solicitud; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitud_item_solicitud ON public.solicitud_aula_item USING btree (id_solicitud);


--
-- Name: idx_solicitud_preferencia_aula; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitud_preferencia_aula ON public.solicitud_aula_preferencia USING btree (id_aula);


--
-- Name: idx_solicitud_preferencia_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitud_preferencia_item ON public.solicitud_aula_preferencia USING btree (id_item);


--
-- Name: idx_usuario_rol_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuario_rol_usuario ON public.usuario_rol USING btree (id_usuario);


--
-- Name: aula_permiso aula_permiso_id_aula_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula_permiso
    ADD CONSTRAINT aula_permiso_id_aula_fkey FOREIGN KEY (id_aula) REFERENCES public.aula(id_aula);


--
-- Name: aula_recurso aula_recurso_id_aula_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula_recurso
    ADD CONSTRAINT aula_recurso_id_aula_fkey FOREIGN KEY (id_aula) REFERENCES public.aula(id_aula);


--
-- Name: aula_recurso aula_recurso_id_tipo_recurso_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula_recurso
    ADD CONSTRAINT aula_recurso_id_tipo_recurso_fkey FOREIGN KEY (id_tipo_recurso) REFERENCES public.tipo_recurso(id_tipo_recurso);


--
-- Name: asignacion_aula fk_asignacion_aula; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asignacion_aula
    ADD CONSTRAINT fk_asignacion_aula FOREIGN KEY (id_aula) REFERENCES public.aula(id_aula);


--
-- Name: asignacion_aula_aud fk_asignacion_aula_aud_rev; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asignacion_aula_aud
    ADD CONSTRAINT fk_asignacion_aula_aud_rev FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: asignacion_aula fk_asignacion_ocurrencia; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asignacion_aula
    ADD CONSTRAINT fk_asignacion_ocurrencia FOREIGN KEY (id_ocurrencia) REFERENCES public.ocurrencia(id_ocurrencia);


--
-- Name: aula fk_aula_edificio; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula
    ADD CONSTRAINT fk_aula_edificio FOREIGN KEY (id_edificio) REFERENCES public.edificio(id_edificio);


--
-- Name: aula fk_aula_tipo_aula; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aula
    ADD CONSTRAINT fk_aula_tipo_aula FOREIGN KEY (id_tipo_aula) REFERENCES public.tipo_aula(id_tipo_aula);


--
-- Name: comision fk_comision_periodo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comision
    ADD CONSTRAINT fk_comision_periodo FOREIGN KEY (id_periodo_academico) REFERENCES public.periodo_academico(id_periodo);


--
-- Name: configuracion_aud fk_configuracion_aud_rev; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuracion_aud
    ADD CONSTRAINT fk_configuracion_aud_rev FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: evento_academico_aud fk_evento_academico_aud_rev; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_academico_aud
    ADD CONSTRAINT fk_evento_academico_aud_rev FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: evento_academico fk_evento_academico_comision; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_academico
    ADD CONSTRAINT fk_evento_academico_comision FOREIGN KEY (id_comision) REFERENCES public.comision(id_comision);


--
-- Name: evento_academico fk_evento_academico_materia; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_academico
    ADD CONSTRAINT fk_evento_academico_materia FOREIGN KEY (id_materia) REFERENCES public.materia(id_materia);


--
-- Name: evento_recurrente_aud fk_evento_recurrente_aud_evento; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_recurrente_aud
    ADD CONSTRAINT fk_evento_recurrente_aud_evento FOREIGN KEY (rev, id_evento_academico) REFERENCES public.evento_academico_aud(rev, id_evento_academico);


--
-- Name: evento_recurrente fk_evento_recurrente_evento_academico; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_recurrente
    ADD CONSTRAINT fk_evento_recurrente_evento_academico FOREIGN KEY (id_evento_academico) REFERENCES public.evento_academico(id_evento_academico);


--
-- Name: evento_unico_aud fk_evento_unico_academico_aud_evento; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_unico_aud
    ADD CONSTRAINT fk_evento_unico_academico_aud_evento FOREIGN KEY (rev, id_evento_academico) REFERENCES public.evento_academico_aud(rev, id_evento_academico);


--
-- Name: evento_unico fk_evento_unico_academico_evento_academico; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento_unico
    ADD CONSTRAINT fk_evento_unico_academico_evento_academico FOREIGN KEY (id_evento_academico) REFERENCES public.evento_academico(id_evento_academico);


--
-- Name: materia_comision fk_materia_comision_comision; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materia_comision
    ADD CONSTRAINT fk_materia_comision_comision FOREIGN KEY (id_comision) REFERENCES public.comision(id_comision);


--
-- Name: materia_comision fk_materia_comision_materia; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materia_comision
    ADD CONSTRAINT fk_materia_comision_materia FOREIGN KEY (id_materia) REFERENCES public.materia(id_materia);


--
-- Name: materia fk_materia_plan; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materia
    ADD CONSTRAINT fk_materia_plan FOREIGN KEY (id_plan) REFERENCES public.plan_estudio(id_plan);


--
-- Name: ocurrencia_aud fk_ocurrencia_aud_rev; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ocurrencia_aud
    ADD CONSTRAINT fk_ocurrencia_aud_rev FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: ocurrencia fk_ocurrencia_evento_academico; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ocurrencia
    ADD CONSTRAINT fk_ocurrencia_evento_academico FOREIGN KEY (id_evento_academico) REFERENCES public.evento_academico(id_evento_academico);


--
-- Name: plan_estudio fk_plan_especialidad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.plan_estudio
    ADD CONSTRAINT fk_plan_especialidad FOREIGN KEY (id_especialidad) REFERENCES public.especialidad(id_especialidad);


--
-- Name: refresh_token fk_refresh_token_reemplazado_por; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT fk_refresh_token_reemplazado_por FOREIGN KEY (reemplazado_por) REFERENCES public.refresh_token(id_refresh_token);


--
-- Name: refresh_token fk_refresh_token_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_token
    ADD CONSTRAINT fk_refresh_token_usuario FOREIGN KEY (id_usuario) REFERENCES public.usuario(id_usuario);


--
-- Name: solicitud_aula_aud fk_solicitud_aula_aud_rev; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_aud
    ADD CONSTRAINT fk_solicitud_aula_aud_rev FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: solicitud_aula_item_aud fk_solicitud_aula_item_aud_rev; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_item_aud
    ADD CONSTRAINT fk_solicitud_aula_item_aud_rev FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: solicitud_aula fk_solicitud_aula_materia; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula
    ADD CONSTRAINT fk_solicitud_aula_materia FOREIGN KEY (id_materia) REFERENCES public.materia(id_materia);


--
-- Name: solicitud_aula_preferencia_aud fk_solicitud_aula_preferencia_aud_rev; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_preferencia_aud
    ADD CONSTRAINT fk_solicitud_aula_preferencia_aud_rev FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: solicitud_aula_item fk_solicitud_item_comision; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_item
    ADD CONSTRAINT fk_solicitud_item_comision FOREIGN KEY (id_comision) REFERENCES public.comision(id_comision);


--
-- Name: solicitud_aula_item fk_solicitud_item_solicitud; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_item
    ADD CONSTRAINT fk_solicitud_item_solicitud FOREIGN KEY (id_solicitud) REFERENCES public.solicitud_aula(id_solicitud);


--
-- Name: solicitud_aula_preferencia fk_solicitud_preferencia_aula; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_preferencia
    ADD CONSTRAINT fk_solicitud_preferencia_aula FOREIGN KEY (id_aula) REFERENCES public.aula(id_aula);


--
-- Name: solicitud_aula_preferencia fk_solicitud_preferencia_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aula_preferencia
    ADD CONSTRAINT fk_solicitud_preferencia_item FOREIGN KEY (id_item) REFERENCES public.solicitud_aula_item(id_item);


--
-- Name: usuario_aud fk_usuario_aud_rev; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_aud
    ADD CONSTRAINT fk_usuario_aud_rev FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: usuario_rol_aud fk_usuario_rol_aud_rev; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_rol_aud
    ADD CONSTRAINT fk_usuario_rol_aud_rev FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: usuario_rol fk_usuario_rol_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_rol
    ADD CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (id_usuario) REFERENCES public.usuario(id_usuario);


--
-- PostgreSQL database dump complete
--


