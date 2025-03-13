## Contributing at quarkus-temporal
We are beyond excited to see that you want to contribute! We would love to accept your contributions. Navigate through the following to understand more about contributing.

### Prerequisites
- Java 11
- Apache Maven

## Follow the Steps
- [Fork the repository](https://github.com/quarkiverse/quarkus-temporal/fork)

- Clone the project locally 

``` 
git clone https://github.com/quarkiverse/quarkus-temporal.git
``` 

- Create a new branch

```
git checkout -b <your branch_name>
```

After creating new branch start making your changes and once the changes done then push your changes and then create a ` pull_request`
- Push your changes

```
git push origin <your branch_name>
```

Now you have to wait for the review. The project maintainer will review your PR and once your PR got approve then they will merged it. If you want to support, please give a ⭐

### Things to remember before making changes

Before making any contribution make sure your local `main` keep up-to-date with upstream `main`. To do that type the following commands.

- First add upstream
```
git remote add upstream https://github.com/quarkiverse/quarkus-temporal.git
```
- Pull all changes from upstream
```
 git pull upstream main
```
- Keep your fork up-to-date
```
  git push origin main
```

## Updating Temporal version

Whenever updating temporal version we might need to update Netty/GRPC GraalVM substitutions and configurations.

Check if `grpc-netty-shaded` Netty version changed, later you would also need to check witch Quarkus version has
the right substitutions, we can do that by checking Quarkus BOM and checking the Netty version.

With the right Quarkus
version we can update the `update-netty-substitutions.sh` file and run it, that script will clone Quarkus repo and update
all Netty/GRPC code needed to run in native mode.