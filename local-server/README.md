Uses the `menard-lambda` library, so it must be installed via `lein install` before you start
the server with `lein ring server-headless`:
```
(cd ~/menard/lambda/src/menard/lambda/ && lein install) && lein ring server-headless && ORIGIN=http://192.168.178.31:3449 lein ring server-headless
```

TODO: move the server code from `local-server` to `menard/lambda`; there's not really
anything in the latter that shouldn't be there instead.


