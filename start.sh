#(cd ../menard ; git checkout master; make clean; make compile; lein install);
lein clean
rm target/cljsbuild/public/js/app.js target/cljsbuild/public/js/app-optimized.js
lein cljsbuild once min
ROOT_PATH=/nlquiz lein figwheel
