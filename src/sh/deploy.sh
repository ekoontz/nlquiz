( stat /tmp/nlquiz/ || (git clone $(pwd) /tmp/nlquiz ) && \
      cd /tmp/nlquiz && \
      lein clean && git config pull.ff only && \
      git fetch origin && git checkout origin/master && ./src/sh/update-prod-blue.sh && \
      sleep 60 && ./src/sh/blue-to-green.sh && \
      ./src/sh/pull.sh
)
