@echo off

set CUR_PATH=%cd%
set JAVA_FILE=%JAVA_HOME%\bin\java.exe
set ANT_FILE=%ANT_HOME%\bin\ant
set VERSION_FILE=VERSION

if "%*" == "clean" GOTO :CHECK_ENV
if "%*" == "" GOTO :CHECK_ENV
if "%*" == "/help" GOTO :SHOW_USAGE
if "%*" == "/h" GOTO :SHOW_USAGE
if "%*" == "/?" GOTO :SHOW_USAGE
GOTO :SHOW_USAGE

:CHECK_ENV
echo Checking for requirements...
call :FINDEXEC java.exe JAVA_FILE "%JAVA_FILE%"
if "%JAVA_HOME%" == "" (
  echo "[ERROR] set environment variable is required. (PATH and JAVA_HOME)"
  GOTO :EOF 
)

call :FINDEXEC ant ANT_PATH "%ANT%"
if "%ANT_PATH%" == "" (
  if "%ANT_HOME%" == "" (
    echo "[ERROR] set environment variable is required. (ANT Or ANT_HOME)"
    GOTO :EOF 
  ) else (
    call :FINDEXEC ant ANT_PATH "%ANT_FILE%"
 )
)

if "%ANT_PATH%" == "" (
  echo "Please check ANT_HOME or ANT(excute file)"
  GOTO :EOF 
) else (
  set ANT=%ANT_PATH%
)

:BUILD
for /f "delims=" %%i in (%CUR_PATH%\%VERSION_FILE%) do set VERSION=%%i

echo "VERSION = %VERSION%
if "%*" == "clean" (
  %ANT_PATH% clean -buildfile ./build.xml
) else (
  
  if NOT EXIST "%CUR_PATH%\\output" (
    mkdir output
  )
  copy VERSION output\CUBRID-JDBC-%VERSION%
  %ANT_PATH% dist-cubrid -buildfile ./build.xml -Dbasedir=. -Dversion=%VERSION% -Dsrc=./src
)
GOTO :EOF

:FINDEXEC
if EXIST %3 set %2=%~3
if NOT EXIST %3 for %%X in (%1) do set FOUNDINPATH=%%~$PATH:X
if defined FOUNDINPATH set %2=%FOUNDINPATH:"=%
if NOT defined FOUNDINPATH if NOT EXIST %3 echo Executable [%1] is not found & GOTO :EOF
call echo Executable [%1] is found at [%%%2%%]
GOTO :EOF

:SHOW_USAGE
@echo.Usage: %0 [OPTION]
@echo.Build scrtip for CUBRID JDBC Driver
@echo. OPTIONS
@echo.  clean			   Clean (jar and build file)
@echo.  /help /h /?        Display this help message and exit
@echo. Examples:
@echo.  %0             # JDBC Build
@echo.  %0 clean       # Clean result file
GOTO :EOF
