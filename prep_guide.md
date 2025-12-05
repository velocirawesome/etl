# Preparation Guide: Data Extraction Management (DEM) Microservice

This guide provides an outline of the technical challenge you will be completing during your interview session. The challenge is designed to assess your ability to build a robust, production-ready microservice.

## Challenge Objective

You are tasked with building a Data Extraction Management (DEM) microservice that implements a complete ETL (Extract, Transform, Load) pipeline for master data.

### Key Technical Requirements:

- **Technology Stack:** You must use either Java or Golang (Go) for the backend implementation, utilizing a relevant framework (e.g., Spring/Quarkus for Java, Gin/Echo for Go).
- **Environment:** You will be coding live in your local IDE (VS Code, IntelliJ, etc.). We encourage you to use any AI assistant tools that you would like to. However, questions will be asked on what the AI is suggesting to use.
- **Duration:** The challenge and discussion will take approximately 60 minutes.

## Core Technologies and Setup

Please ensure you have the following ready to go **before** the session starts:

| Technology Focus Area | Requirements |
|----------------------|--------------|
| **Web Framework/Engine** | Routing and API Controllers ready. |
| **Database & Persistence** | Using a local in-memory or file-based database (e.g., SQLite, H2, or similar) for dynamic table creation and data insertion. |
| **Schema & JSON Handling** | Libraries to parse the JSON schema and dynamically navigate the external JSON response. |

## Key Expectations & Focus Areas

Your solution will be evaluated not just on its functionality, but on its design, code quality, and robustness in handling the data lifecycle.

### 1. The ETL Pipeline Breakdown

| Phase | Technical Focus |
|-------|----------------|
| **E - Extraction** | Robust external API consumption. Handling HTTP errors and connection issues gracefully. |
| **T - Transformation** | **Crucial:** Implementing the Transformation Logic. This step requires transforming the raw data, filtering out unneeded information, and mapping it to the target entity structure. |
| **L - Loading** | **Crucial:** Persistence logic. The service must be able to load the transformed entity into the target database table. |

### 2. Microservice Management

You're going to be developing three endpoints for managing the ETL job:

- **POST /etl/run**: Triggers the asynchronous execution of the ETL pipeline.
- **GET /etl/status**: Returns the current status of the running or last completed job (e.g., *RUNNING*, *SUCCESS*, *FAILED*).
- **GET /country**: Returns all successfully loaded data for the Country entity.

### 3. Non-Functional Requirements (Robustness)

- **Transactional Control:** Ensure the Loading phase (DB insertion) is handled with attention to data integrity. If a batch of inserts fails, the insertion of that batch should ideally be isolated or rolled back.
- **Observability & Error Handling:** Demonstrate basic observability by logging key operational metrics (e.g., job start/end, processing counts) and implement general error handling to manage API, network, or data transformation failures without crashing the service.
- **Code Clarity:** Use appropriate module or package structure (Controller/Router, Service/Logic, Persistence/Data Access).

## Tips for Success

The following steps should be completed **before** the interview begins:

1. **Project Setup:** Have a skeleton project initialized in your chosen language (Java or Go) and framework.
2. **IDE Check:** Ensure your IDE (IntelliJ, VS Code, etc.) is fully functional, with the correct language environment/SDK, and that you are comfortable sharing your screen and coding live.
3. **Dependency Check:** If using external libraries for YAML parsing, HTTP calls, or JSON pathing, ensure these dependencies are already declared and resolving in your project configuration (e.g., pom.xml, build.gradle, or go.mod).
4. **Database Ready:** Have your local in-memory database (e.g., H2 or SQLite setup) or connection details configured.
5. **Modular Design:** Design clear interfaces and separate concerns.
6. **Think Out Loud:** Explain your design choices and assumptions before you start coding. We want to hear your thought process.
7. **Prioritize:** Focus on the core requirements. If time is tight, communicate your plan for the remaining steps.
8. **Use Your Tools:** Leverage your IDE's features (debugging, refactoring), any AI assistant tools you would like to, and your browser's access to official documentation as you normally would.

We look forward to a collaborative session and seeing your approach to this challenge!
