ssh hiro-tan.org "cp nlquiz/target/nlquiz-blue.jar nlquiz/target/nlquiz-green-tmp.jar" && \
ssh hiro-tan.org "sudo systemctl stop nlquiz-green" && \
ssh hiro-tan.org "mv /home/ekoontz/nlquiz/target/nlquiz-green-tmp.jar /home/ekoontz/nlquiz/target/nlquiz-green.jar" && \
ssh hiro-tan.org "sudo systemctl restart nlquiz-green"

