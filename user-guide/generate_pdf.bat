@echo off
REM ============================================================================
REM  Genere 01-cycle-vie-reservation.pdf a partir du HTML, via Chrome/Edge headless.
REM  Aucune dependance externe (Chrome OU Edge requis, presents par defaut sur Windows).
REM  Usage : double-clic sur ce fichier, ou bien :
REM    cd user-guide ^&^& generate_pdf.bat
REM ============================================================================

setlocal EnableDelayedExpansion

set HTML_FILE=%~dp001-cycle-vie-reservation.html
set PDF_FILE=%~dp001-cycle-vie-reservation.pdf

REM Convertir en URL file:/// avec slashes (Chrome n'accepte pas les backslashes)
set HTML_URL=file:///%HTML_FILE:\=/%

REM Chercher Chrome ou Edge dans les emplacements standard
set BROWSER=
for %%P in (
  "C:\Program Files\Google\Chrome\Application\chrome.exe"
  "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
  "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
  "C:\Program Files\Microsoft\Edge\Application\msedge.exe"
) do (
  if exist "%%~P" (
    set BROWSER=%%~P
    goto :found
  )
)

echo [ERREUR] Aucun navigateur Chrome/Edge trouve dans les emplacements standard.
echo Solution alternative : ouvrir 01-cycle-vie-reservation.html dans votre navigateur
echo puis Ctrl+P -^> "Enregistrer au format PDF".
pause
exit /b 1

:found
echo Utilisation du navigateur : !BROWSER!
echo Source HTML : %HTML_FILE%
echo Destination : %PDF_FILE%
echo.

"!BROWSER!" --headless --disable-gpu --no-pdf-header-footer --print-to-pdf="%PDF_FILE%" "%HTML_URL%"

if exist "%PDF_FILE%" (
  echo.
  echo [OK] PDF genere : %PDF_FILE%
  for %%A in ("%PDF_FILE%") do echo Taille : %%~zA octets
) else (
  echo.
  echo [ECHEC] Le PDF n'a pas ete genere.
)

pause
endlocal
