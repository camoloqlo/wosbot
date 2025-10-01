package cl.camodev.wosbot.web.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to serve the index.html at the root path.
 * Required because @EnableWebMvc disables Spring Boot's default welcome page handling.
 */
@Controller
public class IndexController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
