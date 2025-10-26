import { getPingService } from "kilua-rpc-examples-typescript-ktor-ktor-server";

var pingService = getPingService();
pingService.ping("Hello from TypeScript!").then((response: any) => {
    console.log("Response from server:", response);
}).catch((error: any) => {
    console.error("Error calling ping service:", error);
});
