# v1.0.0 Setup Instructions

**Purpose**: Step-by-step walkthrough to prepare a system (local or remote) for running the v1.0.0 end-to-end test suite.

**Time estimate**: 45–90 minutes (depending on network speed and prior SDK installation)

---

## Phase 1: System Validation (5 minutes)

Before starting, verify your system meets minimum requirements. Refer to `REQUIREMENTS.md` for full specs.

### Quick Checks

```bash
# Check RAM (need 16+ GB)
free -h | grep Mem

# Check available disk (need 100+ GB free)
df -h ~

# Check CPU cores (need 4+)
nproc

# Check OS
uname -a

# Verify Bash version (need 4.0+)
bash --version

# Verify Git (need 2.20+)
git --version
```

### If Any Check Fails

- **RAM < 16 GB**: Tests will be slow; 12 GB minimum (with risks)
- **Disk < 100 GB**: Cannot proceed; clean up or use larger drive
- **Cores < 4**: Tests will be very slow; consider reducing emulator memory
- **OS not supported**: Refer to REQUIREMENTS.md OS table; WSL2 for Windows only

---

## Phase 2: Install Required Software (20–40 minutes)

### Step 1: Install System Dependencies

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install -y \
  git \
  curl \
  jq \
  openjdk-11-jdk \
  libssl-dev
```

#### macOS (with Homebrew)
```bash
brew install git curl jq openjdk@11
```

#### Fedora/RHEL
```bash
sudo dnf install -y \
  git \
  curl \
  jq \
  java-11-openjdk \
  openssl-devel
```

#### WSL2 (Windows)
Run Ubuntu/Debian commands above within WSL2 terminal.

### Step 2: Set Up Java Environment

Verify Java 11+ is available:
```bash
java -version
```

If not found, set JAVA_HOME:
```bash
# Find Java installation
update-alternatives --list java

# Or on macOS:
/usr/libexec/java_home -v 11

# Export in your shell profile (~/.bashrc, ~/.zshrc, etc)
export JAVA_HOME=/path/to/java/11
export PATH=$JAVA_HOME/bin:$PATH

# Test
java -version
```

### Step 3: Install Android SDK

**Method A: Download SDK Command-Line Tools (Recommended)**

```bash
# Create SDK directory in workspace
mkdir -p ~/workspace/Android/Sdk

# Download latest command-line tools
# Check https://developer.android.com/studio/command-line/sdkmanager
cd ~/workspace/Android/Sdk
curl -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-latest.zip
unzip cmdline-tools.zip
mkdir -p cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true
rm cmdline-tools.zip

# Set SDK root environment variable
export ANDROID_SDK_ROOT=~/workspace/Android/Sdk
export PATH=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH

# Add to shell profile (~/.bashrc, ~/.zshrc, etc)
echo 'export ANDROID_SDK_ROOT=~/workspace/Android/Sdk' >> ~/.bashrc
echo 'export PATH=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

**Method B: Download Full Android Studio**

```bash
# Easier but larger download (~1GB)
# Download from https://developer.android.com/studio
# Extract, run ./studio.sh, accept license, let IDE install SDK
```

### Step 4: Accept Android Licenses

```bash
# Accept all SDK licenses (non-interactive)
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager --licenses <<EOF
y
y
y
y
y
y
y
y
EOF
```

### Step 5: Install Required SDK Packages

```bash
# Install build tools, platform tools, and emulator
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager \
  "platform-tools" \
  "build-tools;34.0.0" \
  "platforms;android-34" \
  "system-images;android-34;google_apis;x86_64" \
  "emulator"

# Verify installation
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager --list_installed
```

### Step 6: Verify ADB and Emulator Are Available

```bash
# ADB should be in path
which adb
adb version

# Emulator should be in SDK
$ANDROID_SDK_ROOT/emulator/emulator -version
```

---

## Phase 3: Create Android Virtual Device (10 minutes)

### Create the dw_test_phone AVD

```bash
# Create AVD with exact specifications
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager create avd \
  -n dw_test_phone \
  -k "system-images;android-34;google_apis;x86_64" \
  -d "Pixel 6" \
  --sdcard 512M \
  --force

# Verify AVD was created
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager list avd | grep dw_test_phone
```

### Configure AVD for Testing (Optional but Recommended)

```bash
# Edit AVD config for test performance
# macOS/Linux:
nano ~/.android/avd/dw_test_phone.avd/config.ini

# Add/modify these lines:
hw.ramSize=2048
hw.cpu.cores=4
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.keyboard=yes
showDeviceFrame=no
```

---

## Phase 3b: Create Wear OS Virtual Device (10 minutes)

### Create the dw_test_watch AVD

