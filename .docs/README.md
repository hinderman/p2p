# Project-local development environment

The development toolchain is self-contained under `C:\MyProjects` (project in `C:\MyProjects\p2p`); it does not require global installations.

## Installed components

| Component | Version | Location |
| --- | --- | --- |
| Eclipse Temurin JDK | 25.0.4.1 LTS | `.languages\jdk-25` |
| Node.js and npm | 24.20.0 / 11.19.0 | `.languages\node-24` |
| Apache Maven | 3.9.16 (via `mvnw`; wrapper dist in `.tools\maven-home`, repo in `.tools\maven-repo`) | `back\mvnw.cmd` |
| PostgreSQL | 18.4 | `.tools\postgresql-18` |
| Mailpit (local SMTP sink) | 1.31.0 | `.tools\mailpit` |

## Activate the environment

Run the following in every new PowerShell terminal:

```powershell
. C:\MyProjects\.tools\env.ps1
```

It configures local Java, Node.js, npm, Maven (wrapper home + local repository), and PostgreSQL binaries for the current session.

## Local database

The project uses a dedicated PostgreSQL cluster on port `5433` (data in `.tools\pgdata-p2p`).

| Setting | Value |
| --- | --- |
| Host | `localhost` |
| Port | `5433` |
| Database | `proyectdb` |
| Username | `proyect` |
| Password | `proyect` |

```powershell
C:\MyProjects\.tools\db.ps1 init     # first time only: create cluster + role + database
C:\MyProjects\.tools\db.ps1 start
C:\MyProjects\.tools\db.ps1 status
C:\MyProjects\.tools\db.ps1 stop
```

These are local development credentials only. Do not reuse them outside this workspace.

## Local SMTP sink (invitation emails)

```powershell
Start-Process C:\MyProjects\.tools\mailpit\mailpit.exe -ArgumentList '--smtp','127.0.0.1:1025','--listen','127.0.0.1:8025','--db-file','C:\MyProjects\.tools\mailpit\mailpit.db' -WindowStyle Hidden
```

Inbox UI: <http://127.0.0.1:8025>

## Backend secrets (development only)

`C:\MyProjects\.tools\secrets.local.ps1` exports `JWT_HMAC_SECRET` and
`INVITATION_OUTBOX_ENCRYPTION_KEY` for local runs. Dot-source it before
starting the backend. It is machine-local and must never be committed.

## Run everything

```powershell
. C:\MyProjects\.tools\env.ps1
. C:\MyProjects\.tools\secrets.local.ps1
C:\MyProjects\.tools\db.ps1 start
cd C:\MyProjects\p2p\back
.\mvnw.cmd clean install
java -jar api\target\backend-api-0.0.1-SNAPSHOT.jar
# in another terminal:
cd C:\MyProjects\p2p\front
npm run dev
```

## Workspace layout

```text
C:\MyProjects
├── .languages\      Project-local runtimes (JDK 25, Node 24)
├── .tools\          Build tools, PostgreSQL, Mailpit, Maven home/repo, npm cache
└── p2p
    ├── .database\   Idempotent PostgreSQL database-first scripts
    ├── .docs\       Development environment documentation
    ├── back\        Clean Architecture backend
    └── front\       Hybrid web and mobile client workspace
```
