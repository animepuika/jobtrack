@echo off
rem Double-click to stop JobTrack's Docker services. Your data is kept.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0app\stop.ps1" %*
pause
