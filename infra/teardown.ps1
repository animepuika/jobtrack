# Deletes the JobTrack lab and checks that nothing is left behind.
#
#   ./infra/teardown.ps1 [-ResourceGroup jobtrack-lab]

param([string] $ResourceGroup = 'jobtrack-lab')

$ErrorActionPreference = 'Stop'

az group delete --name $ResourceGroup --yes
if ((az group exists --name $ResourceGroup) -eq 'true') {
	throw "Resource group $ResourceGroup still exists. Check the Azure portal."
}
Write-Host "Resource group $ResourceGroup is deleted."

$leftovers = az resource list --tag purpose=jobtrack-lab --query "[].{name:name, type:type, group:resourceGroup}" --output table
if ($leftovers) {
	Write-Warning "Resources tagged purpose=jobtrack-lab still exist outside the group:"
	$leftovers
}
else {
	Write-Host "No tagged lab resources remain. Cost reports can lag by a day; check Cost Management tomorrow."
}
