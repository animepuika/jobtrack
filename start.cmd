@echo off
rem Double-click to start JobTrack. It opens in your browser when it is ready.
rem Closing this window stops JobTrack; your data is kept.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0app\start.ps1" %*
if errorlevel 1 pause
