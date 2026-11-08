package rs.ac.uns.sep.team15.bank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import rs.ac.uns.sep.team15.bank.common.exception.BadRequestException;
import rs.ac.uns.sep.team15.bank.common.exception.InternalServerErrorException;
import rs.ac.uns.sep.team15.bank.common.exception.NotFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WebSetupTests.TestController.class)
class WebSetupTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void permitsAnonymousReadsAndWritesWithoutCsrf() throws Exception {
        mvc.perform(get("/test/setup")).andExpect(status().isOk());
        mvc.perform(post("/test/setup").contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsLocalFrontendPreflight() throws Exception {
        for (int port : new int[]{5171, 5172, 5173}) {
            mvc.perform(options("/test/setup")
                            .header("Origin", "http://localhost:" + port)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Content-Type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:" + port));
        }
    }

    @Test
    void mapsCustomClientErrors() throws Exception {
        mvc.perform(get("/test/setup/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Resource not found"));
        mvc.perform(get("/test/setup/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void hidesInternalDetailsForCustomAndUnexpectedFailures() throws Exception {
        for (String path : new String[]{"server-error", "unexpected"}) {
            mvc.perform(get("/test/setup/" + path))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("Internal server error"));
        }
    }

    @Test
    void preservesFrameworkErrors() throws Exception {
        mvc.perform(get("/missing-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        mvc.perform(put("/test/setup"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value(405));
        mvc.perform(post("/test/setup").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @RestController
    static class TestController {
        @GetMapping("/test/setup")
        String get() {
            return "OK";
        }

        @PostMapping("/test/setup")
        String post(@RequestBody java.util.Map<String, Object> body) {
            return "OK";
        }

        @GetMapping("/test/setup/not-found")
        void notFound() {
            throw new NotFoundException("Resource not found");
        }

        @GetMapping("/test/setup/bad-request")
        void badRequest() {
            throw new BadRequestException("Invalid request");
        }

        @GetMapping("/test/setup/server-error")
        void serverError() {
            throw new InternalServerErrorException("Private database details");
        }

        @GetMapping("/test/setup/unexpected")
        void unexpected() {
            throw new IllegalStateException("Private implementation details");
        }
    }
}
