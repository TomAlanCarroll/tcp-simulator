@echo off
echo Compiling the simulator...
rmdir /s /q bin
mkdir bin
javac -d bin -classpath "lib\opencsv-2.3.jar;" src\simulation\*.java src\simulation\network\*.java src\simulation\tcp\*.java src\simulation\network\topology\*.java

echo Welcome, this will run the simulation.
:: Set the congestion avoidance algorithm (Tahoe, Reno, or NewReno)
set /P congestionAvoidanceAlgorithm= Please enter the congestion avoidance algorithm (Tahoe, Reno, or NewReno):
:: Set the number of clients
set /a maxClients= 100

set /a numClients = 0
set /a numRouters = 1

setlocal enabledelayedexpansion enableextensions

:: Start looping here while increasing the jar pars
:: Loop from 0 to maxClients
for /L %%i in (1 1 %maxClients%) do (
    set /a numClients = !numClients! + 1
::    set /a numRouters = !numRouters! + 1
    java -classpath "bin;lib\opencsv-2.3.jar;" simulation.Simulator !congestionAvoidanceAlgorithm! 100 !topology! 6244 65536 !numClients! !numRouters!

    :: The two lines below are used for testing
    echo %numClients%  !numClients!
    echo %numRouters%  !numRouters!
)
@echo on