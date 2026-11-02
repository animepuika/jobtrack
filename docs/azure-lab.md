# Azure lab: temporary deployment (milestone 8)

The goal is a short learning session: deploy JobTrack with fictional data, use it over
HTTPS, record evidence, then delete everything and confirm nothing is left. Nothing
in Azure needs to keep running afterwards.

**Status:** the infrastructure is described in [infra/main.bicep](../infra/main.bicep)
and compiles; CI publishes the images. No Azure resources have been created yet:
that needs your Azure account and the cost checks below.

## What gets created

All in one resource group (`jobtrack-lab`), tagged `purpose=jobtrack-lab`:

| Resource | Purpose | Cost notes to check |
| --- | --- | --- |
| Container Apps environment (Consumption) | Runs both services | Container Apps has a monthly free grant of vCPU-seconds, memory and requests per subscription |
| Container app `jobtrack-app` | Public HTTPS tracker with demo data, scales to zero | Billed only while running beyond the free grant |
| Container app `jobtrack-reminders` | Internal reminder service, scales to zero, woken by a queue rule | Same |
| Storage account (Standard LRS) | Event queues and a private `cv-files` container | Pennies at lab volumes, but not zero; not covered by Container Apps' grant |
| Log Analytics workspace | Container logs | Capped at 0.1 GB a day in the template; first 5 GB a month were free at the time of planning |

Not created in Azure:

- **Databases:** two databases on [Neon's Free plan](https://neon.com/pricing)
  (`jobtrack` and `reminders`), so no managed Azure PostgreSQL cost is assumed.
- **Container registry:** images come from GitHub Container Registry
  (`ghcr.io/<owner>/jobtrack-app`, `ghcr.io/<owner>/jobtrack-reminder-service`),
  published by CI after tests pass on `main`.

## Before you start (only you can do these)

1. **Check pricing and free grants today**, for your region: Container Apps,
   Storage (queues and blobs), Log Analytics, and data transfer. Prices and free
   allowances change; the notes above are from planning, not a guarantee.
2. **Azure account:** use an existing subscription or start a free trial close to
   the session, since trial credit expires. Do not upgrade to pay-as-you-go just for
   this lab.
3. **Budget alert:** create one (for example EUR 1) on the subscription. Alerts only
   notify; they do not stop spending, so teardown is still required.
4. **Neon:** create a free project with two databases and note their JDBC URLs
   (`jdbc:postgresql://<host>/<db>?sslmode=require`), users and passwords.
5. **Images:** after CI publishes them, open each package on GitHub (Your profile →
   Packages) and set its visibility to **Public**, so Azure can pull it without
   registry credentials. The images contain only the application; check that first.
6. **Tools:** `az login`, and `az bicep version` (Bicep CLI is installed).

## Deploy

From the repository root in PowerShell:

```powershell
./infra/deploy.ps1 -Owner <github-user> `
  -TrackerDbUrl "jdbc:postgresql://<host>/jobtrack?sslmode=require" -TrackerDbUsername <user> `
  -RemindersDbUrl "jdbc:postgresql://<host>/reminders?sslmode=require" -RemindersDbUsername <user>
```

The script asks for the two database passwords, generates a random internal token,
deploys `main.bicep`, and prints the HTTPS address. Sign in as `demo` /
`demo-password` (fictional data only; live job search is off).

## Evidence to capture

- The tracker over HTTPS, with fictional data, in a screenshot.
- Add a reminder and see it arrive: proof that the queue and the reminder service
  work, including the reminder service waking from zero.
- Container logs in Log Analytics (both services).
- The resource group's resources and the Cost Management view for the day.

## Tear down and verify

```powershell
./infra/teardown.ps1
```

It deletes the resource group, confirms it is gone, and lists anything still tagged
`purpose=jobtrack-lab`. Cost reports can lag by a day, so check Cost Management again
the next day. Delete the Neon project too if you no longer need it.

## Known limits of this lab

- Uploaded CVs are stored inside the tracker container and disappear when it stops.
  Moving `DocumentStorage` to the private `cv-files` container is the planned Blob
  Storage exercise.
- Secrets are passed as Container Apps secrets from the deploy script. A next step is
  managed identity for the storage account, removing the connection string.
