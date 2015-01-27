package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@RestController
class GreetingsService {

    @RequestMapping("/hi/{name}")
    String hi(@PathVariable String name) {
        String msg = "Hello " + (StringUtils.hasText(name) ? ' ' + name.trim() + ' ' : "") + "!";
        return msg;
    }

    @RequestMapping("/hi")
    String hi() {
        return this.hi("");
    }
}