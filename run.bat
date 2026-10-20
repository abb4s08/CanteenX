@echo off
setlocal
cd /d "%~dp0"

if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
    set "PATH=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin;%PATH%"
)

if exist "C:\Users\ASUS LAPTOP\AppData\Local\Programs\apache-maven-3.9.9\bin" (
    set "PATH=C:\Users\ASUS LAPTOP\AppData\Local\Programs\apache-maven-3.9.9\bin;%PATH%"
)

echo ===================================================
echo   Starting CanteenX Desktop Application...
echo ===================================================

java -cp "target\classes;lib\*" edu.canteenx.Main
