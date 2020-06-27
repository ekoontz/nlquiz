lein uberjar && \
    scp target/nlquiz.jar hiro-tan.org:nlquiz/target/nlquiz-blue.jar && \
    ssh hiro-tan.org "sudo systemctl restart nlquiz-blue"

