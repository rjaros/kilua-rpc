import { PingServiceTs } from "kilua-rpc-examples-typescript-ktor-ktor-server";

var pingService = new PingServiceTs();
pingService.ping("Hello from TypeScript!").then((response: any) => {
    console.log("Response from server:", response);
}).catch((error: any) => {
    console.error("Error calling ping service:", error);
});
