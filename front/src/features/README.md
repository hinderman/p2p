# Features

Cada capacidad de negocio vive en su propia carpeta. Una feature puede contener
sus páginas, componentes, estado, adaptadores HTTP y rutas. Las demás capas no
deben importar archivos internos de otra feature; exponga una API pública desde
su raíz cuando sea necesario.

No agregue aquí funcionalidades genéricas. Lo que sea reutilizable y no
pertenezca a un dominio debe vivir en `src/shared`.
