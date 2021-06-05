OLD_UI_SERVER=$(lsof -i TCP:3449 | grep LISTEN | awk '{print $2}')
OLD_LANGUAGE_SERVER=$(lsof -i TCP:3000 | grep LISTEN | awk '{print $2}')

if [ "${OLD_UI_SERVER}" != "" ]; then
    echo "it seems like a UI server is already running as pid ${OLD_UI_SERVER}."
    exit 1
fi
if [ "${OLD_LANGUAGE_SERVER}" != "" ]; then
    echo "it seems like a UI server is already running as pid ${OLD_LANGUAGE_SERVER}."
    exit 1
fi

IP=$(ifconfig | grep 192 | awk '{print $2}')

lein clean
# Start the language endpoint server in the background. This will generate
# expressions for the user and parse their responses. It listens on port
# 3000, where it handles requests to:
# - generate expressions for the user
# - parse the user's guesses of answers to those expressions
# The ORIGIN is the URL for the UI server (started below).
# The language endpoint needs to know that URL so it can set the
# header: 'Access-Control-Allow-Origin' to that.
(cd local-server && ORIGIN=http://${IP}:3449 lein ring server-headless) &
LANGUAGE_SERVER_PID=$!

# Start the UI server. It needs to know the URL for the language endpoint server
# so it can tell the client to use that URL for generating guesses and parse answers:
LANGUAGE_ENDPOINT_URL=http://${IP}:3000 ROOT_PATH=http://${IP}:3449/ lein figwheel &
UI_SERVER_PID=$!

RESPONSE=$(curl -s http://{$IP}:3000)
while [ "$?" -ne "0" ]; do
      sleep 1
      RESPONSE=$(curl -s http://{$IP}:3000)
done

echo "LANGUAGE_SERVER_PID=${LANGUAGE_SERVER_PID}"
echo "UI_SERVER_PID=${UI_SERVER_PID}"

echo "**** UI server has started. Go to https://${IP}:3000 in your browser. *****"

_cleanup() { 
  echo "Caught SIGINT signal - cleaning up."
  kill -TERM "$LANGUAGE_SERVER_PID"
  kill -TERM "$UI_SERVER_PID"
}

trap _cleanup SIGINT

wait $LANGUAGE_SERVER_PID
wait $UI_SERVER_PID
