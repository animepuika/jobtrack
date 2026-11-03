# Azure lab: temporary deployment (milestone 8)

The goal is a short learning session: deploy JobTrack with fictional data, use it over
HTTPS, record evidence, then delete everything and confirm nothing is left. Nothing
in Azure needs to keep running afterwards.

**Status:** the infrastructure is described in [infra/main.bicep](../infra/main.bicep)
and compiles; CI publishes the images. No Azure resources have been created yet:
that needs your Azure account and the cost checks below.

## What gets created

All in one resource group (`jobtrack-lab`), tagged `purpose=jobtrack-lab`:

| Resource | Purpose | Cost notes (checked November 3, 2026) |
| --- | --- | --- |
| Container Apps environment (Consumption) | Runs both services | Free grant per subscription per month: 180,000 vCPU-seconds, 360,000 GiB-seconds, 2 million requests |
| Container app `jobtrack-app` | Public HTTPS tracker with demo data, scales to zero | 0.5 vCPU / 1 GiB: about 100 hours of running time fits in the free grant |
| Container app `jobtrack-reminders` | Internal reminder service, scales to zero, woken by a queue rule | 0.25 vCPU / 0.5 GiB; shares the same grant |
| Storage account (Standard LRS) | Event queues and a private `cv-files` container | Pennies at lab volumes, but not zero; not covered by Container Apps' grant |
| Log Analytics workspace | Container logs | First 5 GB a month per billing account free, 31 days' retention free; the template caps ingestion at 0.1 GB a day |

A two-hour lab session should stay inside the free grants, apart from a few cents of
storage, which trial or student credit covers.

### Which Azure account to use

| Option | What you get | Notes |
| --- | --- | --- |
| Azure for Students | $100 credit for 12 months, no card | Needs a current student email at an eligible school; the subscription is disabled when the credit runs out unless you upgrade |
| Azure free account | $200 credit for 30 days, plus 12 months of free amounts of popular services | Needs a Microsoft or GitHub account, a phone and a non-prepaid card (identity check, possibly a temporary $1 hold). You are not charged unless you choose pay-as-you-go. One per person |

Start the free account's 30 days close to the lab session. Neon's Free plan needs no
card: 100 CU-hours of compute and 1 GB of storage per project each month, and its
compute suspends after 5 minutes idle and wakes on the next query.

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
