package io.neo4flix.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class ApiErrorsTest {

    private MockMvc mvc;

    @RestController
    static class Endpoint {

        record Input(@NotBlank String value) {}

        @PostMapping("/input")
        public Input input(@Valid @RequestBody Input input) {
            return input;
        }

        @GetMapping("/number")
        public int number(@RequestParam int value) {
            return value;
        }

        @GetMapping(value = "/json", produces = MediaType.APPLICATION_JSON_VALUE)
        public Input json() {
            return new Input("ok");
        }

        @GetMapping("/failure")
        public void failure() {
            throw new IllegalStateException("private diagnostic");
        }
    }

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Endpoint())
            .setControllerAdvice(new ApiErrors())
            .build();
    }

    @Test
    void preservesMethodStatusAndAllowHeader() throws Exception {
        mvc.perform(put("/input"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", "POST"))
            .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void rejectsUnsupportedMedia() throws Exception {
        mvc.perform(post("/input").contentType("text/plain").content("hello"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.status").value(415));
        mvc.perform(get("/json").accept("application/xml")).andExpect(status().isNotAcceptable());
    }

    @Test
    void malformedAndMissingInputRemainClientErrors() throws Exception {
        mvc.perform(post("/input").contentType("application/json").content("{")).andExpect(
            status().isBadRequest()
        );
        mvc.perform(post("/input").contentType("application/json").content("{}")).andExpect(
            status().isBadRequest()
        );
        mvc.perform(get("/number")).andExpect(status().isBadRequest());
        mvc.perform(get("/number?value=wrong")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownRoutesReturn404() throws Exception {
        mvc.perform(get("/missing")).andExpect(status().isNotFound());
    }

    @Test
    void unexpectedErrorsRemainGeneric() throws Exception {
        mvc.perform(get("/failure"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Something went wrong. Please try again."));
    }
}
