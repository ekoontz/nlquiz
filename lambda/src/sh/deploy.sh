sudo service docker start && time ((cd ~/menard && git pull && git log -1 && lein install) && (cd ~/nlquiz/lambda/ && git pull &&  make clean deploy-native))
