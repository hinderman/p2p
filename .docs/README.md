# Project-local development environment

The development toolchain is self-contained under `C:\Proyect`; it does not require global installations.

## Installed components

| Component | Version | Location |
| --- | --- | --- |
| Eclipse Temurin JDK | 25.0.4+7 LTS | `.languages\jdk-25` |
| Node.js and npm | 24.19.0 / 11.17.0 | `.languages\node-24` |
| Apache Maven | 3.9.16 | `.tools\maven` |
| Angular CLI | 22.1.3 | `.tools\npm-global` |
| PostgreSQL | 18.6 | `.tools\pgsql` |

## Activate the environment

Run the following in every new PowerShell terminal:

```powershell
. C:\Proyect\.tools\env.ps1
```

It configures local Java, Node.js, npm, Maven, Angular CLI, and PostgreSQL binaries for the current session.

## Local database

The project uses a dedicated PostgreSQL cluster on port `5433`.

| Setting | Value |
| --- | --- |
| Host | `localhost` |
| Port | `5433` |
| Database | `proyectdb` |
| Username | `proyect` |
| Password | `proyect` |

```powershell
C:\Proyect\.tools\db.ps1 start
C:\Proyect\.tools\db.ps1 status
C:\Proyect\.tools\db.ps1 stop
C:\Proyect\.tools\db.ps1 psql
```

These are local development credentials only. Do not reuse them outside this workspace.

## Workspace layout

```text
C:\Proyect
├── .database\       Idempotent PostgreSQL database-first scripts
├── .languages\      Project-local runtimes
├── .tools\          Project-local build tools and PostgreSQL controls
├── .docs\           Development environment documentation
├── back\            Clean Architecture backend
└── front\           Hybrid web and mobile client workspace
```
