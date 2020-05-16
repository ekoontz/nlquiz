(cd ../babylon ; make clean; make compile; lein install);
rm target/cljsbuild/public/js/app.js target/cljsbuild/public/js/app-optimized.js
#lein cljsbuild once min
lein cljsbuild once app
lein figwheel
