# Prerequisites

- `aws`
- `sam`

## Additional prerequisites needed from running Lambdas locally

- Docker (used by `sam` to host the Lambda execution environment)

## Additional prerequisites for building native lambdas

- Docker (on non-Linux build machines)
- graalvm (on Linux build machines): run 'make install-graalvm' two levels up.

# Deploy a locally-hosted Lambda

You have the option of running either `make dry-api` or `make native-dry-api`.

## `make dry-api`

This runs the java artifact `output.jar` using `sam local` with `template.yml` as its
AWS SAM template file.

## `make dry-api-native`

This compiles the Clojure code into a `.jar`, and then runs GraalVM
either natively (if you are on Linux) or within a Docker container
(if you are *not* on Linux) to create a Linux x86 binary from this
`.jar`, and then zips this binary along with a short bootstrap shell
script. Then, `sam local` is started, which creates a Docker container
running Amazon's Lambda execution environment with
`./native-template.yml` as its AWS SAM template file. This is a
Docker container that runs this template file references the zip file
made in the first step.

# Deploy an unoptimized, JVM version to AWS

```
make deploy
```

# Deploy an optimized, native version to AWS

```
make deploy-native
```

# As used from AWS

```
sudo service docker start && time ((cd ~/menard && git pull && git log -1 && lein install) && (cd ~/nlquiz/lambda/ && git pull &&  make clean deploy-native))
```
