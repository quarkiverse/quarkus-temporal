<div align="center">
<img src="https://raw.githubusercontent.com/quarkiverse/quarkus-temporal/master/docs/modules/ROOT/assets/images/quarkus.svg" width="67" height="70" ><img src="https://raw.githubusercontent.com/quarkiverse/quarkus-temporal/master/docs/modules/ROOT/assets/images/plus-sign.svg" height="70" ><img src="https://raw.githubusercontent.com/quarkiverse/quarkus-temporal/master/docs/modules/ROOT/assets/images/temporal_logo.svg" height="70" >

# Quarkus Temporal
</div>
<br>

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.temporal/quarkus-temporal?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.temporal/quarkus-temporal)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](https://opensource.org/licenses/Apache-2.0)
[![Build](https://github.com/quarkiverse/quarkus-temporal/actions/workflows/build.yml/badge.svg)](https://github.com/quarkiverse/quarkus-temporal/actions/workflows/build.yml)

A Quarkus extension that lets you utilize [Temporal](https://temporal.io/), orchestrating both mission-critical and mainstream workloads.

Using this service has some obvious advantages including but not limited to:

* Temporal captures the complete state of your functions (variables, threads, blocking calls) so you get the benefits of a state machine, without maintaining complex state machine code.
* Temporal allows you to define exponential retry policies for Activities, so you don’t have to write retry logic into your application code. And your retry duration can be as long as needed, even months.
* Set timers to wait for days, weeks, or months, knowing that an outage or server restart during the wait period won’t prevent your workflow from executing.
* Temporal delivers an ability to schedule a workflow (much like a cron job) and then pause, re-start, and stop them as needed.
* Temporal allows you to simply code for durable execution.
* Use external sources -- including human actions -- that interact seamlessly with Workflows.
* Temporal allows you to inspect, replay, and rewind every Workflow execution, step by step. 

## Getting started

Read the full [Quarkus Temporal documentation](https://docs.quarkiverse.io/quarkus-temporal/dev/index.html) or check out the [Quarkus Insights](https://youtu.be/XICZxuaeYwI) video explaining what Temporal is and how to use it!

### Installation

Create a new temporal project (with a base temporal starter code):

- With [code.quarkus.io](https://code.quarkus.io/?a=temporal-bowl&j=17&e=io.quarkiverse.temporal%3Aquarkus-temporal)
- With the [Quarkus CLI](https://quarkus.io/guides/cli-tooling):

```bash
quarkus create app temporal-app -x=io.quarkiverse.temporal:quarkus-temporal
```
Or add to you pom.xml directly:

```xml
<dependency>
    <groupId>io.quarkiverse.temporal</groupId>
    <artifactId>quarkus-temporal</artifactId>
    <version>{project-version}</version>
</dependency>
```

> [!IMPORTANT]  
> This extension is not supported in GraalVM Native Image mode due to complexities Temporal's use of `grpc-netty-shaded`.
> Netty 5 will apparently fix the issue so it possibly might have to wait until that release.

## Demonstration Use Case

[Quarkus Temporal Petstore](https://github.com/melloware/quarkus-temporal-petstore) is a comprehensive demonstration using Quarkus and Temporal. It simulates placing a new order on your Petstore website and fulfilling the order using a microservice architecture.

## 🧑‍💻 Contributing

- Contribution is the best way to support and get involved in community!
- Please, consult our [Code of Conduct](./CODE_OF_CONDUCT.md) policies for interacting in our community.
- Contributions to `quarkus-temporal` Please check our [CONTRIBUTING.md](./CONTRIBUTING.md)

### If you have any idea or question 🤷

- [Ask a question](https://github.com/quarkiverse/quarkus-temporal/discussions)
- [Raise an issue](https://github.com/quarkiverse/quarkus-temporal/issues)
- [Feature request](https://github.com/quarkiverse/quarkus-temporal/issues)
- [Code submission](https://github.com/quarkiverse/quarkus-temporal/pulls)

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):
<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="http://loic.pandore2015.fr"><img src="https://avatars.githubusercontent.com/u/10419172?v=4?s=100" width="100px;" alt="Loïc Hermann"/><br /><sub><b>Loïc Hermann</b></sub></a><br /><a href="#maintenance-rmanibus" title="Maintenance">🚧</a> <a href="https://github.com/quarkiverse/quarkus-temporal/commits?author=rmanibus" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://melloware.com"><img src="https://avatars.githubusercontent.com/u/4399574?v=4?s=100" width="100px;" alt="Melloware"/><br /><sub><b>Melloware</b></sub></a><br /><a href="#maintenance-melloware" title="Maintenance">🚧</a> <a href="https://github.com/quarkiverse/quarkus-temporal/commits?author=melloware" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/ggrebert"><img src="https://avatars.githubusercontent.com/u/1737774?v=4?s=100" width="100px;" alt="Geoffrey GREBERT"/><br /><sub><b>Geoffrey GREBERT</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-temporal/commits?author=ggrebert" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/tmulle"><img src="https://avatars.githubusercontent.com/u/5183186?v=4?s=100" width="100px;" alt="tmulle"/><br /><sub><b>tmulle</b></sub></a><br /><a href="#ideas-tmulle" title="Ideas, Planning, & Feedback">🤔</a> <a href="https://github.com/quarkiverse/quarkus-temporal/commits?author=tmulle" title="Tests">⚠️</a></td>
      <td align="center" valign="top" width="14.28%"><a href="http://shrikanth.in"><img src="https://avatars.githubusercontent.com/u/1776590?v=4?s=100" width="100px;" alt="Shrikanth"/><br /><sub><b>Shrikanth</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-temporal/commits?author=shrikanthkr" title="Tests">⚠️</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://teedjay.github.io/"><img src="https://avatars.githubusercontent.com/u/1426570?v=4?s=100" width="100px;" alt="Thore Johnsen"/><br /><sub><b>Thore Johnsen</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-temporal/issues?q=author%3Ateedjay" title="Bug reports">🐛</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->
