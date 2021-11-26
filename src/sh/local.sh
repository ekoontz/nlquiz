(cd ../menard && lein install)

OLD_UI_SERVER=$(lsof -i TCP:3449 | grep LISTEN | awk '{print $2}')
OLD_LANGUAGE_SERVER=$(lsof -i TCP:3000 | grep LISTEN | awk '{print $2}')

if [ "${OLD_UI_SERVER}" != "" ]; then
    echo "it seems like a UI server is already running as pid ${OLD_UI_SERVER}. Killing it."
    kill -TERM ${OLD_UI_SERVER}
    sleep 2
    echo "continuing after killing UI server."
fi
if [ "${OLD_LANGUAGE_SERVER}" != "" ]; then
    echo "it seems like a language server is already running as pid ${OLD_LANGUAGE_SERVER}. Killing it."
    kill -TERM ${OLD_LANGUAGE_SERVER}
    sleep 2
    echo "continuing after killing language server."
fi

if [ "${HOSTNAME}" == "" ]; then
    echo "no HOSTNAME found in environment. Will look at 'ifconfig' and try to determine one..."
    HOSTNAME=$(ifconfig | grep inet | grep -v 127.0.0.1 | awk '{print $2}' | head -n1)
    echo "..determined hostname: ${HOSTNAME}."
fi

# lowercase hostname: browsers are sensitive to it for enforcing policies for
# cross-site scripting:
HOSTNAME=$(echo $(perl -e "print lc('$HOSTNAME');"))

echo "USING HOSTNAME: ${HOSTNAME}"

lein clean
# Start the language endpoint server in the background. This will generate
# expressions for the user and parse their responses. It listens on port
# 3000, where it handles requests to:
# - generate expressions for the user
# - parse the user's guesses of answers to those expressions
# The ORIGIN is the URL for the UI server (started below).
# The language endpoint needs to know that URL so it can set the
# header: 'Access-Control-Allow-Origin' to that.
pushd .
cd ~/menard/server
ORIGIN=http://${HOSTNAME}:3449 lein ring server-headless &
LANGUAGE_SERVER_PID=$!
popd
# Start the UI server. It needs to know the URL for the language endpoint server
# so it can tell the client to use that URL for generating guesses and parse answers:
# Here we set the ROOT_PATH to "/" but we could have also set it to "", or simply not
# set it at all, and the same effect would be had.
LANGUAGE_ENDPOINT_URL=http://${HOSTNAME}:3000 ROOT_PATH="/" lein figwheel &

UI_SERVER_PID=$!

RESPONSE=$(curl -s http://${HOSTNAME}:3000)
while [ "$?" -ne "0" ]; do
      sleep 1
      RESPONSE=$(curl -s http://${HOSTNAME}:3000)
done

echo "LANGUAGE_SERVER_PID=${LANGUAGE_SERVER_PID}"
echo "UI_SERVER_PID=${UI_SERVER_PID}"

echo ""
echo ""
echo "************************************************************************************"
echo "**** UI server has started. Go to http://${HOSTNAME}:3449 in your browser.                "
echo "***    (not quite yet; wait for the figwheel to start. The logs will show up here.) "
echo "************************************************************************************"
echo ""
echo ""

_cleanup() { 
  echo "Caught SIGINT signal - cleaning up."
  echo kill -TERM "$LANGUAGE_SERVER_PID"
  kill -TERM "$LANGUAGE_SERVER_PID"
  echo kill -TERM "$UI_SERVER_PID"
  kill -TERM "$UI_SERVER_PID"

  OLD_LANGUAGE_SERVER=$(lsof -i TCP:3000 | grep LISTEN | awk '{print $2}')
  if [ "${OLD_LANGUAGE_SERVER}" != "" ]; then
      echo "it seems like a language server is still running as pid ${OLD_LANGUAGE_SERVER}: killing it."
      echo kill -TERM "${OLD_LANGUAGE_SERVER}"
      kill -TERM "${OLD_LANGUAGE_SERVER}"
  fi
}

trap _cleanup SIGINT

wait $LANGUAGE_SERVER_PID
wait $UI_SERVER_PID
