# Smart IoT Dashboard

[![Java](https://img.shields.io/badge/Java-17-orange?logo=java)](https://www.java.com/)
[![Keycloak](https://img.shields.io/badge/Keycloak-IAM-purple?logo=keycloak&logoColor=white)](https://www.keycloak.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.0-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue?logo=react)](https://reactjs.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-Event_Streaming-black?logo=apachekafka)](https://kafka.apache.org/)
[![Apache Flink](https://img.shields.io/badge/Apache_Flink-Stream_Processing-e6526f?logo=apacheflink)](https://flink.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-Cache-red?logo=redis&logoColor=white)](https://redis.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-Messaging-FF6600?logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://www.docker.com/)

> **Cloud-Native, Event-Driven IoT Platform for Real-Time Telemetry Processing & Device Management.**

Smart IoT Dashboard is a scalable IoT platform built on a microservices architecture. It ensures secure device connectivity, real-time telemetry stream processing, and state management via the "Digital Twin" concept.

The project demonstrates a **Modern Data Engineering** approach: moving away from monoliths to loosely coupled services (Kafka), using time-series data (InfluxDB), and Complex Event Processing (Flink CEP) instead of basic CRUD operations.

---

## 🏗 Architecture

The system is built on an **Event-Driven Architecture**. Devices and services are decoupled and communicate primarily through message brokers.

```mermaid
graph TD
    subgraph Edge_Layer [IoT Edge Layer]
        Device[Smart Device / Simulator] -- MQTT --> Mosquitto[Mosquitto Broker]
        Device -- HTTP Claiming --> APIGateway
        
        User[User / React Dashboard] -- 1. Login --> Keycloak[Keycloak IAM]
        User -- 2. HTTP/WS (Secure) --> APIGateway[User Device Service]
    end

    subgraph Streaming_Core [Streaming Core]
        Mosquitto -. Auth / ACL Check .-> APIGateway
        Mosquitto -- MQTT --> Bridge[MQTT-Kafka Bridge]
        Bridge -- Telemetry Raw --> Kafka
        Kafka -- Telemetry Raw --> DPS[Data Processing Service / Flink]
        DPS -- Telemetry Processed --> Kafka
        DPS -- Alerts --> Kafka
    end

    subgraph Backend_Infra [Backend Infrastructure]
        APIGateway -- TTL Tokens --> Redis[(Redis)]
        APIGateway -- WS Sync --> RabbitMQ{RabbitMQ}
        Kafka -- Telemetry Processed --> APIGateway
        Kafka -- Alerts --> APIGateway
    end

    subgraph Storage_Layer [Storage & Persistence]
        DPS -- Influx Line Protocol --> InfluxDB[(InfluxDB v2)]
        APIGateway -- JPA/JDBC --> Postgres[(PostgreSQL)]
        APIGateway -. Validate JWT .-> Keycloak
        APIGateway -- Query History --> InfluxDB
    end

    style Device fill:#dbeafe,stroke:#333,stroke-width:2px,color:#000
    style DPS fill:#dbeafe,stroke:#333,stroke-width:2px,color:#000
    
    style Kafka fill:#1f2937,stroke:#fff,stroke-width:2px,color:#fff

    style Keycloak fill:#e9d5ff,stroke:#333,stroke-width:2px,color:#000
    style Redis fill:#fee2e2,stroke:#333,stroke-width:2px,color:#000
    style RabbitMQ fill:#ffedd5,stroke:#333,stroke-width:2px,color:#000
```

## 🔌 Key Features

* ✅ **Event-Driven Microservices:** Complete decoupling of components. Apache Kafka acts as the "central nervous system" and backpressure buffer, ensuring high throughput and fault tolerance.
* ✅ **Complex Event Processing (CEP):** **Apache Flink** is used for on-the-fly anomaly detection (e.g., "Temperature dropping while Heater is ON").
* ✅ **Scalable WebSockets (RabbitMQ):** Solved the "Split-Brain" problem for distributed WebSocket sessions. **RabbitMQ** is used as a STOMP Broker Relay to broadcast updates across multiple backend instances.
* ✅ **Secure Provisioning (Redis):** Short-lived **Redis** tokens (TTL 5 min) are used for the secure PIN-code device claiming flow, preventing replay attacks.
* ✅ **Secure Provisioning:** Implemented a secure device claiming protocol via **PIN-code** (similar to Smart TV login). Mosquitto uses a sidecar plugin to delegate Auth/ACL checks to the Backend via a **Custom Security Filter**.
* ✅ **Hybrid Persistence:**
    * **PostgreSQL:** Stores metadata, users, alerts, audit logs, and device commands.
    * **InfluxDB:** Stores high-frequency time-series telemetry data.

## Demo

### 1. Real-Time Control & Telemetry
![Control Demo](control.gif)

## 🚀 Tech Stack

| Domain | Technology | Key Libraries & Details |
| :--- | :--- | :--- |
| **Backend** | **Java 17, Spring Boot 3** | Spring Security, OAuth2 Resource Server, JPA, WebSocket (STOMP), Flyway (Migration). |
| **Security** | **Keycloak** | Centralized Identity & Access Management (IAM), OAuth2/OIDC provider. |
| **Cache & Msg** | **Redis, RabbitMQ** | **Redis** for TTL tokens. **RabbitMQ** for internal WebSocket broadcasting. |
| **Streaming** | **Apache Kafka** | Used as the central Event Backbone and backpressure buffer. |
| **Processing** | **Apache Flink** | Complex Event Processing (CEP) for real-time anomaly detection and state management. |
| **IoT Edge** | **Eclipse Mosquitto** | MQTT Broker configured with a dynamic Auth Plugin delegating security checks to the Backend API. |
| **Frontend** | **React 18** | TypeScript, Material UI (MUI) for components, **Recharts** for real-time telemetry visualization. |
| **Persistence** | **PostgreSQL, InfluxDB** | **PostgreSQL** for relational data (users, devices, logs).<br>**InfluxDB v2** for high-speed time-series telemetry (Flux language). |


## 🔮 Future Improvements (Roadmap)

The project architecture is designed for growth. The current backlog focuses on scalability validation and infrastructure hardening:

* [ ] **High-Load Stress Testing:** Implementation of a dedicated Python script (`stress_test.py`) to simulate **1000+ concurrent devices**. Goal: Validate Kafka partition strategy and Flink backpressure handling under load.
* [ ] **UX Scalability & Testing:** Refinement of frontend pagination and list management logic to ensure smooth rendering for users with large device fleets (50+ active devices).
* [ ] **CI/CD Automation:** Implementation of **GitHub Actions** pipelines for automated building, testing, and pushing Docker images to a registry. This will ensure code quality and streamline the deployment process.
* [ ] **Infrastructure Evolution:** Migration from local Docker Compose to **Kubernetes** (Helm Charts) for production-grade deployment capabilities.
* [ ] **API Documentation:** Adoption of the **AsyncAPI** specification to formally document Kafka topics and event schemas, complementing the existing OpenAPI (Swagger) documentation.
* [ ] **Storage Modernization:** Strategic migration to **InfluxDB 3.0** (IOx engine). This will enable native **SQL support** and columnar storage (Apache Parquet) for deeper analytics without proprietary languages like Flux.
