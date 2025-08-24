# Network Configuration Guide

## 🌐 Different URL Configurations for Different Testing Scenarios

### Option 1: Android Emulator
**Use**: `http://10.0.2.2:8080/`
**When**: Testing on Android Studio emulator
**Why**: Android emulator maps `10.0.2.2` to the host machine's `localhost`

### Option 2: Real Android Device (Same Network)
**Use**: `http://192.168.1.XXX:8080/` (your computer's IP)
**When**: Testing on physical device connected to same Wi-Fi
**How to find your IP**:
```bash
# On Mac/Linux
ifconfig | grep "inet " | grep -v 127.0.0.1

# On Windows
ipconfig | findstr "IPv4"
```

### Option 3: USB Debugging (Port Forwarding)
**Use**: `http://localhost:8080/`
**When**: Testing on real device with USB debugging
**Setup**: Use ADB port forwarding
```bash
adb reverse tcp:8080 tcp:8080
```

## 🔧 Current Configuration

I just updated the code to use `http://localhost:8080/` which will work if you use ADB port forwarding.

## 🚀 Recommended Setup for Testing

### Step 1: Enable ADB Port Forwarding
```bash
# Forward device's localhost:8080 to your computer's localhost:8080
adb reverse tcp:8080 tcp:8080
```

### Step 2: Verify Forwarding Works
```bash
# This should work from your computer
curl http://localhost:8080/api/ai/insights

# After port forwarding, this should also work from device
adb shell curl http://localhost:8080/api/ai/insights
```

### Step 3: Build and Install App
```bash
./gradlew assembleDebug
adb install ./app/build/outputs/apk/debug/app-debug.apk
```

## 🔄 Alternative: Dynamic Configuration

If you want to switch between different environments easily, I can create a build variant system where you can choose:
- Development (localhost:8080)
- Emulator (10.0.2.2:8080)  
- Network IP (192.168.1.X:8080)

Let me know which approach you prefer!