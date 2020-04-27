package io.quarkus.grpc.server.devmode;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 4/28/20
 */
@Path("/test")
public class DevModeTestRestResource {

    @Inject
    DevModeTestInterceptor interceptor;

    @GET
    public String get() {
        return "testresponse";
    }

    @GET
    @Path("/interceptor-status")
    public String getInterceptorStatus() {
        return interceptor.getLastStatus();
    }
}
