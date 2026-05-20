# v1.0.0 Test Execution Requirements

**Purpose**: Comprehensive checklist for any system (local or remote) to execute the v1.0.0 end-to-end test plan.

**Test Scope**: T1–T21 (369 stories across all mobile, Wear, and Auto surfaces)

---

## System Requirements

### Hardware

| Component | Specification | Notes |
|-----------|---------------|-------|
| **CPU** | 4+ cores, 2.0+ GHz | For emulator + datawatch server simultaneous load |
| **RAM** | 16+ GB | Emulator (2GB) + datawatch (2GB) + OS + overhead |
| **Disk** | 100+ GB free | Emulator image (8GB) + datawatch repo (5GB) + test artifacts (10GB+) |
| **Network** | 1 Gbps LAN | For emulator-to-host bridge; bridging latency must be <10ms |

### Operating System

| OS | Version | Status |
|----|---------|--------|
| Linux (Ubuntu/Debian) | 20.04 LTS+ | ✅ Primary (tested) |
| Linux (Fedora/RHEL) | 8.0+ | ✅ Supported |
| macOS | 12.0+ (Intel or Apple Silicon) | ✅ Supported (Rosetta2 for x86_64 emulator) |
| Windows | 10/11 (WSL2) | ⚠️ WSL2 only; native Windows not tested |

### Software Dependencies

#### Required

| Package | Version | Install Command | Purpose |
|---------|---------|-----------------|---------|
| **Android SDK** | 34 (API 34) | See `SETUP.md` § SDK Setup | Emulator + ADB |
| **Android Emulator** | Latest | `sdkmanager "emulator"` | Run dw_test_phone AVD |
| **ADB (Android Debug Bridge)** | Latest | Included in SDK Tools | Control emulator, install APK, logcat |
| **Git** | 2.20+ | `apt install git` | Clone repos, commit results |
| **Bash** | 4.0+ | Included in OS | Test runner scripts |
| **curl** | 7.0+ | `apt install curl` | HTTP requests to datawatch server |
| **jq** | 1.6+ | `apt install jq` | JSON parsing (progress tracking) |
| **datawatch binary** | v7.0.0+ | Build from source or download | Secondary test instance daemon |

#### Optional (for enhanced diagnostics)

| Package | Purpose |
|---------|---------|
| `screencap` | Emulator screenshot capture (built-in) |
| `logcat` | ADB logcat viewing (built-in) |
| `openssl` | TLS cert verification (for self-signed certs) |
| `docker` | Alternative to native datawatch binary (containerized) |

---

## Repository Setup

### Source Repositories

**Two repos required** (clone to sibling directories):

```
~/workspace/
  ├── datawatch/              # Server repo (dmz006/datawatch)
  │   └── bin/datawatch       # Build output (or downloaded binary)
  └── datawatch-app/          # Mobile app repo (dmz006/datawatch-app)
      ├── docs/testing/v1.0.0/plan.md
      └── composeApp/...      # Android app source
```

### Clone Commands

```bash
cd ~/workspace

# Clone datawatch server repo (if not already present)
git clone https://github.com/dmz006/datawatch.git

# Clone datawatch-app repo (if not already present)
git clone https://github.com/dmz006/datawatch-app.git

# Ensure both repos are on main branch and up-to-date
cd datawatch && git checkout main && git pull origin main
cd ../datawatch-app && git checkout main && git pull origin main
```

---

## Emulator Setup

### AVD (Android Virtual Device) Configuration

**Device Name**: `dw_test_phone`  
**Target**: Android 14 (API 34)  
**Device Profile**: Pixel 6  
**ABI**: x86_64 (or arm64-v8a if running on ARM host)  
**RAM**: 2048 MB  
**Storage**: 8 GB  

### Create AVD (if not already present)

```bash
# List available system images
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager --list | grep "system-images.*34"

# Install Android 14 system image (if not present)
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager "system-images;android-34;google_apis;x86_64"

# Create AVD
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager create avd \
  -n dw_test_phone \
  -k "system-images;android-34;google_apis;x86_64" \
  -d "Pixel 6"

# Verify AVD was created
$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager list avd
```

---

## Secondary Test Instance (datawatch Server)

### Datawatch Binary

**Source**: 
- **Option A** (Recommended): Build from source
  ```bash
  cd ~/workspace/datawatch
  go build -o bin/datawatch ./cmd/server
  ```
