# 🤖 XiaoZhi AI Assistant

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-purple?logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API%2026+-green?logo=android)](https://www.android.com/)
[![Gradle](https://img.shields.io/badge/Gradle-8.2.0-blue?logo=gradle)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Status](https://img.shields.io/badge/Status-Active-brightgreen)](README.md)

> An advanced AI-powered Android assistant with voice interaction, computer vision, and smart home integration.

XiaoZhi is a comprehensive Android application that combines voice recognition, face detection, smart home control, and intelligent task execution. Built with Jetpack Compose and leveraging cloud AI services through WebSocket communication.

## 📋 Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Requirements](#-requirements)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Build & Run](#-build--run)
- [Project Structure](#-project-structure)
- [Permissions](#-permissions)
- [Performance](#-performance)
- [Troubleshooting](#-troubleshooting)
- [Contributing](#-contributing)
- [License](#-license)

## ✨ Features

### 🎤 Voice & Audio
- **Hotword Detection** — Wake word detection using Picovoice Porcupine 4.0.0
- **Voice Activity Detection (VAD)** — Real-time audio processing with WebRTC VAD
- **Voice Command Integration** — Seamless voice command integration with Android system
- **Audio Visualization** — Real-time waveform display during music playback
- **Multi-format Audio Support** — Play songs from YouTube using yt-dlp binary

### 👁️ Computer Vision
- **Real-time Face Detection** — Face presence and eye tracking with ML Kit
- **IP Camera Streaming** — Support for viewing IP cameras (RTSP protocol)
- **Photo Capture & Analysis** — Capture and analyze photos with AI
- **Advanced Camera UI** — Quantum camera screen with face detection

### 🏠 Smart Home Integration
- **Home Assistant Support** — Connect and control Home Assistant instances
- **MQTT Protocol** — Message queue telemetry transport for IoT devices
- **WiFi Management** — Scan, connect, and manage wireless networks
- **Bluetooth Control** — Pair and manage Bluetooth devices
- **System Commands** — Control volume, brightness, airplane mode, and more

### 🎬 Media Playback
- **Video Streaming** — Play videos from various sources with ExoPlayer
- **Music Playback** — Stream music with real-time visualizations
- **Media Controls** — Play, pause, next, previous controls
- **Album Art Display** — Beautiful album artwork visualization

### 🧠 AI & MCP Protocol
- **Model Context Protocol (MCP) 2.0** — Standardized tool interface for AI models
- **50+ System Tools** — Built-in tools for system interaction
- **Real-time Processing** — Fast response times with coroutine-based architecture
- **Accessibility Services** — Deep system integration for advanced automation

### 📱 Advanced Features
- **Sensor Fusion** — Combine multiple sensor data for better context awareness
- **Overlay Mode** — Quick access UI triggered via voice commands
- **Google Sign-in** — Firebase authentication integration
- **File Management** — Browse, delete, and manage device files
- **Application Management** — List and launch installed applications
- **OTA Updates** — Over-the-air update support with device activation

## 🏗️ Architecture

### Project Structure

```
Tieuchi/
├── app/
│   ├── build.gradle                  # App configuration and dependencies
│   ├── src/main/
│   │   ├── AndroidManifest.xml       # Permissions, activities, services
│   │   ├── kotlin/com/xiaozhi/
│   │   │   ├── MainActivity.kt       # Main UI orchestration (90KB)
│   │   │   ├── McpHandler.kt         # MCP protocol implementation (60KB)
│   │   │   ├── MyApplication.kt      # App initialization
│   │   │   ├── WebSocketManager.kt   # Server communication
│   │   │   ├── XiaoZhiAudioManager.kt # Audio processing
│   │   │   ├── EyeManager.kt         # Face detection
│   │   │   ├── SystemController.kt   # System commands
│   │   │   ├── SensorFusion.kt       # Sensor data aggregation
│   │   │   ├── OverlayActivity.kt    # Floating overlay UI
│   │   │   ├── SettingsActivity.kt   # Configuration screen
│   │   │   ├── HotwordForegroundService.kt # Wake word detection
│   │   │   ├── MusicPlaybackService.kt     # Audio playback
│   │   │   ├── ai/                   # AI & ML components
│   │   │   ├── services/             # System services
│   │   │   ├── smarthome/            # Home Assistant integration
│   │   │   └── ui/                   # Jetpack Compose UI components
│   │   ├── res/                      # Resources (drawable, layout, values)
│   │   └── assets/                   # Asset files
│   ├── proguard-rules.pro            # Code obfuscation rules
│   └── google-services.json          # Firebase configuration
├── build.gradle                      # Root build configuration
├── settings.gradle                   # Project setup
└── gradle.properties                 # Gradle configuration
```

### Data Flow Diagram

```
┌─────────────────────┐
│   User Input        │
│  (Voice/Touch)      │
└──────────┬──────────┘
           │
           ▼
┌──────────────────────────┐
│  Audio Processing /      │
│  Gesture Recognition     │
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────┐
│  WebSocket Server        │
│ (Cloud AI Processing)    │
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────┐
│  MCP Handler             │
│ (Tool Execution)         │
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────┐
│  System Controllers      │
│ (Media, Camera, etc.)    │
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────┐
│  UI Update / Response    │
└──────────────────────────┘
```

### Runtime Architecture

- **WebSocket Communication** — Real-time bidirectional server communication
- **MCP Message Handler** — Processes tool calls and responses
- **Voice Pipeline** — Audio input → VAD → Server → AI Processing
- **Face Detection Loop** — Continuous monitoring for face presence
- **Auto-Listening** — Automatic audio capture when face detected
- **Sensor Fusion** — Accelerometer, gyro, and other sensor data aggregation

## 🛠️ Tech Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| **Language** | Kotlin 1.9.24 | Primary development language |
| **Framework** | Android SDK 34 | Target platform |
| **UI Framework** | Jetpack Compose | Modern declarative UI |
| **Build Tool** | Gradle 8.2.0 | Project automation |
| **Min SDK** | API 26 (Android 8.0) | Minimum supported version |
| **Target SDK** | API 34 (Android 14) | Target platform version |
| **Java** | 17 | JVM version |

### Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| **Jetpack Compose** | 2024.11.00 | Modern UI framework |
| **Media3 / ExoPlayer** | 1.3.1 | Video & audio playback |
| **CameraX** | 1.3.1 | Camera access and processing |
| **Room Database** | 2.6.1 | Local data persistence |
| **Firebase Auth** | BOM 34.12.0 | User authentication |
| **Retrofit 2** | 2.11.0 | HTTP client for API calls |
| **OkHttp3** | 4.12.0 | Network communication |
| **Gson** | 2.10.1 | JSON serialization |
| **Picovoice Porcupine** | 4.0.0 | Wake word detection |
| **WebRTC VAD** | 2.0.9 | Voice activity detection |
| **ML Kit** | 16.1.5 | Face detection |
| **TensorFlow Lite** | 2.13.0 | On-device ML inference |
| **Shizuku** | 13.1.5 | Advanced system integration |

## ⚙️ Requirements

### System Requirements

- **OS** — macOS, Windows, or Linux
- **Android Studio** — Latest stable version (Ladybug or later)
- **Android SDK** — API 34+
- **Java Development Kit** — JDK 17+
- **Gradle** — 8.2.0+ (included via wrapper)

### Development Prerequisites

```bash
# Verify Java installation
java -version

# Check Gradle wrapper
./gradlew --version

# Android SDK components
sdkmanager "platforms;android-34"
sdkmanager "build-tools;34.0.0"
```

## 📥 Installation

### Clone the Repository

```bash
# Clone the project
git clone https://github.com/Thanhdlpb/Tieuchi.git
cd Tieuchi

# Verify structure
ls -la
```

### Import to Android Studio

1. **Open Android Studio** → File → Open
2. **Select** the Tieuchi directory
3. **Wait** for Gradle sync to complete
4. **Configure** SDK paths if needed

### Using Android IDE (Termux)

```bash
# Install required packages
pkg install android-studio android-sdk

# Clone and navigate
git clone https://github.com/Thanhdlpb/Tieuchi.git
cd Tieuchi

# Open in Android IDE
android-studio .
```

## 🔐 Configuration

### 1. Firebase Setup

```bash
# Create local.properties if it doesn't exist
touch local.properties

# Add Firebase configuration
# Download google-services.json from Firebase Console
# Place it in: app/google-services.json
```

### 2. Picovoice API Key

```bash
# Edit local.properties
echo "PICOVOICE_ACCESS_KEY=your_actual_key_here" >> local.properties
```

### 3. WebSocket Configuration

- Open app settings
- Configure server URL: `wss://your-server.com/ws`
- Set authentication token
- Activate device with PIN code

### 4. Home Assistant (Optional)

```
Settings → Home Assistant
URL: https://your-ha-instance.local:8123
Token: Long-lived access token
```

## 🚀 Build & Run

### Debug Build

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Run with logging
./gradlew installDebug -d
```

### Release Build

```bash
# Create release APK (requires keystore)
./gradlew assembleRelease

# Create App Bundle (for Play Store)
./gradlew bundleRelease

# Sign with keystore
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  my-release-key.jks
```

### From Android Studio

1. **Build** → Build Bundle(s) / APK(s)
2. **Select** configuration (Debug/Release)
3. **Choose** target device/emulator
4. **Run** (Shift + F10)

### Testing

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests com.xiaozhi.AudioManagerTest

# Run with coverage
./gradlew test --coverage

# Run Android tests
./gradlew connectedAndroidTest
```

## 📂 Project Structure

### Source Code Organization

- **Main Components**
  - `MainActivity.kt` — Primary activity and UI orchestration
  - `McpHandler.kt` — Model Context Protocol implementation
  - `WebSocketManager.kt` — Server communication

- **Core Services**
  - `XiaoZhiAudioManager.kt` — Audio capture and processing
  - `EyeManager.kt` — Face detection and tracking
  - `SystemController.kt` — System-level commands

- **UI Components**
  - `ui/theme/` — Material3 theme configuration
  - `ui/screens/` — Compose UI screens
  - `OverlayActivity.kt` — Floating overlay interface

- **Integration Modules**
  - `ai/` — AI & ML components
  - `services/` — Android services
  - `smarthome/` — Home Assistant integration

### Resource Organization

- `res/drawable/` — Images and vector drawables
- `res/layout/` — XML layout files
- `res/values/` — Strings, colors, dimensions
- `res/xml/` — Configuration files

## 🔒 Permissions

### Runtime Permissions Required

| Permission | Purpose | Min API |
|-----------|---------|---------|
| `RECORD_AUDIO` | Voice capture and processing | 31 |
| `CAMERA` | Face detection and photo capture | 31 |
| `ACCESS_FINE_LOCATION` | WiFi and location services | 31 |
| `ACCESS_COARSE_LOCATION` | Approximate location | 31 |
| `BLUETOOTH_SCAN` | Bluetooth device discovery | 31 |
| `BLUETOOTH_CONNECT` | Bluetooth connections | 31 |
| `WRITE_SETTINGS` | System brightness and volume control | 23 |
| `POST_NOTIFICATIONS` | Notification display | 33 |

### Install-time Permissions

- `INTERNET` — Network communication
- `ACCESS_NETWORK_STATE` — Network status
- `MODIFY_AUDIO_SETTINGS` — Audio routing
- `NEARBY_WIFI_DEVICES` — WiFi scanning

### How to Request Permissions

```kotlin
// Check if permission is granted
if (ContextCompat.checkSelfPermission(
    this,
    Manifest.permission.RECORD_AUDIO
) != PackageManager.PERMISSION_GRANTED) {
    // Request permission
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.RECORD_AUDIO),
        REQUEST_AUDIO_PERMISSION
    )
}
```

## 📊 Performance

### Benchmarks (Modern Devices)

| Metric | Value | Notes |
|--------|-------|-------|
| **Startup Time** | < 3 seconds | Cold start |
| **Face Detection** | 30 FPS | Real-time |
| **Voice Response** | 1-2 seconds | With server |
| **Memory Usage** | 200-300 MB | Normal operation |
| **Battery Impact** | 5-10% per hour | With hotword detection |
| **Network Latency** | 50-200 ms | Typical |

### Optimization Tips

- Use release build for production
- Enable ProGuard obfuscation
- Minimize permissions requests
- Cache frequently accessed data
- Optimize image resources

## ❓ Troubleshooting

### Audio Issues

**Problem:** Voice recognition not working
- **Solution:** Check microphone permission, verify audio input not occupied

**Problem:** No audio output
- **Solution:** Verify speaker permissions, check volume settings

### Face Detection Issues

**Problem:** Face not detected
- **Solution:** Improve lighting, ensure camera is unobstructed

**Problem:** Continuous false positives
- **Solution:** Adjust detection sensitivity in settings

### WebSocket Connection

**Problem:** Server connection refused
- **Solution:** Verify server URL, check network connectivity

**Problem:** Frequent disconnections
- **Solution:** Check WiFi stability, increase timeout settings

### Build Issues

**Problem:** Gradle sync failed
- **Solution:** Clear `.gradle` folder, invalidate cache in Android Studio

**Problem:** APK build fails
- **Solution:** Check SDK version, verify keystore for release builds

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Quick Start

1. Fork the repository
2. Create feature branch: `git checkout -b feature/your-feature`
3. Commit changes: `git commit -m "feat: add new feature"`
4. Push to branch: `git push origin feature/your-feature`
5. Open Pull Request

### Code Style

- Follow Kotlin coding conventions
- Use descriptive naming
- Include documentation for public APIs
- Add comments for complex logic

## 📄 License

This project is licensed under the MIT License. See [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Dương Văn Thành**
- GitHub: [@Thanhdlpb](https://github.com/Thanhdlpb)
- Email: thanhdlpb@gmail.com

## 🔗 Links

- [GitHub Repository](https://github.com/Thanhdlpb/Tieuchi)
- [Issue Tracker](https://github.com/Thanhdlpb/Tieuchi/issues)
- [Discussions](https://github.com/Thanhdlpb/Tieuchi/discussions)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)

## 📝 Related Documents

- [README_VI.md](README_VI.md) — Vietnamese documentation
- [CONTRIBUTING.md](CONTRIBUTING.md) — Contribution guidelines
- [CHANGELOG.md](CHANGELOG.md) — Version history
- [SECURITY.md](SECURITY.md) — Security policy
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — Community guidelines

---

**Last Updated:** 2024-07-21  
**Version:** 1.0.0  
**Status:** ✅ Active Development