```bash
# Install Wear OS system image
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager \
  "system-images;android-33;android-wear;x86" \
  "platforms;android-33"

# Accept licenses
yes | $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager --licenses

# Create Wear OS AVD
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager create avd \
  -n dw_test_watch \
  -k "system-images;android-33;android-wear;x86" \
  -d "wear_round" \
  --force

# Verify AVD created
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager list avd | grep dw_test_watch
```

### Launch Wear OS Emulator

```bash
# Launch watch emulator (usually gets emulator-5556)
$ANDROID_SDK_ROOT/emulator/emulator \
  -avd dw_test_watch \
  -no-snapshot-save \
  -no-audio \
  -no-boot-anim \
  2>/tmp/wear-emulator.log &

adb wait-for-device
# Verify both devices online
adb devices
# Expected:
# emulator-5554   device   (phone)
# emulator-5556   device   (watch)
```

### Build and Install Wear APK

```bash
cd ~/workspace/datawatch-app

# Build wear APK
./gradlew :wear:assembleDebug

# Install on watch emulator
adb -s emulator-5556 install -r wear/build/outputs/apk/debug/wear-debug.apk

# Verify installation
adb -s emulator-5556 shell pm list packages | grep datawatch
```

### Pair Watch with Phone Emulator

```bash
# Enable DataLayer communication between emulators
# Open Android Studio → Device Manager → select emulator-5556 (watch)
# OR: Use command line pairing
adb -s emulator-5554 forward tcp:5601 tcp:5601
adb -s emulator-5556 forward tcp:5601 tcp:5601

# Verify DataLayer bridge works (phone should show companion app notification)
adb -s emulator-5556 logcat -d | grep -i "DataLayer\|WearOS" | tail -10
```

---

## Phase 3c: Set Up Android Auto DHU Emulator (15 minutes)

### Install DHU from SDK

```bash
# Install Android Auto Desktop Head Unit
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager "extras;google;auto"

# Verify DHU binary exists
ls -la $ANDROID_HOME/extras/google/auto/desktop-head-unit
```

### Enable Android Auto Developer Mode on Phone Emulator

```bash
# Launch phone emulator if not running
# In emulator UI: Settings → Apps → Android Auto → version → tap 10× to enable developer mode
# OR via ADB:
adb -s emulator-5554 shell settings put global development_settings_enabled 1

# Enable unknown sources for Android Auto
adb -s emulator-5554 shell settings put secure install_non_market_apps 1
```

### Connect DHU to Phone Emulator

```bash
# Port-forward for DHU TCP connection
adb -s emulator-5554 forward tcp:5277 tcp:5277

# Launch DHU (connects to first ADB device automatically)
cd $ANDROID_HOME/extras/google/auto
./desktop-head-unit &

# Expected: DHU window opens showing Android Auto interface
# The app's AutoSummaryScreen should appear within 5–10 seconds
```

### DHU Keyboard Controls

| Key | Action |
|---|---|
| Arrow keys | Navigate list items |
| Enter | Select / confirm |
| Backspace | Go back |
| M | Microphone (voice command) |
| H | Home |
| S | Stop (end call) |
| F1–F9 | Software buttons |

### Capture DHU Screenshots for Evidence

```bash
# Screenshots via ADB from the phone emulator
adb -s emulator-5554 shell screencap -p /sdcard/auto_screenshot.png
adb -s emulator-5554 pull /sdcard/auto_screenshot.png evidence/TS-NNN/

# Or: DHU → File menu → Save Screenshot
```

---

## Phase 4: Clone Repositories (5 minutes)

### Verify Workspace Directory

```bash
# Create workspace if it doesn't exist
mkdir -p ~/workspace
cd ~/workspace

# List contents (should be empty or have existing projects)
ls -la
```

### Clone Both Repositories

```bash
# Clone datawatch server repo
git clone https://github.com/dmz006/datawatch.git
cd datawatch && git checkout main && git pull origin main
cd ..

# Clone datawatch-app repo
git clone https://github.com/dmz006/datawatch-app.git
cd datawatch-app && git checkout main && git pull origin main
cd ..

# Verify both repos exist and are on main
cd datawatch && git branch && cd ..
cd datawatch-app && git branch && cd ..
```

---

## Phase 5: Build Datawatch Server Binary (10–20 minutes)

### Prerequisites Check

```bash
# Verify Go is installed (if building from source)
go version

# If not installed, download from https://golang.org/dl
# For Ubuntu: sudo apt install golang-go
# For macOS: brew install go
```

### Build Binary from Source

```bash
cd ~/workspace/datawatch

# Create build output directory
mkdir -p bin

# Build server binary
go build -o bin/datawatch ./cmd/server

# Verify binary exists and is executable
ls -lh bin/datawatch
./bin/datawatch --version
```

### Or Download Pre-Built Binary