- **Option B**: Download pre-built binary (if available)
  ```bash
  # Check releases: https://github.com/dmz006/datawatch/releases
  wget https://github.com/dmz006/datawatch/releases/download/vX.Y.Z/datawatch-linux-amd64
  chmod +x datawatch-linux-amd64
  mv datawatch-linux-amd64 ~/workspace/datawatch/bin/datawatch
  ```

### Configuration

**Config file**: `../datawatch-test-<RUN_ID>/config.yaml` (outside the repo)

```yaml
data_dir: /home/dmz/workspace/datawatch-test-<RUN_ID>/.datawatch-test-<pid>
server:
  port: 18080                    # HTTP port
  tls_port: 18443                # HTTPS port
  token: "dw-test-token-12345"   # Bearer token for auth
  tls_enabled: true              # Enable TLS
  tls_auto_generate: true        # Auto-generate self-signed cert

session:
  skip_permissions: true         # Don't prompt for system permissions

autonomous:
  enabled: true                  # Enable Automata/PRD features

memory:
  enabled: true                  # Enable episodic memory

mcp:
  enabled: true                  # Enable MCP surface (optional)
```

### Startup Commands

```bash
# Working dir outside the repo
RUN_ID=$(openssl rand -hex 3)
TEST_WORK_DIR=~/workspace/datawatch-test-${RUN_ID}
TEST_DATA_DIR=${TEST_WORK_DIR}/.datawatch-test-${RUN_ID}
mkdir -p "$TEST_DATA_DIR"
mkdir -p "${TEST_DATA_DIR}/.claude"  # BL318: scope MCP config to this instance

cat > "${TEST_WORK_DIR}/config.yaml" <<'EOF'
data_dir: REPLACED_BY_STARTUP_SCRIPT
server:
  port: 18080
  tls_port: 18443
  token: "dw-test-token-12345"
  tls_enabled: true
  tls_auto_generate: true
session:
  skip_permissions: true
autonomous:
  enabled: true
memory:
  enabled: true
EOF
# Inject actual data_dir
sed -i "s|data_dir: REPLACED_BY_STARTUP_SCRIPT|data_dir: ${TEST_DATA_DIR}|" "${TEST_WORK_DIR}/config.yaml"

CLAUDE_CONFIG_DIR="${TEST_DATA_DIR}/.claude" \
  ~/workspace/datawatch/bin/datawatch start --foreground \
  --config "${TEST_WORK_DIR}/config.yaml" \
  >> "${TEST_WORK_DIR}/daemon.log" 2>&1 &
echo $! > "${TEST_WORK_DIR}/test-daemon.pid"

# Verify startup (poll until ready)
sleep 3
curl -sk https://127.0.0.1:18443/api/health
```

---

## Mobile App Build

### Prerequisites

- **Kotlin**: 1.8+
- **Gradle**: 8.0+
- **Java JDK**: 11+

### Build Commands

```bash
cd ~/workspace/datawatch-app

# Clean build
rtk ./gradlew clean

# Build publicTrack debug APK
rtk ./gradlew :composeApp:assemblePublicTrackDebug

# Output APK
composeApp/build/outputs/apk/publicTrack/debug/composeApp-publicTrack-debug.apk
```

### Installation

```bash
# Connect emulator (via adb)
adb wait-for-device

# Install APK
adb install -r composeApp/build/outputs/apk/publicTrack/debug/composeApp-publicTrack-debug.apk

# Verify installation
adb shell pm list packages | grep datawatch
```

---

## Network Configuration

### ADB Port Forwarding

```bash
# Forward emulator HTTPS port to host
adb reverse tcp:18443 tcp:18443

# Forward HTTP port (optional)
adb reverse tcp:18080 tcp:18080

# Verify forwarding
adb reverse --list
```

### Emulator Networking

**In-app server URL**: `https://10.0.2.2:18443`
- `10.0.2.2` is the special alias for host localhost from inside the emulator
- No additional configuration needed; emulator handles network bridging

---

## Test Directory Structure

