# ISO/IEC 25010:2023, modelo de calidad de producto

Vigencia: segunda edición, publicada en noviembre de 2023. Verificado el 2026-10-13.
Fuentes: https://www.iso.org/standard/78176.html y el resumen de
https://quality.arc42.org/standards/iso-25010 (la norma es paga; el resumen lista
características y subcaracterísticas).

## Nueve características y subcaracterísticas

| Característica | Subcaracterísticas |
|---|---|
| Functional Suitability | Functional Completeness, Functional Correctness, Functional Appropriateness |
| Performance Efficiency | Time Behaviour, Resource Utilization, Capacity |
| Compatibility | Co-Existence, Interoperability |
| Interaction Capability (antes Usability) | Appropriateness Recognisability, Learnability, Operability, User Error Protection, User Engagement, Inclusivity, User Assistance, Self Descriptiveness |
| Reliability | Faultlessness, Availability, Fault Tolerance, Recoverability |
| Security | Confidentiality, Integrity, Non-Repudiation, Accountability, Authenticity, Resistance |
| Maintainability | Modularity, Reusability, Analysability, Modifiability (Testability: ver nota) |
| Flexibility (antes Portability) | Adaptability, Scalability, Installability, Replaceability (arc42 suma Testability acá) |
| Safety (nueva) | Operational Constraint, Risk Identification, Fail Safe, Hazard Warning, Safe Integration |

Nota: arc42 ubica Testability bajo Flexibility en su tabla; en la edición 2011 estaba bajo
Maintainability. Si un hallazgo depende de esa ubicación, citá "ISO 25010 Testability" sin la
característica padre.

## Cómo se usa en una auditoría de SIGA

- Es el paraguas: cada dimensión del plan se mapea a una o más características, y los
  hallazgos de calidad de código citan la subcaracterística (`ISO 25010 Maintainability/Modularity`).
- Las que más pesan en este backend: Functional Correctness (reglas de choque de horario,
  estados de solicitudes), Security, Maintainability/Modularity (fronteras de Modulith),
  Reliability/Fault Tolerance (eventos entre módulos, sync con Sysacad), Interaction
  Capability/User Error Protection y Self Descriptiveness (mensajes de error de la API).
- Safety no aplica a SIGA (no controla sistemas físicos); dejarlo fuera explícitamente.
