(cd ../babylon ; git checkout 0.0.1 ; make clean; make compile; lein install);
rm target/cljsbuild/public/js/app.js target/cljsbuild/public/js/app-optimized.js
lein cljsbuild once min
lein figwheel
