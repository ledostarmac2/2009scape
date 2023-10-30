#!/usr/bin/env bash

# Usage: ./script.sh [path_to_java]
# If path_to_java is not provided, the script will use the default java command.

export _JAVA_OPTIONS=
SCRIPT_DIR=$(cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P)
FIRST_RUN=1

# Use the provided java command or fall back to the default
JAVA_CMD=${1:-java}

lsof -i:43595

if [ $? -ne 1 ]; then
  echo "Something is already listening on port 43595. Cannot start the server."
  exit
fi

if [ -f "$SCRIPT_DIR/game/data/eco/grandexchange.db" ]; then
  FIRST_RUN=0
else
  echo "This is first run of the single-player release in this location."
  echo "Please allow some time (30-60s) for initial data structure creation before asking for support."

  sleep 3
fi

cd $SCRIPT_DIR/game
$JAVA_CMD -jar -Xmx2G -Xms2G -jar server.jar &
SERVER_PID=$!

lsof -i:43595 > /dev/null
while [ $? -eq "1" ]; do
  if [ $FIRST_RUN -ne "1" ]; then
    echo "Still waiting for the server to start..."
    sleep 2
  fi

  lsof -i:43595 > /dev/null
done;

sleep 2
$JAVA_CMD -Xmx1G -Xms1G -jar client.jar

kill $SERVER_PID
exit
