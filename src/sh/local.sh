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
( ( (cd local-server && ORIGIN=http://${IP}:3449 lein ring server-headless) & ) &&

# Start the UI server. It needs to know the URL for the language endpoint server
# so it can tell the client to use that URL for generating guesses and parse answers:
  (LANGUAGE_ENDPOINT_URL=http://${IP}:3000 ROOT_PATH=http://${IP}:3449/ lein figwheel & ))
echo "**** Go to https://${IP}:3000 in your browswer. *****"

