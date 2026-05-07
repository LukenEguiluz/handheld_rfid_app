"""
Referencia para POST /inventory/reconcile (ESFERICA / PostgreSQL).

La app Android envía:
{"client_id": "uuid", "warehouse_id": "uuid", "status": "DISPONIBLE", "rfids": ["E280...", ...]}

Consulta orientativa de inventario esperado:

FROM esferica.stock s
LEFT JOIN esferica.product p ON p.id::text = (s.data ->> 'product')
WHERE (s.data ->> 'client') = :client_id
  AND (s.data ->> 'warehouse') = :warehouse_id
  AND COALESCE(s.status::text, '') = :status

RFID: s.data->>'barcode' (en app: trim, mayúsculas, sin espacios internos).

Respuesta: mismo contrato que ReconcileResponseDto en Android (summary, products, matched, missing,
extra, extra_identified, extra_unknown).
"""


def normalize_rfid(epc: str) -> str:
    return "".join(epc.strip().upper().split())
