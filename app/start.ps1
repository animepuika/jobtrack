<#
Starts JobTrack for everyday use:
  1. starts Docker Desktop if it isn't running,
  2. starts the databases, the queue and the reminder service in Docker,
  3. asks for a username and password the very first time, when no account exists,
  4. starts the tracker and opens it in the browser as soon as it answers.

Press Ctrl+C (or close the window) to stop the tracker. Run stop.ps1 to stop the
Docker services too. Nothing is deleted either way.

  .\start.ps1                 normal start
  .\start.ps1 -NoBrowser      don't open the browser
#>
param(
	[string]$Project = 'app',
	[int]$Port = 8080,
	[switch]$NoBrowser
)

# Native commands write progress to stderr; handle failures by exit code instead.
$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

function Write-Step([string]$text) {
	Write-Host ''
	Write-Host "==> $text" -ForegroundColor Cyan
}

function Stop-WithError([string]$text) {
	Write-Host ''
	Write-Host $text -ForegroundColor Red
	exit 1
}

function Test-Docker {
	& docker info *> $null
	return $LASTEXITCODE -eq 0
}

function Test-PortInUse([int]$number) {
	$client = New-Object System.Net.Sockets.TcpClient
	try {
		$client.Connect('127.0.0.1', $number)
		return $true
	}
	catch {
		return $false
	}
	finally {
		$client.Dispose()
	}
}

$url = "http://localhost:$Port/"

if (Test-PortInUse $Port) {
	Write-Host "Something is already running on port $Port, probably JobTrack. Opening $url"
	if (-not $NoBrowser) {
		Start-Process $url
	}
	exit 0
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
	Stop-WithError 'Docker is not installed. Install Docker Desktop, then run this again.'
}

if (-not (Test-Docker)) {
	$desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
	if (-not (Test-Path $desktop)) {
		Stop-WithError 'Docker is not running and Docker Desktop was not found. Start Docker, then run this again.'
	}
	Write-Step 'Starting Docker Desktop (this can take a minute)'
	Start-Process $desktop
	$deadline = (Get-Date).AddMinutes(3)
	while (-not (Test-Docker)) {
		if ((Get-Date) -gt $deadline) {
			Stop-WithError 'Docker Desktop did not start within 3 minutes. Open it yourself, wait until it says it is running, then run this again.'
		}
		Start-Sleep -Seconds 3
	}
}

Write-Step 'Starting the databases, the queue and the reminder service'
& docker compose -p $Project up -d --build --wait
if ($LASTEXITCODE -ne 0) {
	Stop-WithError 'Docker could not start the services. The reason is in the lines above.'
}

# A brand-new database has no account yet: ask for one, used only for this start.
$users = & docker compose -p $Project exec -T postgres psql -U jobtrack -d jobtrack -tAc 'select count(*) from app_user' 2> $null
$hasAccount = ($LASTEXITCODE -eq 0) -and ("$users".Trim() -ne '0')
if (-not $hasAccount -and -not $env:JOBTRACK_USERNAME) {
	Write-Step 'No account yet. Choose the username and password you will sign in with'
	$name = Read-Host 'Username'
	do {
		$secret = Read-Host 'Password (8 characters or more)' -AsSecureString
		$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
		try {
			$plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
		}
		finally {
			[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
		}
		if ($plain.Length -lt 8) {
			Write-Host 'Too short, try again.' -ForegroundColor Yellow
		}
	} while ($plain.Length -lt 8)
	$env:JOBTRACK_USERNAME = $name
	$env:JOBTRACK_PASSWORD = $plain
}

if (-not $NoBrowser) {
	# A small hidden PowerShell waits until the tracker answers, opens the browser and
	# ends; it gives up after about 4 minutes. A separate process (not Start-Job) is
	# used because background jobs can't always open windows.
	$opener = "for (`$i = 0; `$i -lt 120; `$i++) { try { Invoke-WebRequest -UseBasicParsing '$($url)login' -TimeoutSec 2 | Out-Null; Start-Process '$url'; break } catch { Start-Sleep -Seconds 2 } }"
	$encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($opener))
	Start-Process powershell -WindowStyle Hidden -ArgumentList '-NoProfile', '-EncodedCommand', $encoded
}

Write-Step "Starting JobTrack on $url (it opens in your browser when ready; press Ctrl+C to stop it)"
$env:SERVER_PORT = "$Port"
& .\mvnw.cmd -q spring-boot:run
