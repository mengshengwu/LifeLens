# LifeLens

An Android app that uses on-device vision-language models (VLM) to help elderly users and children understand objects around them. Point your camera at an object, ask a question, and get a simple, safety-aware explanation — all processed locally on your device with no internet required for inference.

## Features

- **On-Device VLM Inference** — Powered by [Nexa SDK](https://nexa.ai) with OmniNeural-4B-mobile, a 4.9GB vision-language model optimized for Qualcomm NPUs. No cloud API needed.
- **Camera & Upload** — Capture photos with CameraX or upload from your gallery.
- **Audience Modes** — Toggle between Elderly and Child modes for age-appropriate language, safety tips, and explanations.
- **Streaming Answers** — Real-time token-by-token response as the model generates text.
- **Safety Detection** — Built-in hazard detection for common objects (knives, stoves, medicine) with audience-specific warnings.
- **Offline Knowledge Base** — Local knowledge lookup for common objects.
- **Quick Test** — One-tap test with a bundled image, no camera permission required.
- **Crash Diagnostics** — Global crash handler saves crash info for debugging on next launch.

## Architecture

```
MainActivity (Jetpack Compose UI)
├── Phase.INTRO  → Welcome screen
├── Phase.SETUP  → Model download + VLM initialization
└── Phase.READY  → Camera preview, image input, Q&A

NexaVlmClient (Nexa SDK wrapper)
├── init()                      → Load model with NPU/CPU plugin
└── generateWithImageStream()   → Image + prompt → streaming tokens

ModelManager
├── downloadModel()    → HuggingFace / external storage / bundled assets
├── missingFiles()     → Validate all 13 model files
└── isModelReady()     → Check download completeness

Tool Servers
├── SafetyServer       → Keyword-based hazard detection
├── KnowledgeServer    → Offline knowledge from knowledge_seed.json
└── ActionServer       → Step-by-step guides, video suggestions
```

## Project Structure

```
app/src/main/java/com/example/lifelens/
├── MainActivity.kt                  # UI, camera, image processing, VLM orchestration
├── nexa/
│   ├── NexaVlmClient.kt            # Nexa VLM SDK wrapper
│   ├── ModelManager.kt             # Model download & validation
│   ├── GenerationConfigSample.kt   # Generation config with image/audio paths
│   └── NexaManifestBean.kt         # Model manifest parsing
├── camera/
│   └── CameraCapture.kt            # CameraX photo capture
├── tool/
│   ├── models.kt                   # Audience enum, data classes
│   ├── PromptBuilder.kt            # Audience-specific prompt generation
│   ├── SafetyServer.kt             # Hazard detection
│   ├── KnowledgeServer.kt          # Offline knowledge lookup
│   ├── ActionServer.kt             # Action suggestions
│   ├── ToolRouter.kt               # Tool orchestration
│   └── TtsManager.kt               # Text-to-speech
└── ui/theme/                        # Material 3 theme
```

## Requirements

- Android 8.1+ (API 27)
- ARM64 device (arm64-v8a)
- ~5GB free storage for model files
- Qualcomm Snapdragon with NPU recommended for image recognition

## Setup

### 1. Clone and Build

```bash
git clone https://github.com/michellemashutian/LifeLens.git
cd LifeLens
```

Open in Android Studio or build from command line:

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### 2. Model Files

The app downloads the OmniNeural-4B-mobile model (~4.9GB) automatically on first launch. Alternatively, you can pre-deploy model files via adb:

```bash
# Create model directory on device
adb shell mkdir -p /sdcard/Android/data/com.example.lifelens/files/models/OmniNeural-4B-mobile

# Push all .nexa files
adb push *.nexa /sdcard/Android/data/com.example.lifelens/files/models/OmniNeural-4B-mobile/
```

**Model files (13 total):**

| File | Description |
|------|-------------|
| `nexa.manifest` | Model metadata |
| `files-1-1.nexa` | Entry file |
| `attachments-{1..3}-3.nexa` | Attachment files (3 parts) |
| `weights-{1..8}-8.nexa` | Model weights (8 parts) |

Download source: [NexaAI/OmniNeural-4B-mobile on HuggingFace](https://huggingface.co/NexaAI/OmniNeural-4B-mobile)

### 3. Qualcomm Device Cloud (QDC)

For testing on QDC devices with SSH tunnel:

```bash
# Establish tunnel (replace session ID and key path)
ssh -i /path/to/qdc_key.pem \
    -L 5037:<SESSION_ID>.sa.svc.cluster.local:5037 \
    -N sshtunnel@ssh.qdc.qualcomm.com

# Install via ADB through tunnel
export ANDROID_ADB_SERVER_PORT=15037
adb connect localhost:5037
adb install -r app-debug.apk
```

## Usage

1. **Launch the app** and tap **Get Started**
2. The app will download/verify model files and initialize the VLM
3. Once ready, you can:
   - **Capture** a photo with the camera
   - **Upload** an image from your gallery
   - **Quick Test** with the bundled test image
4. Type a question (or use the default) and tap **Ask**
5. The model streams its answer in real time

### Audience Modes

| Mode | Language Style | Safety | Default Question |
|------|---------------|--------|-----------------|
| **Elderly** | Simple English, short sentences | 1-3 safety tips | "What is this object? What is it used for? Are there any safety concerns?" |
| **Child** | Friendly, fun facts | Age-appropriate warnings | "What is this? What does it do? Is it safe to use?" |

## Image Processing Pipeline

Images are preprocessed before VLM inference for optimal results:

```
Raw Image → EXIF Rotation Fix → Scale to 448px → Square Center-Crop → JPEG (90%) → VLM
```

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| [Nexa AI Core](https://nexa.ai) | 0.0.22 | On-device VLM inference |
| CameraX | 1.4.1 | Camera integration |
| Jetpack Compose | 2024.09.00 | UI framework |
| OkHttp | 5.1.0 | Model file downloads |
| Kotlinx Serialization | 1.6.3 | JSON parsing |
| ExifInterface | 1.3.7 | Image rotation handling |

## Hardware Compatibility

| Device | Plugin | Image Recognition |
|--------|--------|-------------------|
| Qualcomm Snapdragon 8 Gen 3+ (NPU) | `npu` | Supported |
| Other ARM64 devices | `cpu_gpu` | Text-only fallback |
| Android Emulator | `cpu_gpu` | Text-only (auto-detected) |

## License

Apache License 2.0