```bash
cd ~/workspace/datawatch

# Check latest releases: https://github.com/dmz006/datawatch/releases
# Example (adjust version as needed):
wget https://github.com/dmz006/datawatch/releases/download/v7.0.0/datawatch-linux-amd64
chmod +x datawatch-linux-amd64
mkdir -p bin
mv datawatch-linux-amd64 bin/datawatch

# Verify
./bin/datawatch --version
```

---

## Phase 6: Clone and Build Mobile App (10–15 minutes)

### Verify Gradle and Kotlin

```bash
# Check if gradle wrapper exists in app repo
cd ~/workspace/datawatch-app
ls -la gradlew

# Verify Java is visible to gradle
./gradlew --version
```

### Clean Build of Debug APK

```bash
cd ~/workspace/datawatch-app

# Clean build cache
./gradlew clean

# Build debug APK for publicTrack flavor
./gradlew :composeApp:assemblePublicTrackDebug

# Verify APK was created
ls -lh composeApp/build/outputs/apk/publicTrack/debug/composeApp-publicTrack-debug.apk
```

If build fails:
- Check Java version: `java -version` (need 11+)
- Check network: Gradle downloads dependencies (~500 MB)
- Check disk space: Build needs ~5 GB temporary space

---

## Phase 7: Set Up Secondary Test Instance (5 minutes)

### Create Working Directory (Outside the Repo)

```bash
# Working dir lives OUTSIDE the repo — never commit test data
cd ~/workspace/datawatch-app
RUN_ID=$(openssl rand -hex 3)
TEST_WORK_DIR=~/workspace/datawatch-test-${RUN_ID}
TEST_DATA_DIR=${TEST_WORK_DIR}/.datawatch-test-${RUN_ID}
mkdir -p "$TEST_DATA_DIR"
# BL318: pre-create .claude/ inside the data dir so the daemon scopes
# all MCP registrations here instead of writing to ~/.claude.json
mkdir -p "${TEST_DATA_DIR}/.claude"
echo "Test working dir: $TEST_WORK_DIR"
```

### Write Test Configuration

```bash
# Create config.yaml for secondary instance
cat > "${TEST_WORK_DIR}/config.yaml" <<EOF
data_dir: ${TEST_DATA_DIR}

server:
  port: 18080
  tls_port: 18443
  token: "dw-test-token-12345"
  tls_cert: ""
  tls_key: ""
  tls_enabled: true
  tls_auto_generate: true

session:
  skip_permissions: true

autonomous:
  enabled: true

memory:
  enabled: true

mcp:
  enabled: true
EOF

# Verify file was created
cat "${TEST_WORK_DIR}/config.yaml"
```

---

## Phase 8: Start and Verify Components (10 minutes)

### Start Emulator

```bash
# Launch emulator in background
$ANDROID_SDK_ROOT/emulator/emulator \
  -avd dw_test_phone \
  -no-snapshot-save \
  -no-audio \
  -gpu swiftshader_indirect \
  -no-boot-anim \
  2>/tmp/emulator.log &

# Wait for emulator to fully boot
adb wait-for-device
echo "Waiting for emulator to finish booting..."
adb shell getprop sys.boot_completed

# Verify device is online
adb devices
```

Expected output:
```
List of attached devices
emulator-5554          device
```

### Install Mobile App to Emulator

```bash
# Install APK
cd ~/workspace/datawatch-app
adb install -r composeApp/build/outputs/apk/publicTrack/debug/composeApp-publicTrack-debug.apk

# Verify installation
adb shell pm list packages | grep datawatch
```

Expected output:
```
package:com.anthropic.datawatch
```

### Set Up ADB Port Forwarding

```bash
# Forward emulator ports to host
adb reverse tcp:18443 tcp:18443
adb reverse tcp:18080 tcp:18080

# Verify forwarding
adb reverse --list
```

Expected output:
```
emulator-5554 tcp:18443 tcp:18443
emulator-5554 tcp:18080 tcp:18080
```

### Start Secondary Datawatch Instance

```bash
# Start server — CLAUDE_CONFIG_DIR keeps MCP registrations inside the
# test data dir (BL318 pattern; never touches ~/.claude.json or ~/.mcp.json)
CLAUDE_CONFIG_DIR="${TEST_DATA_DIR}/.claude" \
  ~/workspace/datawatch/bin/datawatch start --foreground \
  --config "${TEST_WORK_DIR}/config.yaml" \
  >> "${TEST_WORK_DIR}/daemon.log" 2>&1 &
echo $! > "${TEST_WORK_DIR}/test-daemon.pid"

# Give server time to start
sleep 3

# Health check
curl -sk https://127.0.0.1:18443/api/health
```

Expected output:
```
{"status":"healthy","timestamp":"2026-05-16T..."}
```

