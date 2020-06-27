lein uberjar && \
    scp target/nlquiz.jar hiro-tan.org:nlquiz/target/nlquiz-green.jar && \
    ssh hiro-tan.org "sudo systemctl restart nlquiz-green"

