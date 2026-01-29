# 🐕 Woof Detect

[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Python](https://img.shields.io/badge/Python-Backend-3776AB?logo=python&logoColor=white)](https://www.python.org/)
[![YOLOv8](https://img.shields.io/badge/YOLOv8-AI-00FFFF?logo=yolo&logoColor=black)](https://github.com/ultralytics/ultralytics)

**Woof Detect** is a mobile application for dog breed recognition powered by AI. The project combines an Android mobile app with a microservices-based backend and machine learning models to identify dog breeds from photos.

---

## 📋 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Development](#-development)
- [API Documentation](#-api-documentation)
- [Contributing](#-contributing)

---

## ✨ Features

- 📸 **Photo Recognition** - Upload or take a photo to identify dog breeds
- 🤖 **AI-Powered** - Uses YOLOv8 for accurate breed detection
- 📱 **Native Android App** - Built with Kotlin for optimal performance
- 🔐 **Authentication System** - Secure user authentication and authorization
- 💾 **Database Management** - Persistent storage for breeds and user data
- 🐳 **Dockerized Services** - Easy deployment with Docker Compose

---

## 🏗️ Architecture

The project follows a microservices architecture with the following components:

```
┌─────────────┐
│   Mobile    │
│   (Kotlin)  │
└──────┬──────┘
       │
       ├─────────────────────────────────────┐
       │                                     │
       ▼                                     ▼
┌─────────────┐                      ┌─────────────┐
│  Endpoints  │◄────────────────────►│    Auth     │
│   :8000     │                      │   :8010     │
└──────┬──────┘                      └─────────────┘
       │
       ├──────────────┬──────────────┐
       ▼              ▼              ▼
┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│  Database   │ │ Recognition │ │   AI Model  │
│   :8020     │ │   :8030     │ │   (YOLOv8)  │
└─────────────┘ └─────────────┘ └─────────────┘
```

### Services:

- **Endpoints** (`:8000`) - Main API gateway
- **Auth** (`:8010`) - User authentication and authorization
- **Database** (`:8020`) - Data persistence layer
- **Recognition** (`:8030`) - AI-powered breed recognition

---

## 🛠️ Tech Stack

### Mobile
- **Language:** Kotlin
- **Framework:** Android SDK
- **Build Tool:** Gradle

### Backend
- **Language:** Python
- **Framework:** Flask (assumed based on microservices pattern)
- **AI/ML:** YOLOv8 (Ultralytics)
- **Database:** SQLite

### DevOps
- **Containerization:** Docker & Docker Compose
- **Architecture:** Microservices

---

## 📁 Project Structure

```
dog_breed_mobile_application/
├── mobile/                 # Android mobile application
│   ├── app/               # Main application module
│   ├── build.gradle.kts   # Gradle build configuration
│   └── settings.gradle.kts
│
├── backend/               # Backend microservices
│   ├── auth/             # Authentication service
│   ├── database/         # Database service
│   │   ├── authServer.db
│   │   ├── raceDB.db
│   │   └── racesFolder/
│   ├── endpoints/        # API endpoints service
│   ├── recognition/      # AI recognition service
│   │   ├── models/       # ML models
│   │   ├── dog_photos/   # Photo storage
│   │   └── yolov8n.pt    # YOLOv8 model
│   └── CONTAINERIZATION.md
│
├── ai/                    # AI/ML development
│
├── docker-compose.yml     # Docker orchestration
└── README.md             # This file
```

---

## 🚀 Getting Started

### Prerequisites

- **Docker** and **Docker Compose** installed
- **Android Studio** (for mobile development)
- **Git**

### Quick Start with Docker

1. **Clone the repository:**
   ```bash
   git clone https://github.com/j-bisew/dog_breed_mobile_application.git
   cd dog_breed_mobile_application
   ```

2. **Start all services:**
   ```bash
docker-compose up --build -d
   ```

3. **Check service status:**
   ```bash
docker-compose ps
   ```

4. **View logs:**
   ```bash
docker-compose logs -f
   ```

5. **Stop services:**
   ```bash
docker-compose down
   ```

### Services URLs

Once running, the services are available at:

- **Endpoints API:** `http://localhost:8000`
- **Auth Service:** `http://localhost:8010`
- **Database Service:** `http://localhost:8020`
- **Recognition Service:** `http://localhost:8030`

---

## 💻 Development

### Backend Development

#### Option 1: Using Docker (Recommended)

```bash
# Build and run all services
docker-compose up --build

# Rebuild specific service
docker-compose up --build recognition

# View logs for specific service
docker-compose logs -f endpoints
```

#### Option 2: Local Development (Windows)

```bash
cd backend
./startServers.bat
```

This script will:
- Start all microservices in separate terminals
- Activate virtual environments automatically
- Run each service on its designated port

### Mobile Development

1. **Open project in Android Studio:**
   ```bash
   cd mobile
   # Open this directory in Android Studio
   ```

2. **Sync Gradle:**
   - Android Studio will automatically sync dependencies

3. **Configure backend endpoint:**
   - Update API endpoints in your app configuration to point to `http://localhost:8000` (or appropriate IP)

4. **Run the app:**
   - Connect an Android device or start an emulator
   - Click "Run" in Android Studio

---

## 📚 API Documentation

### Endpoints Service (`:8000`)

Main API gateway for mobile application requests.

### Auth Service (`:8010`)

Handles user authentication and authorization.

### Database Service (`:8020`)

Manages data persistence for:
- User accounts (`authServer.db`)
- Dog breed information (`raceDB.db`)
- Breed photos and metadata

### Recognition Service (`:8030`)

AI-powered breed recognition using YOLOv8.

**Volumes:**
- `./backend/recognition/models` - ML models
- `./backend/recognition/dog_photos` - Uploaded photos
- `./backend/recognition/yolov8n.pt` - YOLOv8 weights

---

## 🤝 Contributing

### Workflow Guidelines

1. **Branch Strategy**
   - Always work on feature branches
   - Create descriptive branch names (e.g., `feature/add-breed-filter`, `fix/auth-bug`)
   - Never push directly to `main`

2. **Commit Messages**
   - Write clear, descriptive commit messages
   - Use conventional commits format:
     - `feat:` for new features
     - `fix:` for bug fixes
     - `docs:` for documentation
     - `refactor:` for code refactoring
   - ❌ Avoid: `ok`, `update`, `change`
   - ✅ Good: `feat: add breed filtering to search`, `fix: resolve auth token expiration`

3. **Communication**
   - Notify the team when merging changes
   - Share significant updates in team channels
   - No formal code review required, but communication is essential

4. **AI Usage**
   - AI assistance is allowed for learning and small code snippets
   - Don't generate entire features with AI - this should be **our** work
   - Understanding the code you write is more important than speed

5. **File Management**
   - Remove `.gitkeep` files after adding actual content to folders

### Making Changes

1. **Create a branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes and commit:**
   ```bash
   git add .
   git commit -m "feat: add your feature description"
   ```

3. **Push to GitHub:**
   ```bash
   git push origin feature/your-feature-name
   ```

4. **Create a Pull Request:**
   - Go to GitHub and create a PR
   - Add a clear description of your changes
   - Notify the team

---

## 📄 License

This project is developed as a portfolio/CV project by the team.

---

## 👥 Authors

Team project - check contributors for individual contributions.

---

## 🎯 Project Goals

This project aims to be a high-quality portfolio piece that demonstrates:
- ✅ Full-stack mobile application development
- ✅ Microservices architecture
- ✅ AI/ML integration
- ✅ Modern DevOps practices (Docker, containerization)
- ✅ Clean code and professional workflows

**Each team member can proudly showcase this project in their CV and portfolio!** 🚀

---

## 📝 Notes

- See `backend/CONTAINERIZATION.md` for detailed Docker setup information
- Model files and databases are persisted through Docker volumes
- Services communicate via Docker's internal network

---

## 🐛 Troubleshooting

### Services won't start
```bash
# Check if ports are already in use
netstat -an | grep "8000\|8010\|8020\|8030"

# Remove containers and rebuild
docker-compose down -v
docker-compose up --build
```

### Mobile app can't connect to backend
- Ensure Docker services are running: `docker-compose ps`
- Use `10.0.2.2` instead of `localhost` on Android emulator
- Check firewall settings if testing on physical device

---

**Made with ❤️ by the Woof Detect Team**