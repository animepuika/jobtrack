<#
Stops JobTrack's Docker services: the databases, the queue and the reminder service.
Your data stays in Docker's volumes; nothing is deleted. Stop the tracker itself
with Ctrl+C in its window, or by closing that window.
#>
param(
	[string]$Project = 'app'
)

$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

& docker compose -p $Project stop
if ($LASTEXITCODE -ne 0) {
	Write-Host 'Docker could not stop the services. Is Docker running?' -ForegroundColor Red
	exit 1
}
Write-Host ''
Write-Host 'Stopped. Your data is kept; start again with start.cmd.' -ForegroundColor Green
