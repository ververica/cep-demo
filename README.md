# Dynamic CEP Demo

[![Build Status](https://github.com/ververica/cep-demo/actions/workflows/ci.yml/badge.svg)](https://github.com/ververica/cep-demo/actions/workflows/ci.yml)

A demo of Flink CEP with dynamic patterns.

Please follow the example [Dynamic Flink CEP](https://docs.ververica.com/vvc/get-started/flink-cep) blog post to deploy and run it on the [Ververica Cloud](https://www.ververica.com/cloud).

## Prerequisite

- Java JDK version 11 or above
- Maven build tool version 3.9.5 or above

## Building

To build and to create the jar file, run the following command:

```sh
mvn clean verify
```

The `JAR` artifact file will be created in path `target/ververica-cep-demo-<VERSION>.jar`.

## Acknowledgement

The initial version of this demo was provided in [`RealtimeCompute/ververica-cep-demo`](https://github.com/RealtimeCompute/ververica-cep-demo) repository.

## License

[Apache License 2.0](LICENSE.txt)
