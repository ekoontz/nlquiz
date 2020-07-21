(cd /tmp/nlquiz/ && git config pull.ff only && git pull origin master && ./src/sh/update-prod-blue.sh && sleep 60 && ./src/sh/blue-to-green.sh)
