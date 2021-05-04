# nlquiz

Learn Dutch by translating simple expressions.

## Development mode

In one terminal, start the _language endpoint_. This will generate
expressions for the user and parse their responses. It listens on port
3000, where it handles requests to:
- generate expressions for the user
- parse the user's guesses of answers to those expressions

```
cd ~/nlquiz/local-server && (cd ~/menard && lein install) && lein clean && ORIGIN=http://192.168.178.31:3449 lein ring server-headless
```

In another terminal, (re)start the nlquiz UI:

```
lein clean && LANGUAGE_ENDPOINT_URL=http://192.168.178.31:3000 ROOT_PATH=http://192.168.178.31:3449/ lein figwheel
```

Figwheel will automatically push cljs changes to the browser. The server will be available at [http://localhost:3449](http://localhost:3449) once Figwheel starts up. 

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



