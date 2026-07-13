import {getPingService, PingServiceWrapper} from "kilua-rpc-examples-typescript-ktor-ktor-server";

var pingService = getPingService();
pingService.ping("Hello from TypeScript!").then((response: any) => {
    console.log("Response from server:", response);
}).catch((error: any) => {
    console.error("Error calling ping service:", error);
});

new PingServiceWrapper(pingService, (message: string) => {
    console.log("Received message from server:", message);
})
