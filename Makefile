build:
	(cd ../babylon ; make clean compile; lein install); rm target/cljsbuild/public/js/app-optimized.js;  lein cljsbuild once min ;  lein figwheel
