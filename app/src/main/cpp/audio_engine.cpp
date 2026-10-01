#include "audio_engine.h"
#include <cstring>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "GbaAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

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

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Shared) // Shared has better compatibility across diverse devices
           ->setFormat(oboe::AudioFormat::I16)
           ->setChannelCount(oboe::ChannelCount::Stereo)
           ->setSampleRate(mSampleRate)
           ->setDataCallback(this);

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open Oboe audio stream: %s", oboe::convertToText(result));
        return false;
    }

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe audio stream: %s", oboe::convertToText(result));
        mStream->close();
        mStream.reset();
        return false;
    }

    mIsRunning.store(true);
    LOGI("Oboe audio stream started successfully (Sample rate: %d)", mSampleRate);
    return true;
}

void AudioEngine::stop() {
    mIsRunning.store(false);
    if (mStream) {
        mStream->stop();
        mStream->close();
        mStream.reset();
    }
}

void AudioEngine::pause() {
    if (mStream && mIsRunning.load()) {
        mStream->pause();
    }
}

void AudioEngine::resume() {
    if (mStream && mIsRunning.load()) {
        mStream->requestStart();
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
        // Drop oldest samples to avoid latency build-up
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

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* /*oboeStream*/,
    void* audioData,
    int32_t numFrames) {

    int16_t* out = static_cast<int16_t*>(audioData);

    if (mMuted.load(std::memory_order_relaxed)) {
        std::memset(out, 0, numFrames * 2 * sizeof(int16_t));
        return oboe::DataCallbackResult::Continue;
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

    // If underrun, fill rest with silence
    if (framesToRead < static_cast<size_t>(numFrames)) {
        std::memset(out + framesToRead * 2, 0, (numFrames - framesToRead) * 2 * sizeof(int16_t));
    }

    mReadHead.store((r + framesToRead) % RING_BUFFER_CAPACITY_FRAMES, std::memory_order_release);
    return oboe::DataCallbackResult::Continue;
}
