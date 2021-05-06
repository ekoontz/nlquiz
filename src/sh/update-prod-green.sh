unset MODEL_URL && \
ROOT_PATH=/nlquiz lein uberjar && \
    scp target/nlquiz.jar hiro-tan.org:nlquiz/target/nlquiz-green-tmp.jar && \
    ssh hiro-tan.org "sudo systemctl stop nlquiz-green" && \
    ssh hiro-tan.org "mv /home/ekoontz/nlquiz/target/nlquiz-green-tmp.jar /home/ekoontz/nlquiz/target/nlquiz-green.jar" && \
    ssh hiro-tan.org "sudo systemctl restart nlquiz-green"

