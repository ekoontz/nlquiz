# nlquiz

Learn Dutch by translating simple expressions.

## Development mode

```
DEV=true ./src/sh/local.sh
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

## Add new curriculum topic

1. Add a new .edn file for the topic content to the file hierarchy
with [edn/curriculum](https://github.com/ekoontz/nlquiz/tree/master/resources/public/edn/curriculum). See
[edn/curriculum/nouns/indef-adj.edn](https://github.com/ekoontz/nlquiz/blob/master/resources/public/edn/curriculum/nouns/indef-adj.edn)
for an example of what the content looks like.

2. Choose a human-readable phrase as a name for your new curriculum topic, e.g. "Indefinite articles and adjectives"

3. Modify [edn/curriculum.edn](https://github.com/ekoontz/nlquiz/blob/master/resources/public/edn/curriculum.edn) to add a new
   `:name,:href` pair to the .edn map in that file, using the name you
   chose in step 2 as the `:name` and the path to the .edn file that you created in
   step 1 as the `:href`, for example:
   
```
{:name "Indefinite articles and adjectives"
 :href "nouns/indef-adj"}
```
