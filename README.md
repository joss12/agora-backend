# 🛒 Agora — E-Commerce REST API

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-brightgreen?logo=springboot)
![MySQL](https://img.shields.io/badge/MySQL-Database-blue?logo=mysql)
![Redis](https://img.shields.io/badge/Redis-Cache-red?logo=redis)
![JWT](https://img.shields.io/badge/Authentication-JWT-purple)
![Swagger](https://img.shields.io/badge/API_Docs-Swagger_UI-green?logo=swagger)

**Agora** is a full-stack e-commerce application powered by a Java Spring Boot REST API and a Next.js frontend.

The backend provides authentication, product management, shopping cart functionality, order processing, payment integration, shipment tracking, and administrative operations.

The project demonstrates backend engineering practices, including secure API development, database migrations, transaction management, role-based authorization, and integration testing.

---

## 🌐 Live Application

| Resource | Link |
|----------|------|
| 🛒 Frontend | [Agora E-Commerce](https://agora-web-olive.vercel.app) |
| 📚 Swagger API Documentation | [Swagger UI](https://agora-backend-qqkr.onrender.com/swagger-ui) |
| ⚙️ Backend Deployment | [Render](https://agora-backend-qqkr.onrender.com) |
| 💻 Backend Repository | [GitHub](https://github.com/joss12/agora-backend) |
| 👨‍💻 Developer Portfolio | [Eddy Mouity](https://www.eddymouity.dev/en) |

> **Note:** The backend is hosted on Render. Requests may take longer when the service is waking from inactivity.

---

## 🚀 Features

### Authentication & Authorization

- User registration and login
- JWT-based authentication
- Role-based access control
- Protected API endpoints
- Password recovery and reset
- Spring Security integration

### Product Management

- Product creation and updates
- Product listing and details
- Product categorization
- Inventory management
- Product image management
- Cloudinary integration

### Shopping Cart

- Shopping cart management
- Add and remove products
- Update product quantities
- Shopping cart persistence

### Order Management

- Order creation
- Order details and history
- Order status management
- Administrative order processing
- Order cancellation
- Inventory restoration during cancellation

### Payments

- Payment processing integration
- Payment status tracking
- Demo payment simulation
- Payment attempt history
- Payment failure handling
- Refund state management

### Shipment Management

- Shipment creation
- Carrier information
- Tracking number management
- Shipment status tracking
- Delivery confirmation

### Administration

- Administrative API endpoints
- Product and inventory management
- Order management
- Shipment processing
- Protected administrative operations

---

## 🛠️ Technology Stack

| Category | Technology |
|----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| API | Spring Web MVC |
| Security | Spring Security |
| Authentication | JSON Web Tokens (JWT) |
| Database | MySQL |
| ORM | Spring Data JPA / Hibernate |
| Database Migrations | Flyway |
| Caching | Redis / Spring Cache |
| Image Storage | Cloudinary |
| Email | Spring Mail |
| API Documentation | Springdoc OpenAPI / Swagger UI |
| Validation | Jakarta Bean Validation |
| Testing | JUnit 5, Spring Boot Test, Testcontainers |
| Build Tool | Maven |
| Deployment | Render |

---

## 🏗️ Architecture

Agora follows a layered backend architecture.

```text
                  ┌───────────────────────┐
                  │     Next.js Client    │
                  │     Agora Frontend    │
                  └───────────┬───────────┘
                              │
                              │ HTTP / REST
                              ▼
                  ┌───────────────────────┐
                  │   Spring Boot API     │
                  │                       │
                  │    Controllers        │
                  └───────────┬───────────┘
                              │
                              ▼
                  ┌───────────────────────┐
                  │     Service Layer     │
                  │                       │
                  │   Business Logic      │
                  │   Order Processing    │
                  │   Payment Handling    │
                  └───────────┬───────────┘
                              │
                              ▼
                  ┌───────────────────────┐
                  │    Repository Layer   │
                  │                       │
                  │   Spring Data JPA     │
                  │      Hibernate        │
                  └───────────┬───────────┘
                              │
                              ▼
                  ┌───────────────────────┐
                  │         MySQL         │
                  └───────────────────────┘
```

### Supporting Services

- **Redis:** Caching infrastructure
- **Cloudinary:** Product image storage
- **Spring Security:** Authentication and authorization
- **Flyway:** Database schema migrations
- **Spring Mail:** Email functionality
- **Swagger:** Interactive API documentation

---

## 📚 API Documentation

Agora provides interactive API documentation using Swagger UI.

### Live Swagger UI

**[📚 Swagger API Documentation](https://agora-backend-qqkr.onrender.com/swagger-ui.html)**

Swagger UI allows developers to:

- Explore available REST endpoints
- Inspect request and response schemas
- Understand API parameters
- Review HTTP methods and response codes
- Test supported API operations

Protected endpoints require appropriate authentication and authorization.

---

## 🔐 Security

Agora uses Spring Security and JWT authentication to protect backend resources.

Security features include:

- JWT authentication
- Protected REST endpoints
- Role-based authorization
- Input validation
- Administrative access restrictions

Sensitive credentials and configuration values should be provided through environment variables rather than committed to the repository.

---

## 💳 Payment Processing

Agora supports payment-related operations, including payment state management and demo payment simulation.

Payment states include:

| Status | Description |
|--------|-------------|
| PAID | Payment completed successfully |
| FAILED | Payment attempt failed |
| REFUNDED | Payment recorded as refunded |

Demo payment simulation supports development and testing without requiring real financial transactions.

Demo payment functionality should only be enabled in explicitly authorized environments.

---

## 📦 Order & Shipment Workflow

A typical successful order follows this lifecycle:

```text
       PLACED
          │
          ▼
      PROCESSING
          │
          ▼
       SHIPPED
          │
          ▼
      DELIVERED
```

Orders can also enter a cancellation workflow when cancellation is permitted.

Shipment records include carrier information, tracking numbers, shipment dates, and delivery confirmation.

---

## 🗄️ Database Management

Agora uses MySQL for relational data storage.

Spring Data JPA and Hibernate provide persistence and entity management.

Flyway manages versioned database migrations, helping maintain consistent database schemas across environments.

---

## ⚙️ Local Development

### Prerequisites

Install the following:

- Java Development Kit (JDK) 21
- MySQL
- Git
- Docker (for container-based integration tests)

Redis may also be required depending on the active application configuration.

### 1. Clone the Repository

```bash
git clone https://github.com/joss12/agora-backend.git
```

```bash
cd agora-backend
```

### 2. Configure the Application

Configure the required database credentials, JWT secrets, and external service settings for your environment.

Do not commit passwords, API keys, or other secrets to Git.

Refer to the application's configuration files and deployment environment for the required settings.

### 3. Run the Application

On Linux or macOS:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
mvnw.cmd spring-boot:run
```

The application starts using the port configured for the active environment.

### 4. Build the Project

```bash
./mvnw clean package
```

### 5. Run Tests

```bash
./mvnw test
```

Integration tests using Testcontainers require a working Docker environment.

---

## 🧪 Testing

Agora includes automated backend tests using:

- JUnit 5
- Spring Boot Test
- Spring Security testing support
- Testcontainers
- MySQL integration testing

Testing covers important backend workflows such as authentication, payment handling, order management, and administrative operations.

The objective is to validate business rules and persistence behavior before deployment.

---

## ☁️ Deployment

The Agora backend is deployed on Render.

**API Documentation:**  
https://agora-backend-qqkr.onrender.com/swagger-ui.html

The frontend is deployed separately using Vercel.

**Frontend:**  
https://agora-web-olive.vercel.app

This separation allows the frontend and backend to be developed and deployed independently.

---

## 🎯 Project Goals

Agora was developed to demonstrate practical full-stack and backend engineering skills.

Key engineering objectives include:

- Designing maintainable REST APIs
- Implementing secure authentication
- Managing relational database transactions
- Building reliable order-processing workflows
- Integrating external services
- Writing automated integration tests
- Documenting APIs with OpenAPI
- Deploying applications to cloud infrastructure

---

## 👨‍💻 Author

### Eddy Mouity

**Backend / Full-Stack Developer**

Focused on building backend systems, REST APIs, and scalable web applications using Golang, Java, Spring Boot, Node.js, TypeScript, and modern web technologies.

- 🌐 **Portfolio:** [www.eddymouity.dev](https://www.eddymouity.dev/en)
- 💻 **GitHub:** [github.com/joss12](https://github.com/joss12)
- 🛒 **Live Project:** [Agora E-Commerce](https://agora-web-olive.vercel.app)
- 📚 **API Documentation:** [📚 Swagger API Documentation](https://agora-backend-qqkr.onrender.com/swagger-ui.html)

---

## ⭐ Support

If you find this project interesting, consider giving the repository a star.

**Built with Java and Spring Boot.**
