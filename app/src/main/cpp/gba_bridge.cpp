#include <jni.h>
#include <string>
#include <sstream>
#include <vector>
#include <mutex>
#include <android/log.h>

#include <mgba/core/core.h>
#include <mgba/core/interface.h>
#include <mgba/core/blip_buf.h>
#include <mgba/core/cheats.h>
#include <mgba/core/serialize.h>
#include <mgba/internal/gba/cheats.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/memory.h>
#include <mgba-util/vfs.h>

#include "audio_engine.h"
#include "video_renderer.h"

#define LOG_TAG "GbaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex sCoreMutex;
static struct mCore* sCore = nullptr;
static struct VFile* sRomVf = nullptr;
static void* sRomBufferCopy = nullptr;
static size_t sRomBufferSize = 0;

static uint32_t sVideoBuffer[VideoRenderer::GBA_WIDTH * VideoRenderer::GBA_HEIGHT];
static int16_t sAudioBuffer[4096 * 2];

static AudioEngine sAudioEngine;
static VideoRenderer sVideoRenderer;
static bool sIsVideoInitialized = false;
static float sFastForwardRatio = 1.0f;

static void cleanUpCore() {
    sAudioEngine.stop();
    if (sCore) {
        sCore->unloadROM(sCore);
        sCore->deinit(sCore);
        sCore = nullptr;
    }
    if (sRomVf) {
        sRomVf->close(sRomVf);
        sRomVf = nullptr;
    }
    if (sRomBufferCopy) {
        free(sRomBufferCopy);
        sRomBufferCopy = nullptr;
        sRomBufferSize = 0;
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeInit(
    JNIEnv* env, jobject /*thiz*/, jstring internalDir) {
    LOGI("GbaBridge nativeInit completed");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeLoadRom(
    JNIEnv* env, jobject /*thiz*/, jbyteArray romBytes, jint romSize) {
    std::lock_guard<std::mutex> lock(sCoreMutex);

    cleanUpCore();

    if (!romBytes || romSize <= 0) {
        LOGE("Invalid ROM buffer or size");
        return JNI_FALSE;
    }

    sRomBufferCopy = malloc(romSize);
    if (!sRomBufferCopy) {
        LOGE("Failed to allocate memory for ROM copy");
        return JNI_FALSE;
    }

    jbyte* buffer = env->GetByteArrayElements(romBytes, nullptr);
    memcpy(sRomBufferCopy, buffer, romSize);
    env->ReleaseByteArrayElements(romBytes, buffer, JNI_ABORT);
    sRomBufferSize = romSize;

    sRomVf = VFileFromMemory(sRomBufferCopy, sRomBufferSize);
    if (!sRomVf) {
        LOGE("Failed to create VFile from memory");
        cleanUpCore();
        return JNI_FALSE;
    }

    sCore = mCoreFindVF(sRomVf);
    if (!sCore) {
        sCore = mCoreCreate(mPLATFORM_GBA);
    }

    if (!sCore) {
        LOGE("Failed to create mCore for GBA");
        cleanUpCore();
        return JNI_FALSE;
    }

    mCoreInitConfig(sCore, nullptr);
    sCore->init(sCore);


    // Set 240x160 video buffer
    memset(sVideoBuffer, 0, sizeof(sVideoBuffer));
    sCore->setVideoBuffer(sCore, (color_t*)sVideoBuffer, VideoRenderer::GBA_WIDTH);

    // Audio setup (44.1kHz stereo)
    sCore->setAudioBufferSize(sCore, 2048);
    blip_set_rates(sCore->getAudioChannel(sCore, 0), sCore->frequency(sCore), 44100);
    blip_set_rates(sCore->getAudioChannel(sCore, 1), sCore->frequency(sCore), 44100);

    bool loaded = sCore->loadROM(sCore, sRomVf);
    if (!loaded) {
        LOGE("Failed to load ROM into mCore");
        cleanUpCore();
        return JNI_FALSE;
    }

    sCore->reset(sCore);
    sAudioEngine.reset();
    sAudioEngine.start(44100);

    LOGI("GBA ROM loaded successfully (%d bytes)", romSize);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeLoadRomFile(
    JNIEnv* env, jobject /*thiz*/, jstring romPath) {
    std::lock_guard<std::mutex> lock(sCoreMutex);

    cleanUpCore();

    const char* pathStr = env->GetStringUTFChars(romPath, nullptr);
    if (!pathStr) return JNI_FALSE;

    sRomVf = VFileOpen(pathStr, O_RDONLY);
    env->ReleaseStringUTFChars(romPath, pathStr);

    if (!sRomVf) {
        LOGE("Failed to open ROM file");
        return JNI_FALSE;
    }

    sCore = mCoreFindVF(sRomVf);
    if (!sCore) {
        sCore = mCoreCreate(mPLATFORM_GBA);
    }

    if (!sCore) {
        LOGE("Failed to create mCore");
        cleanUpCore();
        return JNI_FALSE;
    }

    mCoreInitConfig(sCore, nullptr);
    sCore->init(sCore);

    memset(sVideoBuffer, 0, sizeof(sVideoBuffer));
    sCore->setVideoBuffer(sCore, (color_t*)sVideoBuffer, VideoRenderer::GBA_WIDTH);

    sCore->setAudioBufferSize(sCore, 2048);
    blip_set_rates(sCore->getAudioChannel(sCore, 0), sCore->frequency(sCore), 44100);
    blip_set_rates(sCore->getAudioChannel(sCore, 1), sCore->frequency(sCore), 44100);

    bool loaded = sCore->loadROM(sCore, sRomVf);
    if (!loaded) {
        LOGE("Failed to load ROM from file");
        cleanUpCore();
        return JNI_FALSE;
    }

    sCore->reset(sCore);
    sAudioEngine.reset();
    sAudioEngine.start(44100);

    LOGI("ROM loaded from file successfully");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeUnloadRom(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    cleanUpCore();
    LOGI("ROM unloaded");
}

JNIEXPORT jboolean JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeIsRomLoaded(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    return sCore != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeStepFrame(
    JNIEnv* /*env*/, jobject /*thiz*/, jint keyMask) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (!sCore) return;

    sCore->setKeys(sCore, keyMask);
    sCore->runFrame(sCore);

    // Process audio samples
    blip_t* left = sCore->getAudioChannel(sCore, 0);
    blip_t* right = sCore->getAudioChannel(sCore, 1);
    if (left && right) {
        int avail = blip_samples_avail(left);
        if (avail > 0) {
            if (avail > 4096) avail = 4096;
            blip_read_samples(left, sAudioBuffer, avail, 1);
            blip_read_samples(right, sAudioBuffer + 1, avail, 1);
            sAudioEngine.writeSamples(sAudioBuffer, avail);
        }
    }
}

JNIEXPORT jintArray JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeGetVideoBuffer(
    JNIEnv* env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    constexpr int totalPixels = VideoRenderer::GBA_WIDTH * VideoRenderer::GBA_HEIGHT;
    jintArray result = env->NewIntArray(totalPixels);
    env->SetIntArrayRegion(result, 0, totalPixels, (jint*)sVideoBuffer);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeSaveState(
    JNIEnv* env, jobject /*thiz*/, jint slot, jstring path) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (!sCore) return JNI_FALSE;

    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    struct VFile* vf = VFileOpen(pathStr, O_CREAT | O_TRUNC | O_RDWR);
    env->ReleaseStringUTFChars(path, pathStr);

    if (!vf) {
        LOGE("Failed to open file for save state: %d", slot);
        return JNI_FALSE;
    }
    bool success = mCoreSaveStateNamed(sCore, vf, SAVESTATE_SAVEDATA | SAVESTATE_RTC);
    vf->close(vf);

    LOGI("SaveState named: slot=%d, success=%d", slot, success);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeLoadState(
    JNIEnv* env, jobject /*thiz*/, jint slot, jstring path) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (!sCore) return JNI_FALSE;

    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    struct VFile* vf = VFileOpen(pathStr, O_RDONLY);

    if (!vf) {
        LOGE("Failed to open file for load state: slot=%d, path=%s", slot, pathStr);
        env->ReleaseStringUTFChars(path, pathStr);
        return JNI_FALSE;
    }

    size_t fileSize = vf->size(vf);
    LOGI("nativeLoadState: slot=%d, size=%zu, path=%s", slot, fileSize, pathStr);

    // 1. Try standard mCoreLoadStateNamed with SAVESTATE_SAVEDATA | SAVESTATE_RTC
    bool success = mCoreLoadStateNamed(sCore, vf, SAVESTATE_SAVEDATA | SAVESTATE_RTC);
    LOGI("LoadState primary (SAVEDATA|RTC): slot=%d, success=%d", slot, success);

    // 2. If primary failed, try with flags = 0
    if (!success) {
        vf->seek(vf, 0, SEEK_SET);
        success = mCoreLoadStateNamed(sCore, vf, 0);
        LOGI("LoadState fallback (flags=0): slot=%d, success=%d", slot, success);
    }

    // 3. Fallback: If it's a battery save file (e.g. <= 131072 bytes like 64KB/128KB from companion web app)
    if (!success && fileSize > 0 && fileSize <= 131072) {
        LOGI("LoadState fallback: file size %zu matches battery save, restoring as savedata", fileSize);
        bool batOk = mCoreLoadSaveFile(sCore, pathStr, false);
        if (batOk) {
            sCore->reset(sCore);
            success = true;
            LOGI("LoadState battery fallback succeeded, core reset");
        }
    }

    vf->close(vf);
    env->ReleaseStringUTFChars(path, pathStr);

    LOGI("LoadState final: slot=%d, success=%d", slot, success);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeSaveBattery(
    JNIEnv* env, jobject /*thiz*/, jstring path) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (!sCore) return JNI_FALSE;

    void* sram = nullptr;
    size_t size = sCore->savedataClone(sCore, &sram);
    if (!sram || size == 0) return JNI_FALSE;

    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    struct VFile* vf = VFileOpen(pathStr, O_CREAT | O_TRUNC | O_RDWR);
    env->ReleaseStringUTFChars(path, pathStr);

    if (!vf) {
        free(sram);
        return JNI_FALSE;
    }

    size_t written = vf->write(vf, sram, size);
    vf->close(vf);
    free(sram);

    LOGI("Battery save exported (%zu bytes)", written);
    return written == size ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeLoadBattery(
    JNIEnv* env, jobject /*thiz*/, jstring path) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (!sCore) return JNI_FALSE;

    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    bool success = mCoreLoadSaveFile(sCore, pathStr, false);
    env->ReleaseStringUTFChars(path, pathStr);

    LOGI("Battery save loaded: %d", success);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeAddCheat(
    JNIEnv* env, jobject /*thiz*/, jstring name, jstring code, jint type) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (!sCore) return JNI_FALSE;

    struct mCheatDevice* device = sCore->cheatDevice(sCore);
    if (!device) return JNI_FALSE;

    const char* nameStr = env->GetStringUTFChars(name, nullptr);
    const char* codeStr = env->GetStringUTFChars(code, nullptr);

    struct mCheatSet* set = device->createSet(device, nameStr);
    bool success = false;
    if (set) {
        std::stringstream ss(codeStr);
        std::string line;
        bool anyAdded = false;
        while (std::getline(ss, line)) {
            size_t first = line.find_first_not_of(" \t\r\n");
            if (first == std::string::npos) continue;
            size_t last = line.find_last_not_of(" \t\r\n");
            std::string trimmed = line.substr(first, last - first + 1);
            if (trimmed.empty()) continue;

            if (mCheatAddLine(set, trimmed.c_str(), type >= 0 ? type : GBA_CHEAT_AUTODETECT)) {
                anyAdded = true;
            }
        }

        if (anyAdded) {
            mCheatAddSet(device, set);
            mCheatRefresh(device, set);
            success = true;
        } else {
            mCheatSetDeinit(set);
            free(set);
        }
    }

    env->ReleaseStringUTFChars(name, nameStr);
    env->ReleaseStringUTFChars(code, codeStr);

    LOGI("Add cheat '%s' success=%d", nameStr, success);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeClearCheats(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (!sCore) return;

    struct mCheatDevice* device = sCore->cheatDevice(sCore);
    if (device) {
        mCheatDeviceClear(device);
        LOGI("Cheats cleared");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeWriteMemory(
    JNIEnv* /*env*/, jobject /*thiz*/, jint address, jint value, jint size) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (!sCore) return JNI_FALSE;
    uint32_t addr = static_cast<uint32_t>(address);
    uint32_t val = static_cast<uint32_t>(value);
    if (size == 1) {
        sCore->busWrite8(sCore, addr, static_cast<uint8_t>(val));
    } else if (size == 2) {
        sCore->busWrite16(sCore, addr, static_cast<uint16_t>(val));
    } else if (size == 4) {
        sCore->busWrite32(sCore, addr, val);
    }
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeReadMemory(
    JNIEnv* /*env*/, jobject /*thiz*/, jint address, jint size) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (!sCore) return 0;
    uint32_t addr = static_cast<uint32_t>(address);
    if (size == 1) {
        return sCore->busRead8(sCore, addr);
    } else if (size == 2) {
        return sCore->busRead16(sCore, addr);
    } else if (size == 4) {
        return static_cast<jint>(sCore->busRead32(sCore, addr));
    }
    return 0;
}

JNIEXPORT jintArray JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeScanMemory(
    JNIEnv* env, jobject /*thiz*/,
    jint targetVal, jint valType, jint compType, jintArray prevAddrs) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (!sCore) return env->NewIntArray(0);

    struct GBA* gba = static_cast<struct GBA*>(sCore->board);
    if (!gba || !gba->memory.wram || !gba->memory.iwram) return env->NewIntArray(0);

    const uint8_t* ewram = reinterpret_cast<const uint8_t*>(gba->memory.wram);
    const uint8_t* iwram = reinterpret_cast<const uint8_t*>(gba->memory.iwram);

    auto readVal = [&](uint32_t addr) -> uint32_t {
        const uint8_t* buf = nullptr;
        uint32_t offset = 0;
        if (addr >= 0x02000000 && addr <= 0x02040000 - valType) {
            buf = ewram;
            offset = addr - 0x02000000;
        } else if (addr >= 0x03000000 && addr <= 0x03008000 - valType) {
            buf = iwram;
            offset = addr - 0x03000000;
        } else {
            return 0;
        }

        if (valType == 1) return buf[offset];
        if (valType == 2) return buf[offset] | (buf[offset + 1] << 8);
        if (valType == 4) return buf[offset] | (buf[offset + 1] << 8) | (buf[offset + 2] << 16) | (buf[offset + 3] << 24);
        return 0;
    };

    std::vector<int> matches;
    matches.reserve(5000);
    uint32_t target = static_cast<uint32_t>(targetVal);

    if (prevAddrs != nullptr) {
        jsize len = env->GetArrayLength(prevAddrs);
        jint* addrs = env->GetIntArrayElements(prevAddrs, nullptr);
        for (jsize i = 0; i < len; ++i) {
            uint32_t addr = static_cast<uint32_t>(addrs[i]);
            uint32_t cur = readVal(addr);
            bool match = false;
            if (compType == 0) match = (cur == target);
            else if (compType == 1) match = (cur > target);
            else if (compType == 2) match = (cur < target);
            else if (compType == 3) match = (cur != target);
            if (match) {
                matches.push_back(addrs[i]);
                if (matches.size() >= 5000) break;
            }
        }
        env->ReleaseIntArrayElements(prevAddrs, addrs, JNI_ABORT);
    } else {
        uint32_t step = (valType == 1) ? 1 : 2;

        // Scan EWRAM (256KB)
        for (uint32_t offset = 0; offset <= 0x040000 - valType; offset += step) {
            uint32_t addr = 0x02000000 + offset;
            uint32_t cur = readVal(addr);
            bool match = false;
            if (compType == 0) match = (cur == target);
            else if (compType == 1) match = (cur > target);
            else if (compType == 2) match = (cur < target);
            else if (compType == 3) match = true;
            if (match) {
                matches.push_back(static_cast<int>(addr));
                if (matches.size() >= 5000) break;
            }
        }

        // Scan IWRAM (32KB)
        if (matches.size() < 5000) {
            for (uint32_t offset = 0; offset <= 0x008000 - valType; offset += step) {
                uint32_t addr = 0x03000000 + offset;
                uint32_t cur = readVal(addr);
                bool match = false;
                if (compType == 0) match = (cur == target);
                else if (compType == 1) match = (cur > target);
                else if (compType == 2) match = (cur < target);
                else if (compType == 3) match = true;
                if (match) {
                    matches.push_back(static_cast<int>(addr));
                    if (matches.size() >= 5000) break;
                }
            }
        }
    }

    jintArray result = env->NewIntArray(matches.size());
    if (!matches.empty()) {
        env->SetIntArrayRegion(result, 0, matches.size(), matches.data());
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeSetFastForward(
    JNIEnv* /*env*/, jobject /*thiz*/, jfloat ratio) {
    sFastForwardRatio = ratio;
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeSetAudioMute(
    JNIEnv* /*env*/, jobject /*thiz*/, jboolean muted) {
    sAudioEngine.setMuted(muted == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeSetAudioVolume(
    JNIEnv* /*env*/, jobject /*thiz*/, jfloat volume) {
    sAudioEngine.setVolume(volume);
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeReset(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (sCore) {
        sCore->reset(sCore);
        sAudioEngine.reset();
        LOGI("Emulation reset");
    }
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeDestroy(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    cleanUpCore();
    sAudioEngine.stop();
    LOGI("GbaBridge destroyed");
}

// OpenGL ES surface callbacks
JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeSurfaceCreated(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    sVideoRenderer.init();
    sIsVideoInitialized = true;
    LOGI("Surface created in GL thread");
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeSurfaceChanged(
    JNIEnv* /*env*/, jobject /*thiz*/, jint width, jint height) {
    sVideoRenderer.setViewport(width, height);
    LOGI("Surface changed: %dx%d", width, height);
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeRenderFrame(
    JNIEnv* /*env*/, jobject /*thiz*/, jint keyMask) {
    std::lock_guard<std::mutex> lock(sCoreMutex);

    if (sCore) {
        sCore->setKeys(sCore, keyMask);
        sCore->runFrame(sCore);

        // Feed audio
        blip_t* left = sCore->getAudioChannel(sCore, 0);
        blip_t* right = sCore->getAudioChannel(sCore, 1);
        if (left && right) {
            int avail = blip_samples_avail(left);
            if (avail > 0) {
                if (avail > 4096) avail = 4096;
                blip_read_samples(left, sAudioBuffer, avail, 1);
                blip_read_samples(right, sAudioBuffer + 1, avail, 1);
                sAudioEngine.writeSamples(sAudioBuffer, avail);
            }
        }
    }

    if (sIsVideoInitialized) {
        sVideoRenderer.updateTexture(sVideoBuffer);
        sVideoRenderer.render();
    }
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeRedrawFrame(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(sCoreMutex);
    if (sIsVideoInitialized) {
        sVideoRenderer.render();
    }
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeSetFilter(
    JNIEnv* /*env*/, jobject /*thiz*/, jint filterType) {
    sVideoRenderer.setFilter(static_cast<VideoFilter>(filterType));
}

JNIEXPORT void JNICALL
Java_com_gba_nativeemu_core_GbaBridge_nativeSetAspectRatio(
    JNIEnv* /*env*/, jobject /*thiz*/, jint mode) {
    sVideoRenderer.setAspectRatio(static_cast<AspectRatioMode>(mode));
}

} // extern "C"
