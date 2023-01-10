#!/usr/bin/env bash

export _JAVA_OPTIONS=
SCRIPT_DIR=$(cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P)

lsof -i:43595

if [ $? -ne 1 ]; then
  echo "Something is already listening on port 43595. Cannot start the server."
  exit
fi

cd $SCRIPT_DIR/game
java -jar -Xmx2G -Xms2G -jar server.jar &
SERVER_PID=$!

lsof -i:43595
while [ $? -eq 1 ]; do
  lsof -i:43595
  echo "Still waiting for the server to start..."
  sleep 2
done;

java -Xmx1G -Xms1G -jar client.jar &
CLIENT_PID=$!

wait $CLIENT_PID
kill $SERVER_PID
exit

