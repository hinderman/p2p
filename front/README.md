# Hybrid Client

Cliente híbrido construido con Ionic React y Capacitor. La misma aplicación se
ejecuta en navegador y puede empaquetarse para plataformas móviles cuando se
definan sus requisitos de distribución.

## Estructura

```text
src/
  app/       composición de la aplicación, router y estilos globales
  core/      configuración e infraestructura transversal (HTTP, entorno)
  features/  capacidades de producto aisladas por dominio
  shared/    piezas reutilizables que no pertenecen a un dominio
  theme/     tokens visuales de Ionic
```

Una feature posee sus páginas, rutas y lógica. `app` conoce las features para
componer las rutas; una feature puede usar `core` y `shared`, pero no debe
importar detalles internos de otra feature.

## Desarrollo

1. Inicie el backend en `http://localhost:8080`. Vite envía `/api` a ese puerto
   mediante su proxy local, por lo que no se requiere CORS durante el desarrollo.
2. Para despliegue o ejecución móvil, copie `.env.example` como `.env.local` y
   configure `VITE_API_BASE_URL` con la URL HTTPS pública del backend.
3. Ejecute `npm run dev` desde esta carpeta.
4. Para validar producción, ejecute `npm run build`.

Las variables `VITE_*` se exponen en el cliente: nunca guarde secretos allí.

## Autenticación

El inicio de sesión usa únicamente correo y contraseña contra
`POST /api/v1/auth/sessions`. Los roles recibidos forman parte de la sesión, pero
el cliente no permite elegirlos ni los usa como autoridad: el backend conserva
la autorización definitiva de cada operación.

El access token y el refresh token permanecen en `sessionStorage`, se eliminan al
cerrar la sesión y el access token se renueva antes de expirar. Cuando el backend
pueda emitir el refresh token en una cookie `HttpOnly`, conviene migrar a ese
mecanismo para reducir aún más su exposición ante código JavaScript.

El cliente HTTP considera autenticadas las solicitudes por defecto: agrega el
Bearer vigente, comparte una sola renovación entre solicitudes concurrentes y
reintenta una sola vez después de un `401`. Login, refresh y onboarding se marcan
explícitamente como públicos. Un refresh rechazado elimina la sesión local; una
falla temporal de red o del servidor no la descarta.

Los problemas RFC devueltos por el backend se normalizan como `ApiError`,
conservando `status`, `code`, `detail` y las violaciones por campo.

### Comprobantes de pago

La pantalla de reporte carga cada PDF, PNG o JPEG al endpoint autenticado de
comprobantes. El usuario ya no escribe UUID ni hashes manualmente: la API devuelve
el identificador y el SHA-256 después del análisis de seguridad, y esos valores se
incorporan al reporte. El formulario no permite continuar mientras una carga esté
en curso o haya fallado.

### Onboarding del pagador

La invitación abre `/onboarding/payer#invitationToken=<token>`. La aplicación
captura el token una sola vez, lo retira inmediatamente de la barra de direcciones
y lo envía únicamente en el JSON de `POST /api/v1/onboarding/payer` junto con la
contraseña. El token no se guarda en `localStorage` ni en `sessionStorage`.

El backend activa o valida la cuenta, acepta el préstamo asociado y devuelve la
sesión autenticada de forma atómica. Actualmente no existe un endpoint público
para consultar los términos antes de consumir la invitación, por lo que el cliente
no presenta información de préstamo que el backend no haya proporcionado.

## Móvil

Capacitor está configurado con `webDir: dist`. Antes de agregar Android o iOS
defina un identificador de aplicación propio y sustituya el valor de ejemplo
`com.example.hybridclient` en `capacitor.config.ts`. Después se podrá añadir la
plataforma objetivo y sincronizar el build con `npm run cap:sync`.