```
~/workspace/datawatch-app/docs/testing/v1.0.0/
├── plan.md                     # Story definitions (T1–T21, 369 stories)
├── cookbook.md                 # Live status tracker (updated during run)
├── REQUIREMENTS.md             # This file
├── SETUP.md                    # Detailed setup instructions
├── RUN.md                      # Test execution instructions
├── .gitignore                  # Ignores evidence/ directory
└── evidence/                   # Test artifacts (gitignored)
    ├── TS-001/
    │   ├── splash.png
    │   ├── response.json
    │   └── logcat.txt
    ├── TS-002/
    │   └── ...
    └── ...
```

---

## Access Control & Security

### Token Management

- **Test token**: `dw-test-token-12345` (hardcoded in config.yaml for secondary instance)
- **Non-production**: Use ONLY on secondary test instance; never use on production servers
- **Bearer header**: `Authorization: Bearer dw-test-token-12345`

### TLS Certificates

- **Self-signed**: Secondary instance auto-generates on first startup
- **Trust-all**: Mobile app configured with `Trust-all TLS: true` for secondary instance
- **Certificate location**: `${TEST_WORK_DIR}/.datawatch-test-<pid>/tls/cert.pem` (auto-generated)

---

## System Limits & Tuning

### Emulator Performance

For optimal emulator performance (faster test execution):

```bash
# Allocate max CPU cores (replace 4 with your core count)
emulator -avd dw_test_phone -cores 4 -no-audio -no-window -gpu swiftshader_indirect

# Increase heap size (if test data is large)
emulator -avd dw_test_phone -memory 3072 ...
```

### Datawatch Server Performance

```bash
# Monitor server resources during tests
watch -n 1 "ps aux | grep datawatch"

# If server becomes unresponsive, increase stack size
ulimit -s 8192  # Increase stack size before starting daemon
```

---

## Disk Space Requirements

| Component | Space Needed | Notes |
|-----------|--------------|-------|
| Android SDK | 15 GB | Includes emulator, system images, tools |
| Emulator snapshot | 8 GB | dw_test_phone AVD filesystem |
| datawatch repo + binary | 5 GB | Source code + compiled binary |
| datawatch-app repo | 3 GB | Source code + build artifacts |
| Test artifacts (evidence/) | 10+ GB | Screenshots, logcat, JSON responses (per run) |
| **TOTAL** | **40+ GB** | Reserve 100+ GB for safety |

---

## Verification Checklist

Before starting tests, verify:

- [ ] 16+ GB RAM available (`free -h`)
- [ ] 100+ GB disk free (`df -h ~`)
- [ ] Android SDK installed (`which adb` / `which emulator`)
- [ ] `dw_test_phone` AVD exists (`avdmanager list avd | grep dw_test_phone`)
- [ ] datawatch binary built/downloaded (`ls ~/workspace/datawatch/bin/datawatch`)
- [ ] datawatch-app repo cloned (`ls ~/workspace/datawatch-app`)
- [ ] Both repos on `main` branch (`cd datawatch && git status` / same for datawatch-app)
- [ ] Network available (can reach github.com, etc.)
- [ ] Bash version 4+ (`bash --version`)
- [ ] curl installed (`which curl`)
- [ ] jq installed (`which jq`)

---

## Troubleshooting

### Emulator won't start
```bash
# Check available system images
sdkmanager --list | grep system-images

# Kill stray emulator processes
pkill -f "emulator.*dw_test_phone"

# Restart from clean state
adb kill-server
emulator -avd dw_test_phone -no-snapshot-save &
```

### Datawatch server won't start
```bash
# Check port availability
lsof -i :18080
lsof -i :18443

# Kill existing process by PID — never grep ps
kill $(cat "${TEST_WORK_DIR}/test-daemon.pid") 2>/dev/null || true

# Remove working dir and recreate from scratch
rm -rf "${TEST_WORK_DIR}"
# Re-run startup commands (set RUN_ID, TEST_WORK_DIR, etc.) to recreate config and restart
```

### ADB connection issues
```bash
# Restart ADB
adb kill-server
adb start-server
adb devices

# Check reverse forwarding
adb reverse --list
adb reverse tcp:18443 tcp:18443
```

---

## Support & Questions

- **Plan details**: See `docs/testing/v1.0.0/plan.md`
- **Setup instructions**: See `docs/testing/v1.0.0/SETUP.md`
- **Execution instructions**: See `docs/testing/v1.0.0/RUN.md`
- **Issues/bugs**: File in `dmz006/datawatch-app` repo

---

**Last updated**: 2026-05-16  
**For release**: v1.0.0
