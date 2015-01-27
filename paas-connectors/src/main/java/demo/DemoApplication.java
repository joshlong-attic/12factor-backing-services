package demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.sql.DataSource;
import java.util.Arrays;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    @Profile("cloud")
    DataSource dataSource(@Value("${cloud.services.postgresql-db.connection.jdbcurl}") String jdbcUrl) {
        try {
            return new SimpleDriverDataSource(
                org.postgresql.Driver.class.newInstance() , jdbcUrl);
        }
        catch (Exception e) {
            throw new RuntimeException(e) ;
        }
    }

    @Bean
    CommandLineRunner seed(ReservationRepository rr) {
        return args -> {

            rr.deleteAll();

            Arrays.asList("Phil,Webb", "Josh,Long", "Dave,Syer", "Spencer,Gibb").stream()
                    .map(s -> s.split(","))
                    .forEach(namePair -> rr.save(new Reservation(namePair[0], namePair[1])));

        };
    }

    @Bean
    CommandLineRunner verifyEnv(
            DataSourceProperties dsp,
            @Value("${cloud.services.postgresql-db.connection.jdbcurl:}") String jdbcUrl) {
        return args -> System.out.println("the JDBC URL=" + jdbcUrl + ". the DS URL=" + dsp.getUrl() + ".");
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

    public Long getId() {
        return id;
    }

    private String firstName, lastName;

    Reservation() {
    }

    public Reservation(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}