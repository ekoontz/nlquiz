# nlquiz

Learn Dutch by translating simple expressions.

## Development mode

```
src/sh/local.sh
```

Figwheel will automatically push cljs changes to the browser. 

Figwheel also starts `nREPL` using the value of the `:nrepl-port` in the `:figwheel`
config found in `project.clj`. By default the port is set to `7002`.

### Optional development tools

Start the browser REPL:

```
$ lein repl
```
The Jetty server can be started by running:

```clojure
(start-server)
```
and stopped by running:
```clojure
(stop-server)
```

## Deploying to production

```
./src/sh/deploy.sh
```

See [src/sh](src/sh) for build scripts.

## Add new curriculum items

Modify the following:

### `resources/public/edn/specs.edn`

Add some new specs with new major and minor tags.

### `src/cljs/nlquiz/curriculum/content.cljs`

Add a new `"the-new-section" (fn [] [:div "Hello world"])` section under `majorsection`

### `resources/public/edn/curriculum.edn`

Add a new `{:name "A nice human friendly name" :href "majorsection/the-new-section"}`.



