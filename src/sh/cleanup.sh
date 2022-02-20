OLD_UI_SERVER=$(lsof -i TCP:3449 | grep LISTEN | awk '{print $2}')
OLD_LANGUAGE_SERVER_BY_PORT=$(lsof -i TCP:3000 | grep LISTEN | awk '{print $2}')
OLD_LANGUAGE_SERVER_BY_NAME=$(ps | grep server-headless | grep -v grep | awk '{print $1}')

if [ "${OLD_UI_SERVER}" != "" ]; then
    echo "A UI server is running as pid ${OLD_UI_SERVER}. Killing it."
    kill -TERM ${OLD_UI_SERVER}
else
    echo "No UI server found running (tried port 3449)."
fi

if [ "${OLD_LANGUAGE_SERVER_BY_PORT}" != "" ]; then
    echo "One or more processes of language server running: ${OLD_LANGUAGE_SERVER_BY_PORT}. Killing them."
    echo ${OLD_LANGUAGE_SERVER_BY_PORT} | sed "s/ /\n/" | xargs -I{} kill -TERM {}
else
    echo "No language server found running by-port detection method."
fi

if [ "${OLD_LANGUAGE_SERVER_BY_NAME}" != "" ]; then
    echo "One or more processes of language server running: ${OLD_LANGUAGE_SERVER_BY_NAME}. Killing them."
    echo ${OLD_LANGUAGE_SERVER_BY_NAME} | sed "s/ /\n/" | xargs -I{} kill -TERM {}
else
    echo "No language server found running by-name detection method."
fi
