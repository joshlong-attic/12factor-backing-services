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
        return args ->
                Arrays.asList("Phil,Webb", "Josh,Long", "Dave,Syer", "Spencer,Gibb").stream()
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