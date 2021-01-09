# nlquiz

Learn Dutch by translating simple expressions.

## Development mode

In another terminal:

```
cd nlquiz-local && lein ring server-headless
```

(Re)start the Figwheel compiler by navigating to the project folder and run the following command in the terminal:

```
lein clean && LANGUAGE_ENDPOINT_URL=http://localhost:3000 ROOT_PATH=http://localhost:3449/ lein figwheel
```

Figwheel will automatically push cljs changes to the browser. The server will be available at [http://localhost:3449](http://localhost:3449) once Figwheel starts up. 

Figwheel also starts `nREPL` using the value of the `:nrepl-port` in the `:figwheel`
config found in `project.clj`. By default the port is set to `7002`.

The figwheel server can have unexpected behaviors in some situations such as when using
websockets. In this case it's recommended to run a standalone instance of a web server as follows:

```
lein do clean, run
```

The application will now be available at [http://localhost:3000](http://localhost:3000).

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

## Building for release

```
lein do clean, uberjar
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



