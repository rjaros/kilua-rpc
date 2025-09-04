This is a sample project demonstrating usage of Kilua RPC services from the Typescript client.

### Backend

* `./gradlew :examples:typescript-ktor:ktor-server:jvmRun` - Runs the backend Ktor server on port 8080.

### Frontend

* `./gradlew :examples:typescript-ktor:ktor-server:jsBrowserDevelopmentLibraryDistribution` - Build Kotlin/JS library files and Typescript definitions.
* `cd examples/typescript-ktor/website` - Change directory to the frontend project.
* `npm install` - Install NPM dependencies.
* `vite` - Start the Vite development server on port 5173.
