package demo;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class HelloResource {

    class Hello {
        public String name;

        Hello(String name) {
            this.name = name;
        }

        Hello() {

        }
    }

    @GET
    @Path("/hello")
    public Hello hello() {
        return new Hello("Bahubali");
    }

}
