// Temporary Azure lab for JobTrack (milestone 8). Everything lives in one resource
// group, so the whole lab can be removed with one command (see teardown.ps1).
// Databases are external (Neon's free PostgreSQL) to avoid assuming a managed Azure
// database is free; pass their JDBC URLs as parameters.

targetScope = 'resourceGroup'

@description('Azure region for all resources.')
param location string = resourceGroup().location

@description('Short prefix for resource names, lower-case letters and digits.')
@minLength(3)
@maxLength(12)
param namePrefix string = 'jobtrack'

@description('Tracker image, for example ghcr.io/<owner>/jobtrack-app:latest.')
param trackerImage string

@description('Reminder service image, for example ghcr.io/<owner>/jobtrack-reminder-service:latest.')
param reminderImage string

@description('JDBC URL of the tracker database, including sslmode=require.')
param trackerDbUrl string

param trackerDbUsername string

@secure()
param trackerDbPassword string

@description('JDBC URL of the reminder service database, a separate database from the tracker.')
param remindersDbUrl string

param remindersDbUsername string

@secure()
param remindersDbPassword string

@description('Shared token the tracker sends to the reminder service. Use a long random value.')
@secure()
@minLength(16)
param internalToken string

var storageName = take('${namePrefix}${uniqueString(resourceGroup().id)}', 24)
var queueName = 'reminder-events'

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${namePrefix}-logs'
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
    // Stop collecting after 0.1 GB a day, so logs cannot run up a bill during the lab.
    workspaceCapping: { dailyQuotaGb: json('0.1') }
  }
}

resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: storageName
  location: location
  sku: { name: 'Standard_LRS' }
  kind: 'StorageV2'
  properties: {
    minimumTlsVersion: 'TLS1_2'
    allowBlobPublicAccess: false
    supportsHttpsTrafficOnly: true
  }
}

resource queueService 'Microsoft.Storage/storageAccounts/queueServices@2023-05-01' = {
  parent: storage
  name: 'default'
}

resource eventsQueue 'Microsoft.Storage/storageAccounts/queueServices/queues@2023-05-01' = {
  parent: queueService
  name: queueName
}

resource poisonQueue 'Microsoft.Storage/storageAccounts/queueServices/queues@2023-05-01' = {
  parent: queueService
  name: '${queueName}-poison'
}

resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2023-05-01' = {
  parent: storage
  name: 'default'
}

// Private container for the CV storage exercise; nothing in it is publicly readable.
resource cvContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-05-01' = {
  parent: blobService
  name: 'cv-files'
  properties: { publicAccess: 'None' }
}

var storageConnection = 'DefaultEndpointsProtocol=https;AccountName=${storage.name};AccountKey=${storage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}'

resource env 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${namePrefix}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

// Internal only: reachable from the tracker inside the environment, not the internet.
// It scales to zero, and a queue scale rule starts it when events are waiting, because
// a stopped container cannot run its own polling timer.
resource reminderService 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${namePrefix}-reminders'
  location: location
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      ingress: { external: false, targetPort: 8081, transport: 'http' }
      secrets: [
        { name: 'db-password', value: remindersDbPassword }
        { name: 'queue-connection', value: storageConnection }
        { name: 'internal-token', value: internalToken }
      ]
    }
    template: {
      containers: [
        {
          name: 'reminder-service'
          image: reminderImage
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
          env: [
            { name: 'REMINDERS_DB_URL', value: remindersDbUrl }
            { name: 'REMINDERS_DB_USERNAME', value: remindersDbUsername }
            { name: 'REMINDERS_DB_PASSWORD', secretRef: 'db-password' }
            { name: 'REMINDERS_QUEUE_CONNECTION_STRING', secretRef: 'queue-connection' }
            { name: 'REMINDERS_INTERNAL_TOKEN', secretRef: 'internal-token' }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 1
        rules: [
          {
            name: 'events-waiting'
            azureQueue: {
              queueName: queueName
              queueLength: 1
              auth: [ { secretRef: 'queue-connection', triggerParameter: 'connection' } ]
            }
          }
        ]
      }
    }
  }
  dependsOn: [ eventsQueue, poisonQueue ]
}

// Public HTTPS endpoint with the fictional demo data. Live job search stays off in the
// demo, and it scales to zero when nobody is using it.
resource tracker 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${namePrefix}-app'
  location: location
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      ingress: { external: true, targetPort: 8080, transport: 'auto', allowInsecure: false }
      secrets: [
        { name: 'db-password', value: trackerDbPassword }
        { name: 'queue-connection', value: storageConnection }
        { name: 'internal-token', value: internalToken }
      ]
    }
    template: {
      containers: [
        {
          name: 'tracker'
          image: trackerImage
          resources: { cpu: json('0.5'), memory: '1Gi' }
          env: [
            { name: 'SPRING_PROFILES_ACTIVE', value: 'demo' }
            { name: 'JOBTRACK_HIRINGCAFE_ENABLED', value: 'false' }
            { name: 'JOBTRACK_DB_URL', value: trackerDbUrl }
            { name: 'JOBTRACK_DB_USERNAME', value: trackerDbUsername }
            { name: 'JOBTRACK_DB_PASSWORD', secretRef: 'db-password' }
            { name: 'JOBTRACK_QUEUE_CONNECTION_STRING', secretRef: 'queue-connection' }
            { name: 'JOBTRACK_REMINDERS_URL', value: 'http://${reminderService.properties.configuration.ingress.fqdn}' }
            { name: 'REMINDERS_INTERNAL_TOKEN', secretRef: 'internal-token' }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 1
        rules: [ { name: 'http', http: { metadata: { concurrentRequests: '20' } } } ]
      }
    }
  }
}

output trackerUrl string = 'https://${tracker.properties.configuration.ingress.fqdn}'
output storageAccount string = storage.name
