unset MODEL_URL && \
lein uberjar && \
    scp target/nlquiz.jar hiro-tan.org:nlquiz/target/nlquiz-blue-tmp.jar && \
    ssh hiro-tan.org "sudo systemctl stop nlquiz-blue" && \
    ssh hiro-tan.org "mv /home/ekoontz/nlquiz/target/nlquiz-blue-tmp.jar /home/ekoontz/nlquiz/target/nlquiz-blue.jar" && \
    ssh hiro-tan.org "sudo systemctl restart nlquiz-blue"

