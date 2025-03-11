# Consolidate-packages bal tool

Ballerina inherently supports microservices-style deployments, which are well-suited for microservice orchestration platforms like Kubernetes.
However, many enterprise users deploy on VMs or Docker, where managing each service as a separate process increases complexity and resource overhead.
This tool aims to provide a solution tailored to such scenarios by enabling consolidated deployments.

## Usage

### Create or update a consolidator package

Create a new package and add the `consolidate-packages` tool entry as shown in the below example into the `Ballerina.toml` with the services required to consolidate.
You can add/remove services as needed by updating the values provided for the `options.services` array. The tool will be automatically pulled during
the package build.

```toml
[package]
org = "myorg"
name = "consolidator"
version = "0.1.0"

[[tool.consolidate-packages]]
id = "consolidateSvc"
options.services = ["myorg/svc1", "myorg/svc2"]
```

#### Using the CLI tool
Alternatively, the `consolidate-packages` CLI tool can be installed to create and modify the consolidator package. This
is typically useful in CI/CD pipelines.

An example is shown below.

##### Installation

Execute the command below to pull the tool.

```bash
bal tool pull consolidate-packages
```

##### Creating a new consolidator package
```
$ bal consolidate-packages new --services myorg/svc1,myorg/svc2 [--name <package-name>]
```

##### Adding new services to an existing package
```
$ bal consolidate-packages add --services myorg/svc3,myorg/svc4
```

##### Removing services from an existing package
```
$ bal consolidate-packages remove --services myorg/svc2,myorg/svc3
```
