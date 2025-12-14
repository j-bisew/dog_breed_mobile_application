@echo off

:: Start the first terminal and navigate to the 'auth' folder, activate virtual environment and start the server
start cmd.exe /K "cd /d auth && venv\\Scripts\\activate && python authServer.py"

:: Start the second terminal and navigate to the 'database' folder, activate virtual environment and start the server
start cmd.exe /K "cd /d database && venv\\Scripts\\activate && python databaseHandlerServer.py"

:: Start the third terminal and navigate to the 'endpoints' folder, activate virtual environment and start the server
start cmd.exe /K "cd /d endpoints && venv\\Scripts\\activate && python endpointsServer.py"

:: Start the fourth terminal and navigate to the 'recognition' folder, activate virtual environment and start the server
start cmd.exe /K "cd /d recognition && venv\\Scripts\\activate && python recognitionServer.py"

exit
