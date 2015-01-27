-	CF can manage services. it does so using th eservice-broker API which exposes to the cloud controller a set of callbacks for interacting with the service, and provisioning and managing instances of that service.
-	what if the service u want isn't already there? 2 approaches: [build ur own SB](http://docs.cloudfoundry.org/services/managing-service-brokers.html#register-broker), or use User Provided Services
-	u can create See seth greenberg's Spring Boot example for writing a custom SB

12-Factor App-Style Backing Services with Spring and Cloud Foundry
==================================================================

The [12 Factor App Manifesto](http://12factor.net/) talks about [backing services](http://12factor.net/backing-services) at length. A backing service is, basically, any networked attached service that your application consumes to do its job. This might be a MongoDB instance, PostgreSQL database, a binary store like Amazon's S3, metrics-gathering services like New Relic, a RabbitMQ or ActiveMQ message queue, a Memcached or Redis-based cache, an FTP service, an email service or indeed anything else. The distinction is not so much *what* the service is so much as *how it's exposed and consumed in an application*. To the app, both are attached resources, accessed via a URL or other locator/credentials stored in the configuration.

We looked at how to use [*configuration*](https://spring.io/blog/2015/01/13/configuring-it-all-out-or-12-factor-app-style-configuration-with-spring) to extricate magic strings like locators and credentials from the application code and externalize it. We also [looked at the use of service registries](https://spring.io/blog/2015/01/20/microservice-registration-and-discovery-with-spring-cloud-and-netflix-s-eureka) to keep a sort of living *phone book* of microservices in a dynamic (typically cloud) environment.

In this post, we'll look at the way [Platform-as-a-Service (PaaS) environments](http://en.wikipedia.org/wiki/Platform_as_a_service) like [Cloud Foundry](http://cloudfoundry.org) or Heroku typically expose *backing services*, and look at ways to consume those services from inside a Spring application. For our examples, we'll use Cloud Foundry because it's open-source and easy to run in any datacenter or hosted, though most of this is pretty straightforward to apply on Heroku.

A PaaS like Cloud Foundry exposes backing services as operating system process-local environment variables. Environment variables are convenient because they work for all languages and runtimes, and they're easy to change from one environment to another. This is *way* simpler than trying to get JNDI up and running on a local machine, for example, and promotes portable builds. I intend to look at backing services specifically through the lens of current-day Cloud Foundry in this post. Keep in mind though the approach is specifically intended to promote portable builds *outside* of a cloud environment.

The runtime under the next version of Cloud Foundry (hitherto referred to as *Diego* in the press) is Docker-first and Docker-native. Docker, of course, makes it easy to containerize applications, and the interface between the containerized application and the outside world is kept intentinally minimal again to promote portable applications. One of the key inputs in a Docker image is, you guessed it, environment variables! Our pal Chris Richardson has done a few nice posts on both packaging and building Spring Boot-based Docker images and on standing up backing-services in

A Simple Spring Boot Application that talks to a JDBC `DataSource`
------------------------------------------------------------------

Here's a simple Spring Boot application that inserts and exposes a few records from a `DataSource` bean which Spring Boot will automatically create for us because we have the H2 embedded database driver on the CLASSPATH. If Spring Boot does not detect a bean of type `javax.sql.DataSource` and it *does* detect an embedded database driver (H2, Derby, HSQL), it will automatically create an embedded `javax.sql.DataSource` bean. This example uses JPA to map records to the database. Here are the Maven dependencies:

| Group ID                   | Artifact ID                     |
|----------------------------|---------------------------------|
| `com.h2database`           | `h2`                            |
| `org.springframework.boot` | `spring-boot-starter-data-jpa`  |
| `org.springframework.boot` | `spring-boot-starter-data-rest` |
| `org.springframework.boot` | `spring-boot-starter-test`      |
| `org.springframework.boot` | `spring-boot-starter-actuator`  |

```java
package demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Arrays;

@SpringBootApplication
public class DemoApplication {

		public static void main(String[] args) {
			SpringApplication.run(DemoApplication.class, args);
		}

		@Bean
		CommandLineRunner seed(ReservationRepository rr) {
			return args -> Arrays.asList("Phil,Webb", "Josh,Long", "Dave,Syer", "Spencer,Gibb").stream()
				.map(s -> s.split(","))
				.forEach(namePair -> rr.save(new Reservation(namePair[0], namePair[1])));
		}
}

@RepositoryRestResource
interface ReservationRepository extends JpaRepository<Reservation, Long> {
}

@Entity
class Reservation {

		@Id
		@GeneratedValue
		private Long id;

		private String firstName, lastName;

		Reservation() {
		}

		public Reservation(String firstName, String lastName) {
			this.firstName = firstName;
			this.lastName = lastName;
		}

		public Long getId() {
			return id;
		}

		public String getFirstName() {
			return firstName;
		}

		public String getLastName() {
			return lastName;
		}
}
```

Creating and Binding Backing Services in Cloud Foundry
------------------------------------------------------

The application works locally, but let's turn now to making it run on Cloud Foundry.

Cloud Foundry comes in many flavors. Like Heroku, you can run it hosted in an AWS-based flavor that's [available as Pivotal Web Services](http://run.pivotal.io). You can get a free trial account. You can use Pivotal's shrink-wrapped variant, [Pivotal Cloud Foundry](http://www.pivotal.io/platform-as-a-service/pivotal-cf), and run that in your datacentre, if you like. Alternatively, you can use the open-source bits and run that, too. Or use any of the numerous other implementations from the likes of [IBM](https://console.ng.bluemix.net/) and [HP](http://www8.hp.com/us/en/cloud/helion-devplatform-overview.html). At any rate, you'll end up with a PaaS that behaves basically the same.

Adding backing-services is a declarative function: simply *create* a service and then bind it. Cloud Foundry features a *marketplace* command: `cf marketplace`. For the 80% cases, you should be able to pick from among the options in the `cf marketplace` output. Once you've picked a service, create an instance of it, like this:

```bash
#!/bin/sh

cf create-service elephantsql turtle postgresql-db
```

Note specifically that `postgresql-db` is the backing service name. With that in hand, you can *bind* the service to your deployed application. You can use the `cf` CLI, or simply declare a backing service dependency in your application's `manifest.yml` file.

Here's the basic `manifest.yml` for all our samples. The `name` and `host` change from one example to another, but basically all of the examples will demonstrate using this newly created `postgresql-db` service.

```yml
---
applications:
- name: simple-backing-services
	memory: 512M
	instances: 1
	host: simple-backing-services-${random-word}
	domain: cfapps.io
	path: target/simple.jar
	services:
		- postgresql-db

```

Now, let's look at a few different ways to consume the backing service and look at some of their strengths and weaknesses.

We'll use [Spring Boot](http://start.spring.io), at a minimum, for all of these examples. Spring Boot offers the Spring Boot Actuator module. It provides very helpful information about the application - metrics, environment dumps, a listing of all the defined beans, etc. We'll use some of these endpoints to get insight into the environment variables and system properties exposed when running a Spring application, and to understand things like the profile under which Spring Boot is running. Add the Spring Boot Actuator in your Maven or Gradle build. The `groupId` is `org.springframework.boot` and the `artifactId` is `spring-boot-starter-actuator`. You don't need to specify the version if you're using the a Spring Boot generated from [`start.spring.io`](http://start.spring.io) or the `spring init` CLI command.

Cloud Foundry's Auto-Reconfiguration
------------------------------------

The [Cloud Foundry Java buildpack](https://github.com/cloudfoundry/java-buildpack/blob/master/docs/framework-spring_auto_reconfiguration.md) does *auto reconfiguration* for you. [From the docs](https://github.com/cloudfoundry/java-buildpack-auto-reconfiguration):

> Auto-reconfiguration consists of three parts. First, it adds the `cloud` profile to Spring's list of active profiles. Second it exposes all of the properties contributed by Cloud Foundry as a `PropertySource` in the `ApplicationContext`. Finally it re-writes the bean defintitions of various types to connect automatically with services bound to the application. The types that are rewritten are as follows:
>
> | Bean Type                                                          | Service Type                                         |
> |--------------------------------------------------------------------|------------------------------------------------------|
> | `javax.sql.DataSource`                                             | Relational Data Services (e.g. ClearDB, ElephantSQL) |
> | `org.springframework.amqp.rabbit.connection.ConnectionFactory`     | RabbitMQ Service (e.g. CloudAMQP)                    |
> | `org.springframework.data.mongodb.MongoDbFactory`                  | Mongo Service (e.g. MongoLab)                        |
> | `org.springframework.data.redis.connection.RedisConnectionFactory` | Redis Service (e.g. Redis Cloud)                     |
> | `org.springframework.orm.hibernate3.AbstractSessionFactoryBean`    | Relational Data Services (e.g. ClearDB, ElephantSQL) |
> | `org.springframework.orm.hibernate4.LocalSessionFactoryBean`       | Relational Data Services (e.g. ClearDB, ElephantSQL) |
> | `org.springframework.orm.jpa.AbstractEntityManagerFactoryBean`     | Relational Data Services (e.g. ClearDB, ElephantSQL) |

The effect, for you, is that your application runs locally against H2 will just work when run on Cloud Foundry, in the `cloud` profile and when the application is bound to a RDBMS backing-service like PostgreSQL.

Spring Boot's `VCAP` support
============================

-	buildpack adds `cloud.*` properties.
-	Spring Boot handles `vcap.*` properties

Using Spring Profiles
=====================

-	show how u can split config into multiple files (application.yml, application-cloud.yml) and then use in-property file references to ref vcap.* or cloud.* properties.

Using Java configuration `@Bean`s
=================================

-	show how u can exercise complete control using proper `@Bean`-definitions

Using Spring Cloud PaaS connectors
==================================

-	show how t
