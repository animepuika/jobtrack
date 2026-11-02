# Creates the temporary JobTrack lab in its own resource group. Run teardown.ps1 when
# the lab session is over. See docs/azure-lab.md for the cost checks to do first.
#
#   ./infra/deploy.ps1 -Owner <github-user> -TrackerDbUrl ... -RemindersDbUrl ...
#
# Database passwords and the internal token are asked for, not passed on the command
# line, so they do not end up in shell history.

param(
	[Parameter(Mandatory)] [string] $Owner,
	[Parameter(Mandatory)] [string] $TrackerDbUrl,
	[Parameter(Mandatory)] [string] $TrackerDbUsername,
	[Parameter(Mandatory)] [string] $RemindersDbUrl,
	[Parameter(Mandatory)] [string] $RemindersDbUsername,
	[string] $ResourceGroup = 'jobtrack-lab',
	[string] $Location = 'westeurope',
	[string] $ImageTag = 'latest'
)

$ErrorActionPreference = 'Stop'

$trackerDbPassword = Read-Host 'Tracker database password' -AsSecureString
$remindersDbPassword = Read-Host 'Reminder database password' -AsSecureString
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$internalToken = [Convert]::ToBase64String($bytes)

function Plain([securestring] $value) {
	[System.Net.NetworkCredential]::new('', $value).Password
}

az group create --name $ResourceGroup --location $Location --tags purpose=jobtrack-lab | Out-Null

$parameters = @{
	trackerImage        = @{ value = "ghcr.io/$($Owner.ToLower())/jobtrack-app:$ImageTag" }
	reminderImage       = @{ value = "ghcr.io/$($Owner.ToLower())/jobtrack-reminder-service:$ImageTag" }
	trackerDbUrl        = @{ value = $TrackerDbUrl }
	trackerDbUsername   = @{ value = $TrackerDbUsername }
	trackerDbPassword   = @{ value = (Plain $trackerDbPassword) }
	remindersDbUrl      = @{ value = $RemindersDbUrl }
	remindersDbUsername = @{ value = $RemindersDbUsername }
	remindersDbPassword = @{ value = (Plain $remindersDbPassword) }
	internalToken       = @{ value = $internalToken }
}
$parameterFile = New-TemporaryFile
try {
	@{ '$schema' = 'https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#'; contentVersion = '1.0.0.0'; parameters = $parameters } |
		ConvertTo-Json -Depth 5 | Set-Content -Path $parameterFile -Encoding utf8
	$url = az deployment group create --resource-group $ResourceGroup --template-file "$PSScriptRoot/main.bicep" `
		--parameters "@$parameterFile" --query properties.outputs.trackerUrl.value --output tsv
}
finally {
	Remove-Item $parameterFile -Force
}

Write-Host "JobTrack lab is running at $url (sign in as demo / demo-password)."
Write-Host "When you are done: ./infra/teardown.ps1 -ResourceGroup $ResourceGroup"
