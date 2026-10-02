#include "audio_engine.h"
#include <cstring>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "GbaAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static aaudio_data_callback_result_t aaudioCallback(
    AAudioStream* /*stream*/,
    void* userData,
    void* audioData,
    int32_t numFrames) {
    auto* engine = static_cast<AudioEngine*>(userData);
    return engine ? engine->onAudioReady(audioData, numFrames) : AAUDIO_CALLBACK_RESULT_STOP;
}

AudioEngine::AudioEngine() {
    std::memset(mRingBuffer, 0, sizeof(mRingBuffer));
}

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start(int sampleRate) {
    if (mIsRunning.load()) {
        stop();
    }

    mSampleRate = sampleRate;
    mWriteHead.store(0);
    mReadHead.store(0);
    std::memset(mRingBuffer, 0, sizeof(mRingBuffer));

    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        LOGE("Failed to create AAudioStreamBuilder: %s", AAudio_convertResultToText(result));
        return false;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setSampleRate(builder, mSampleRate);
    AAudioStreamBuilder_setDataCallback(builder, aaudioCallback, this);

    result = AAudioStreamBuilder_openStream(builder, &mStream);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK || !mStream) {
        LOGE("Failed to open AAudioStream: %s", AAudio_convertResultToText(result));
        mStream = nullptr;
        return false;
    }

    result = AAudioStream_requestStart(mStream);
    if (result != AAUDIO_OK) {
        LOGE("Failed to start AAudioStream: %s", AAudio_convertResultToText(result));
        AAudioStream_close(mStream);
        mStream = nullptr;
        return false;
    }

    mIsRunning.store(true);
    LOGI("AAudio stream started successfully (%d Hz stereo)", mSampleRate);
    return true;
}

void AudioEngine::stop() {
    mIsRunning.store(false);
    if (mStream) {
        AAudioStream_requestStop(mStream);
        AAudioStream_close(mStream);
        mStream = nullptr;
    }
}

void AudioEngine::pause() {
    if (mStream && mIsRunning.load()) {
        AAudioStream_requestPause(mStream);
    }
}

void AudioEngine::resume() {
    if (mStream && mIsRunning.load()) {
        AAudioStream_requestStart(mStream);
    }
}

void AudioEngine::reset() {
    mWriteHead.store(0);
    mReadHead.store(0);
    std::memset(mRingBuffer, 0, sizeof(mRingBuffer));
}

void AudioEngine::setMuted(bool muted) {
    mMuted.store(muted);
}

void AudioEngine::setVolume(float volume) {
    mVolume.store(std::clamp(volume, 0.0f, 1.0f));
}

void AudioEngine::writeSamples(const int16_t* samples, size_t numFrames) {
    if (!mIsRunning.load() || numFrames == 0) return;

    size_t w = mWriteHead.load(std::memory_order_relaxed);
    size_t r = mReadHead.load(std::memory_order_acquire);

    size_t availableSpace = RING_BUFFER_CAPACITY_FRAMES - ((w - r) % RING_BUFFER_CAPACITY_FRAMES) - 1;
    if (availableSpace < numFrames) {
        size_t framesToDrop = numFrames - availableSpace;
        mReadHead.store((r + framesToDrop) % RING_BUFFER_CAPACITY_FRAMES, std::memory_order_release);
    }

    for (size_t i = 0; i < numFrames; ++i) {
        size_t idx = (w + i) % RING_BUFFER_CAPACITY_FRAMES;
        mRingBuffer[idx * 2] = samples[i * 2];
        mRingBuffer[idx * 2 + 1] = samples[i * 2 + 1];
    }

    mWriteHead.store((w + numFrames) % RING_BUFFER_CAPACITY_FRAMES, std::memory_order_release);
}

aaudio_data_callback_result_t AudioEngine::onAudioReady(
    void* audioData,
    int32_t numFrames) {

    int16_t* out = static_cast<int16_t*>(audioData);

    if (mMuted.load(std::memory_order_relaxed)) {
        std::memset(out, 0, numFrames * 2 * sizeof(int16_t));
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    size_t r = mReadHead.load(std::memory_order_relaxed);
    size_t w = mWriteHead.load(std::memory_order_acquire);

    size_t availableFrames = (w >= r) ? (w - r) : (RING_BUFFER_CAPACITY_FRAMES - r + w);
    size_t framesToRead = std::min(static_cast<size_t>(numFrames), availableFrames);
    float vol = mVolume.load(std::memory_order_relaxed);

    for (size_t i = 0; i < framesToRead; ++i) {
        size_t idx = (r + i) % RING_BUFFER_CAPACITY_FRAMES;
        if (vol >= 0.99f) {
            out[i * 2] = mRingBuffer[idx * 2];
            out[i * 2 + 1] = mRingBuffer[idx * 2 + 1];
        } else {
            out[i * 2] = static_cast<int16_t>(mRingBuffer[idx * 2] * vol);
            out[i * 2 + 1] = static_cast<int16_t>(mRingBuffer[idx * 2 + 1] * vol);
        }
    }

    if (framesToRead < static_cast<size_t>(numFrames)) {
        std::memset(out + framesToRead * 2, 0, (numFrames - framesToRead) * 2 * sizeof(int16_t));
    }

    mReadHead.store((r + framesToRead) % RING_BUFFER_CAPACITY_FRAMES, std::memory_order_release);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