### Configure Mobile App with Test Server

```bash
# Use ADB to interact with app
# In the emulator:
# 1. Open Settings app
# 2. Navigate to Comms
# 3. Tap "Add server"
# 4. Fill in:
#    - Name: dw-test
#    - URL: https://10.0.2.2:18443
#    - Bearer token: dw-test-token-12345
#    - Trust-all TLS: ON
# 5. Tap Save

# Or use ADB input automation:
adb shell input text "dw-test"
adb shell input keyevent 66  # Enter
adb shell input text "https://10.0.2.2:18443"
adb shell input keyevent 66
adb shell input text "dw-test-token-12345"
adb shell input keyevent 66
# Toggle trust-all (requires UI interaction)
```

---

## Phase 9: Verify Full Test Environment (5 minutes)

Run the verification checklist from `REQUIREMENTS.md`:

```bash
# RAM check
free -h | grep Mem

# Disk check
df -h ~/workspace

# Emulator running
adb devices

# ADB forwarding
adb reverse --list

# Datawatch server health
curl -sk https://127.0.0.1:18443/api/health

# Mobile app installed
adb shell pm list packages | grep datawatch

# Test artifacts directory ready
cd ~/workspace/datawatch-app/docs/testing/v1.0.0
ls -la | grep evidence
```

---

## Cleanup (if needed)

### Stop All Components

```bash
# Stop emulator
adb emu kill

# Stop datawatch server via saved PID — never grep ps
kill $(cat "${TEST_WORK_DIR}/test-daemon.pid") 2>/dev/null || true

# Kill ADB server (optional)
adb kill-server
```

### Reset Test Instance (between test runs)

```bash
# Remove old working dir and create a fresh one
rm -rf "${TEST_WORK_DIR}"

RUN_ID=$(openssl rand -hex 3)
TEST_WORK_DIR=~/workspace/datawatch-test-${RUN_ID}
TEST_DATA_DIR=${TEST_WORK_DIR}/.datawatch-test-${RUN_ID}
mkdir -p "$TEST_DATA_DIR"
mkdir -p "${TEST_DATA_DIR}/.claude"

# Re-run Phase 7 "Write Test Configuration" and Phase 8 "Start Secondary Instance"

# TEST_WORK_DIR cleanup removes TEST_DATA_DIR/.claude/ and .mcp.json — no stale
# MCP registrations survive across runs.
```

---

## Troubleshooting Setup Issues

### SDK Installation Issues

```bash
# Check sdkmanager path
which sdkmanager
echo $ANDROID_SDK_ROOT

# Reinstall tools if path issues
export ANDROID_SDK_ROOT=~/workspace/Android/Sdk
export PATH=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH
sdkmanager --list_installed
```

### Emulator Won't Start

```bash
# Check for stray emulator processes
ps aux | grep emulator

# Kill any stuck processes
pkill -9 -f "emulator.*dw_test_phone"

# Reset ADB
adb kill-server
adb start-server

# Try starting emulator again with verbose logging
$ANDROID_SDK_ROOT/emulator/emulator \
  -avd dw_test_phone \
  -no-snapshot-save \
  -verbose \
  2>&1 | tee /tmp/emulator-verbose.log
```

### Java/Gradle Issues

```bash
# Verify Java
java -version
echo $JAVA_HOME

# Clear gradle cache if build fails
cd ~/workspace/datawatch-app
rm -rf .gradle/
./gradlew clean

# Try build again
./gradlew :composeApp:assemblePublicTrackDebug
```

### Datawatch Server Won't Start

```bash
# Check port availability
lsof -i :18080
lsof -i :18443

# Kill process on port if needed
kill -9 <PID>

# Check server binary
~/workspace/datawatch/bin/datawatch --version

# Check data directory
ls -la .datawatch-test/
cat .datawatch-test/config.yaml
```

### Network Connectivity Issues

```bash
# Test adb connection
adb devices

# Test port forwarding
adb reverse --list
adb reverse tcp:18443 tcp:18443

# Test from emulator (curl in adb shell)
adb shell curl -k https://10.0.2.2:18443/api/health

# Test from host
curl -sk https://127.0.0.1:18443/api/health
```

---

## Next Steps

Once setup is complete and all verification checks pass:

1. **Read `RUN.md`** for instructions on executing the test suite
2. **Review `plan.md`** for the 369 stories you'll be testing
3. **Update `cookbook.md`** as you execute each story
4. **Save evidence** to `evidence/TS-NNN/` after each test
5. **Commit progress** after each T-sprint completes

---

**Setup Date**: 2026-05-16  
**Datawatch Version**: v7.0.0+  
**Mobile App Version**: v1.0.0  
**For questions**: Refer to `REQUIREMENTS.md` or `RUN.md`
